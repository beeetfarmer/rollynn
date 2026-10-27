package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.List;

public class PlaylistPageViewModel extends AndroidViewModel {
    private final PlaylistRepository playlistRepository;

    private Playlist playlist;
    private boolean isOffline;

    private String downloadingPlaylistId;
    private int downloadTotalCount;

    private final MutableLiveData<List<Child>> songLiveList = new MutableLiveData<>();

    public PlaylistPageViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
        playlistRepository.getPlaylistUpdateTrigger().observeForever(needsRefresh -> {
            if (needsRefresh != null && needsRefresh && playlist != null) {
                refreshSongs();
            }
        });
    }

    public LiveData<List<Child>> getPlaylistSongLiveList() {
        if (songLiveList.getValue() == null && playlist != null) {
            refreshSongs();
        }
        return songLiveList;
    }

    @androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
    private void refreshSongs() {
        if (playlist == null) return;
        // Both observers run on the main thread, so the flag can't change between check and set.
        boolean[] serverAnswered = {false};

        // Downloaded tracks first: until the first ping fails, an unreachable server looks
        // reachable and its answer only comes once the 20s connect timeout runs out.
        LiveData<List<Child>> localData = playlistRepository.getDownloadedPlaylistSongs(playlist.getId());
        localData.observeForever(new androidx.lifecycle.Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                localData.removeObserver(this);
                if (!serverAnswered[0] && songs != null && !songs.isEmpty()) songLiveList.setValue(songs);
            }
        });

        LiveData<List<Child>> remoteData = playlistRepository.getPlaylistSongs(playlist.getId());
        remoteData.observeForever(new androidx.lifecycle.Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                remoteData.removeObserver(this);
                serverAnswered[0] = true;
                songLiveList.setValue(songs);
            }
        });
    }

    /**
     * The full track list for downloading, straight from the server (or the downloaded
     * tracks offline), never the early downloaded-only list the page may be showing.
     */
    public LiveData<List<Child>> fetchSongsForDownload() {
        return playlistRepository.getPlaylistSongs(playlist.getId());
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        if (this.playlist == null || !this.playlist.getId().equals(playlist.getId())) {
            this.playlist = playlist;
            this.songLiveList.setValue(null); // Clear old data immediately
        }
    }

    public void setDownloadInProgress(String playlistId, int total) {
        this.downloadingPlaylistId = playlistId;
        this.downloadTotalCount = total;
    }

    public void clearDownloadInProgress() {
        this.downloadingPlaylistId = null;
        this.downloadTotalCount = 0;
    }

    public String getDownloadingPlaylistId() {
        return downloadingPlaylistId;
    }

    public int getDownloadTotalCount() {
        return downloadTotalCount;
    }

    public LiveData<Boolean> isPinned(LifecycleOwner owner) {
        MutableLiveData<Boolean> isPinnedLive = new MutableLiveData<>();

        playlistRepository.getPinnedPlaylists().observe(owner, playlists -> {
            isPinnedLive.postValue(playlists.stream().anyMatch(obj -> obj.getId().equals(playlist.getId())));
        });

        return isPinnedLive;
    }

    public void setPinned(boolean isNowPinned) {
        if (isNowPinned) {
            playlistRepository.insert(playlist);
        } else {
            playlistRepository.delete(playlist);
        }
    }
}
