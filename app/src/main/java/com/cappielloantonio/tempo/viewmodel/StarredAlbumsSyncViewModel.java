package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.repository.AlbumRepository;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Child;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StarredAlbumsSyncViewModel extends AndroidViewModel {
    private final AlbumRepository albumRepository;

    private final MutableLiveData<List<AlbumID3>> starredAlbums = new MutableLiveData<>(null);
    private final MutableLiveData<List<Child>> starredAlbumSongs = new MutableLiveData<>(null);
    private boolean fetchingAlbumSongs = false;

    public StarredAlbumsSyncViewModel(@NonNull Application application) {
        super(application);
        albumRepository = new AlbumRepository();
    }

    public LiveData<List<AlbumID3>> getStarredAlbums(LifecycleOwner owner) {
        albumRepository.getStarredAlbums(false, -1).observe(owner, starredAlbums::postValue);
        return starredAlbums;
    }

    public LiveData<List<Child>> getAllStarredAlbumSongs() {
        if (fetchingAlbumSongs) return starredAlbumSongs;
        fetchingAlbumSongs = true;

        LiveData<List<AlbumID3>> source = albumRepository.getStarredAlbums(false, -1);
        source.observeForever(new Observer<List<AlbumID3>>() {
            @Override
            public void onChanged(List<AlbumID3> albums) {
                source.removeObserver(this);
                if (albums != null && !albums.isEmpty()) {
                    collectAllAlbumSongs(albums, songs -> {
                        starredAlbumSongs.postValue(songs);
                        fetchingAlbumSongs = false;
                    });
                } else {
                    starredAlbumSongs.postValue(new ArrayList<>());
                    fetchingAlbumSongs = false;
                }
            }
        });

        return starredAlbumSongs;
    }

    public void refreshStarredAlbumSongs() {
        fetchingAlbumSongs = false;
        getAllStarredAlbumSongs();
    }

    public LiveData<List<Child>> getStarredAlbumSongs(Activity activity) {
        albumRepository.getStarredAlbums(false, -1).observe((LifecycleOwner) activity, albums -> {
            if (albums != null && !albums.isEmpty()) {
                collectAllAlbumSongs(albums, starredAlbumSongs::postValue);
            } else {
                starredAlbumSongs.postValue(new ArrayList<>());
            }
        });
        return starredAlbumSongs;
    }

    private void collectAllAlbumSongs(List<AlbumID3> albums, AlbumSongsCallback callback) {
        List<Child> allSongs = new ArrayList<>();
        AtomicInteger remaining = new AtomicInteger(albums.size());

        for (AlbumID3 album : albums) {
            LiveData<List<Child>> albumTracks = albumRepository.getAlbumTracks(album.getId());
            albumTracks.observeForever(new Observer<List<Child>>() {
                @Override
                public void onChanged(List<Child> songs) {
                    albumTracks.removeObserver(this);
                    if (songs != null) {
                        synchronized (allSongs) {
                            allSongs.addAll(songs);
                        }
                    }
                    if (remaining.decrementAndGet() == 0) {
                        callback.onSongsCollected(allSongs);
                    }
                }
            });
        }
    }

    private interface AlbumSongsCallback {
        void onSongsCollected(List<Child> songs);
    }
}