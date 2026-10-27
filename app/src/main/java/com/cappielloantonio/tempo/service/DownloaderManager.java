package com.cappielloantonio.tempo.service;

import static androidx.media3.common.util.Assertions.checkNotNull;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadCursor;
import androidx.media3.exoplayer.offline.DownloadHelper;
import androidx.media3.exoplayer.offline.DownloadIndex;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadRequest;
import androidx.media3.exoplayer.offline.DownloadService;

import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.DownloadPlaylistEntry;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.util.DownloadUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public class DownloaderManager {
    private static final String TAG = "DownloaderManager";

    private final Context context;
    private final DataSource.Factory dataSourceFactory;
    private final DownloadIndex downloadIndex;

    // Read from workers as well as the main thread.
    private static Map<String, Download> downloads;
    // One thread, so a row's insert always lands before its completion update.
    private static final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public DownloaderManager(Context context, DataSource.Factory dataSourceFactory, DownloadManager downloadManager) {
        this.context = context.getApplicationContext();
        this.dataSourceFactory = dataSourceFactory;

        downloads = new ConcurrentHashMap<>();
        downloadIndex = downloadManager.getDownloadIndex();

        loadDownloads();
    }

    private DownloadRequest buildDownloadRequest(MediaItem mediaItem) {
        return DownloadHelper
                .forMediaItem(
                        context,
                        mediaItem,
                        DownloadUtil.buildRenderersFactory(context, false),
                        dataSourceFactory)
                .getDownloadRequest(Util.getUtf8Bytes(checkNotNull(mediaItem.mediaId)))
                .copyWithId(mediaItem.mediaId);
    }

    public boolean isDownloaded(String mediaId) {
        @Nullable Download download = downloads.get(mediaId);
        return download != null && download.state != Download.STATE_FAILED;
    }

    public boolean isDownloaded(MediaItem mediaItem) {
        return isDownloaded(mediaItem.mediaId);
    }

    public boolean areDownloaded(List<MediaItem> mediaItems) {
        return mediaItems.stream().anyMatch(this::isDownloaded);
    }

    public void download(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        if (download.getPlaylistId() != null) {
            DownloadPlaylistEntry entry = new DownloadPlaylistEntry(download.getId(), download.getPlaylistId(), download.getPlaylistName(), download.getPlaylistPosition());
            dbExecutor.execute(() -> getDownloadDao().insertPlaylistEntries(Collections.singletonList(entry)));
        }

        @Nullable Download existing = downloads.get(mediaItem.mediaId);
        if (existing != null && existing.state == Download.STATE_COMPLETED) {
            // Already on the device: refresh its details from the server rather than
            // fetching it again, which would also hide it until the refetch finished.
            dbExecutor.execute(() -> {
                com.cappielloantonio.tempo.model.Download row = getDownloadDao().getOne(download.getId());
                download.setDownloadUri(row != null ? row.getDownloadUri() : mediaItem.requestMetadata.mediaUri.toString());
                download.setDownloadState(1);
                getDownloadDao().insert(download);
            });
            com.cappielloantonio.tempo.glide.CoverArtCache.cacheInBackground(download.getCoverArtId());
            return;
        }

        download.setDownloadUri(mediaItem.requestMetadata.mediaUri.toString());

        DownloadService.sendAddDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem), false);
        insertDatabase(download);
        com.cappielloantonio.tempo.glide.CoverArtCache.cacheInBackground(download.getCoverArtId());
    }

    public void download(List<MediaItem> mediaItems, List<com.cappielloantonio.tempo.model.Download> downloads) {
        for (int counter = 0; counter < mediaItems.size(); counter++) {
            download(mediaItems.get(counter), downloads.get(counter));
        }
    }

    public void remove(MediaItem mediaItem, com.cappielloantonio.tempo.model.Download download) {
        DownloadService.sendRemoveDownload(context, DownloaderService.class, buildDownloadRequest(mediaItem).id, false);
        deleteDatabase(download.getId());
        downloads.remove(download.getId());
    }

    public void remove(List<MediaItem> mediaItems, List<com.cappielloantonio.tempo.model.Download> downloads) {
        for (int counter = 0; counter < mediaItems.size(); counter++) {
            remove(mediaItems.get(counter), downloads.get(counter));
        }
    }

    public void removeAll() {
        DownloadService.sendRemoveAllDownloads(context, DownloaderService.class, false);
        deleteAllDatabase();
        DownloadUtil.eraseDownloadFolder(context);
    }

    private void loadDownloads() {
        try (DownloadCursor loadedDownloads = downloadIndex.getDownloads()) {
            while (loadedDownloads.moveToNext()) {
                Download download = loadedDownloads.getDownload();
                downloads.put(download.request.id, download);
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to query downloads", e);
        }
    }

    public static String getDownloadNotificationMessage(String id) {
        com.cappielloantonio.tempo.model.Download download = getDownloadRepository().getDownload(id);
        return download != null ? download.getTitle() : null;
    }

    public static void updateRequestDownload(Download download) {
        updateDatabase(download.request.id);
        downloads.put(download.request.id, download);
    }

    public static void removeRequestDownload(Download download) {
        deleteDatabase(download.request.id);
        downloads.remove(download.request.id);
    }

    private static DownloadRepository getDownloadRepository() {
        return new DownloadRepository();
    }

    private static DownloadDao getDownloadDao() {
        return AppDatabase.getInstance().downloadDao();
    }

    private static void insertDatabase(com.cappielloantonio.tempo.model.Download download) {
        dbExecutor.execute(() -> {
            // A re-queued track can finish before this runs; its completion update has then
            // already been applied, and inserting it as pending would undo it for good.
            @Nullable Download done = downloads.get(download.getId());
            if (done != null && done.state == Download.STATE_COMPLETED) download.setDownloadState(1);
            getDownloadDao().insert(download);
        });
    }

    private static void deleteDatabase(String id) {
        getDownloadRepository().delete(id);
    }

    private static void deleteAllDatabase() {
        getDownloadRepository().deleteAll();
    }

    private static void updateDatabase(String id) {
        dbExecutor.execute(() -> getDownloadDao().update(id));
    }
}