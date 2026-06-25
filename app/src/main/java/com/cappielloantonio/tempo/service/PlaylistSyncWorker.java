package com.cappielloantonio.tempo.service;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.media3.common.util.UnstableApi;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.NetworkUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Response;

@UnstableApi
public class PlaylistSyncWorker extends Worker {

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
            List<String> playlistIds = downloadDao.getDownloadedPlaylistIds();

            for (String playlistId : playlistIds) {
                syncPlaylist(playlistId, downloadDao);
            }

            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private void syncPlaylist(String playlistId, DownloadDao downloadDao) {
        try {
            Response<ApiResponse> response = App.getSubsonicClientInstance(false)
                    .getPlaylistClient()
                    .getPlaylist(playlistId)
                    .execute();

            if (!response.isSuccessful() || response.body() == null
                    || response.body().getSubsonicResponse().getPlaylist() == null) {
                return;
            }

            List<Child> serverSongs = response.body().getSubsonicResponse().getPlaylist().getEntries();
            if (serverSongs == null || serverSongs.isEmpty()) return;

            List<Download> localDownloads = downloadDao.getByPlaylistIdSync(playlistId);
            Set<String> localIds = new HashSet<>();
            for (Download d : localDownloads) {
                localIds.add(d.getId());
            }

            String playlistName = localDownloads.isEmpty() ? null : localDownloads.get(0).getPlaylistName();

            List<Child> newTracks = new ArrayList<>();
            List<Integer> newTrackPositions = new ArrayList<>();
            for (int position = 0; position < serverSongs.size(); position++) {
                Child song = serverSongs.get(position);
                if (!localIds.contains(song.getId())) {
                    newTracks.add(song);
                    newTrackPositions.add(position);
                }
            }

            if (!newTracks.isEmpty()) {
                DownloaderManager tracker = DownloadUtil.getDownloadTracker(getApplicationContext());
                List<Download> downloads = new ArrayList<>();
                for (int i = 0; i < newTracks.size(); i++) {
                    Download d = new Download(newTracks.get(i));
                    d.setPlaylistId(playlistId);
                    d.setPlaylistName(playlistName);
                    d.setPlaylistPosition(newTrackPositions.get(i));
                    downloads.add(d);
                }
                tracker.download(MappingUtil.mapDownloads(newTracks), downloads);
            }
        } catch (Exception ignored) {
        }
    }
}
