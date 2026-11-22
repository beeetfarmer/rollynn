package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.interfaces.MediaCallback;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;

public class AlbumCatalogueViewModel extends AndroidViewModel {
    private static final String TAG = "AlbumCatalogueViewModel";
    private static final int PAGE_SIZE = 500;

    private final MutableLiveData<List<AlbumID3>> albumList = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);

    /** Goes up whenever a load starts or stops, so responses to an older one are ignored. */
    private int generation = 0;

    public AlbumCatalogueViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<AlbumID3>> getAlbumList() {
        return albumList;
    }

    public LiveData<Boolean> getLoadingStatus() {
        return loading;
    }

    public void loadAlbums() {
        generation++;
        albumList.setValue(new ArrayList<>());
        loading.setValue(true);
        loadPage(generation, 0);
    }

    public void stopLoading() {
        generation++;
        loading.setValue(false);
    }

    private void loadPage(int load, int offset) {
        retrieveAlbums(new MediaCallback() {
            @Override
            public void onError(Exception exception) {
                if (load != generation) return;
                Log.w(TAG, "Could not load albums from " + offset, exception);
                loading.setValue(false);
            }

            @Override
            public void onLoadMedia(List<?> media) {
                if (load != generation) return;

                List<AlbumID3> albums = new ArrayList<>(albumList.getValue());
                albums.addAll((List<AlbumID3>) media);
                albumList.setValue(albums);

                if (media.size() == PAGE_SIZE) {
                    loadPage(load, offset + PAGE_SIZE);
                } else {
                    loading.setValue(false);
                }
            }
        }, PAGE_SIZE, offset);
    }

    private void retrieveAlbums(MediaCallback callback, int size, int offset) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("alphabeticalByName", size, offset, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull retrofit2.Response<ApiResponse> response) {
                        SubsonicResponse body = response.body() != null ? response.body().getSubsonicResponse() : null;
                        if (!response.isSuccessful() || body == null || body.getAlbumList2() == null) {
                            callback.onError(new Exception("No album list in the response, HTTP " + response.code()));
                            return;
                        }
                        // The server leaves the list out of an empty page, which is how the last one can look.
                        List<AlbumID3> albums = body.getAlbumList2().getAlbums();
                        callback.onLoadMedia(albums != null ? albums : new ArrayList<>());
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        callback.onError(new Exception(t.getMessage()));
                    }
                });
    }
}