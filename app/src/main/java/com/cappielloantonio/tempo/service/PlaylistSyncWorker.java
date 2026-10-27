package com.cappielloantonio.tempo.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.DownloadPlaylistEntry;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlaylistWithSongs;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import retrofit2.Response;

/**
 * Keeps downloads in step with the server: downloaded playlists gain and lose tracks as
 * they do on the server, and every downloaded track picks up renamed titles, artists,
 * albums and covers.
 */
@UnstableApi
public class PlaylistSyncWorker extends Worker {
    // Subsonic "requested data was not found".
    private static final int ERROR_NOT_FOUND = 70;
    // When set, only this playlist is synced.
    private static final String KEY_PLAYLIST_ID = "playlist_id";

    /** Syncs one downloaded playlist now, as asked from its page. */
    public static void syncPlaylistNow(Context context, String playlistId) {
        WorkManager.getInstance(context).enqueueUniqueWork(
                "playlist_sync_" + playlistId,
                ExistingWorkPolicy.KEEP,
                new OneTimeWorkRequest.Builder(PlaylistSyncWorker.class)
                        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setInputData(new Data.Builder().putString(KEY_PLAYLIST_ID, playlistId).build())
                        .build());
    }

    public PlaylistSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (NetworkUtil.isServerUnreachable()) {
            return Result.retry();
        }

        try {
            DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();
            DownloaderManager tracker = DownloadUtil.getDownloadTracker(getApplicationContext());
            Set<String> refreshed = new HashSet<>();

            String onlyPlaylistId = getInputData().getString(KEY_PLAYLIST_ID);
            if (onlyPlaylistId != null) {
                syncPlaylist(onlyPlaylistId, downloadDao, tracker, refreshed);
                return Result.success();
            }

            for (String playlistId : downloadDao.getDownloadedPlaylistIds()) {
                syncPlaylist(playlistId, downloadDao, tracker, refreshed);
            }
            refreshOtherDownloads(downloadDao, tracker, refreshed);

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void syncPlaylist(String playlistId, DownloadDao downloadDao, DownloaderManager tracker, Set<String> refreshed) {
        try {
            Response<ApiResponse> response = App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .getPlaylist(playlistId)
                    .execute();

            if (!response.isSuccessful() || response.body() == null
                    || response.body().getSubsonicResponse().getPlaylist() == null) {
                return;
            }

            PlaylistWithSongs playlist = response.body().getSubsonicResponse().getPlaylist();
            List<Child> serverSongs = playlist.getEntries();
            if (serverSongs == null) return;

            List<Download> before = downloadDao.getByPlaylistIdSync(playlistId);

            List<Download> downloads = new ArrayList<>();
            List<DownloadPlaylistEntry> entries = new ArrayList<>();
            Set<String> serverIds = new HashSet<>();
            for (int position = 0; position < serverSongs.size(); position++) {
                Child song = serverSongs.get(position);
                Download d = new Download(song);
                d.setPlaylistId(playlistId);
                d.setPlaylistName(playlist.getName());
                d.setPlaylistPosition(position);
                downloads.add(d);
                entries.add(new DownloadPlaylistEntry(song.getId(), playlistId, playlist.getName(), position));
                serverIds.add(song.getId());
            }

            downloadDao.replacePlaylistEntries(playlistId, entries);
            // Fetches new tracks; tracks already here just get their details refreshed.
            tracker.download(MappingUtil.mapDownloads(serverSongs), downloads);
            refreshed.addAll(serverIds);

            // Tracks taken off the playlist go too, unless another playlist still holds them.
            List<Download> dropped = new ArrayList<>();
            for (Download d : before) {
                if (!serverIds.contains(d.getId()) && downloadDao.getPlaylistIdsFor(d.getId()).isEmpty()) {
                    dropped.add(d);
                }
            }
            if (!dropped.isEmpty()) {
                tracker.remove(MappingUtil.mapDownloads(new ArrayList<>(dropped)), dropped);
            }
        } catch (Exception ignored) {
        }
    }

    /** Refreshes downloads outside playlists album by album, which takes far fewer calls than song by song. */
    private void refreshOtherDownloads(DownloadDao downloadDao, DownloaderManager tracker, Set<String> refreshed) {
        Map<String, List<Download>> byAlbum = new HashMap<>();
        for (Download d : downloadDao.getAllSync()) {
            if (refreshed.contains(d.getId())) continue;
            byAlbum.computeIfAbsent(d.getAlbumId() != null ? d.getAlbumId() : "", k -> new ArrayList<>()).add(d);
        }

        List<Download> leftovers = new ArrayList<>();
        for (Map.Entry<String, List<Download>> album : byAlbum.entrySet()) {
            Map<String, Child> serverSongs = new HashMap<>();
            try {
                if (!album.getKey().isEmpty()) {
                    Response<ApiResponse> response = App.getSubsonicClientInstance(false)
                            .getBrowsingClient()
                            .getAlbum(album.getKey())
                            .execute();
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getSubsonicResponse().getAlbum() != null
                            && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                        for (Child song : response.body().getSubsonicResponse().getAlbum().getSongs()) {
                            serverSongs.put(song.getId(), song);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            for (Download d : album.getValue()) {
                Child song = serverSongs.get(d.getId());
                if (song != null) {
                    tracker.download(MappingUtil.mapDownload(song), new Download(song));
                } else {
                    leftovers.add(d);
                }
            }
        }

        // Not on its album any more (moved, or the album was renamed): ask for the track itself.
        List<Download> gone = new ArrayList<>();
        for (Download d : leftovers) {
            try {
                Response<ApiResponse> response = App.getSubsonicClientInstance(false)
                        .getBrowsingClient()
                        .getSong(d.getId())
                        .execute();
                if (!response.isSuccessful() || response.body() == null) continue;
                Child song = response.body().getSubsonicResponse().getSong();
                if (song != null) {
                    tracker.download(MappingUtil.mapDownload(song), new Download(song));
                } else if (response.body().getSubsonicResponse().getError() != null
                        && Integer.valueOf(ERROR_NOT_FOUND).equals(response.body().getSubsonicResponse().getError().getCode())) {
                    // Deleted, or renamed into a new id on the server: the old copy can't be refreshed.
                    gone.add(d);
                }
            } catch (Exception ignored) {
            }
        }
        if (!gone.isEmpty()) {
            tracker.remove(MappingUtil.mapDownloads(new ArrayList<>(gone)), gone);
        }
    }
}
