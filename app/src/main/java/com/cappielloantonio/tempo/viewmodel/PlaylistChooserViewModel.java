package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.app.Dialog;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.repository.PlaylistRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PlaylistChooserViewModel extends AndroidViewModel {
    private static final String TAG = "PlaylistChooserVM";
    private final PlaylistRepository playlistRepository;
    private final MutableLiveData<List<Playlist>> playlists = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> playlistIsPublic = new MutableLiveData<>(false);

    public Boolean getIsPlaylistPublic() {
        return playlistIsPublic.getValue();
    }

    public void setIsPlaylistPublic(boolean isPublic) {
        playlistIsPublic.setValue(isPublic);
    }

    private ArrayList<Child> toAdd = new ArrayList<>();

    public PlaylistChooserViewModel(@NonNull Application application) {
        super(application);

        playlistRepository = new PlaylistRepository();
    }

    public LiveData<List<Playlist>> getPlaylistList(LifecycleOwner owner) {
        playlistRepository.getPlaylists(false, -1).observe(owner, list -> playlists.postValue(sortForChooser(list)));
        return playlists;
    }

    /** The playlists tracks were last added to first (most recent on top), then the rest by name. */
    private static List<Playlist> sortForChooser(List<Playlist> list) {
        if (list == null) return null;
        List<String> recent = Preferences.getRecentPlaylistIds();
        List<Playlist> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt((Playlist p) -> {
            int index = recent.indexOf(p.getId());
            return index < 0 ? recent.size() : index;
        }).thenComparing(p -> p.getName() == null ? "" : p.getName(), String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    public void addSongsToPlaylist(LifecycleOwner owner, Dialog dialog, String playlistId) {
        List<String> playlistIds = new ArrayList<>();
        playlistIds.add(playlistId);
        addSongsToPlaylists(owner, dialog, playlistIds);
    }

    public void addSongsToPlaylists(LifecycleOwner owner, Dialog dialog, List<String> playlistIds) {
        android.util.Log.d(TAG, "addSongsToPlaylists: playlists=" + playlistIds + ", songs=" + toAdd.size());
        if (playlistIds == null || playlistIds.isEmpty()) {
            if (dialog != null) dialog.dismiss();
            return;
        }

        final int totalRequests = playlistIds.size();
        final int[] completedRequests = {0};
        final List<String> addedToNames = new ArrayList<>();
        final List<String> skippedFromNames = new ArrayList<>();
        final List<String> failedForNames = new ArrayList<>();

        List<String> songIdsToAdd = new ArrayList<>(Lists.transform(toAdd, Child::getId));

        for (String playlistId : playlistIds) {
            String playlistName = getPlaylistNameById(playlistId);
            if (Preferences.allowPlaylistDuplicates()) {
                playlistRepository.addSongToPlaylist(playlistId, new ArrayList<>(songIdsToAdd), getIsPlaylistPublic(), new com.cappielloantonio.tempo.repository.PlaylistRepository.AddToPlaylistCallback() {
                    @Override public void onSuccess() { 
                        addedToNames.add(playlistName);
                        Preferences.addRecentPlaylistId(playlistId);
                        checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                    }
                    @Override public void onFailure() { 
                        failedForNames.add(playlistName);
                        checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                    }
                    @Override public void onAllSkipped() { 
                        skippedFromNames.add(playlistName);
                        checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                    }
                });
            } else {
                playlistRepository.getPlaylistSongs(playlistId).observe(owner, playlistSongs -> {
                    List<String> specificSongIdsToAdd = new ArrayList<>(songIdsToAdd);
                    if (playlistSongs != null) {
                        List<String> playlistSongIds = Lists.transform(playlistSongs, Child::getId);
                        specificSongIdsToAdd.removeAll(playlistSongIds);
                    }
                    playlistRepository.addSongToPlaylist(playlistId, new ArrayList<>(specificSongIdsToAdd), getIsPlaylistPublic(), new com.cappielloantonio.tempo.repository.PlaylistRepository.AddToPlaylistCallback() {
                        @Override public void onSuccess() { 
                            addedToNames.add(playlistName);
                            Preferences.addRecentPlaylistId(playlistId);
                            checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                        }
                        @Override public void onFailure() { 
                            failedForNames.add(playlistName);
                            checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                        }
                        @Override public void onAllSkipped() { 
                            skippedFromNames.add(playlistName);
                            checkCompletion(completedRequests, totalRequests, dialog, addedToNames, skippedFromNames, failedForNames); 
                        }
                    });
                });
            }
        }
    }

    private String getPlaylistNameById(String id) {
        if (playlists.getValue() != null) {
            for (Playlist p : playlists.getValue()) {
                if (p.getId().equals(id)) return p.getName();
            }
        }
        return id;
    }

    private void checkCompletion(int[] completedRequests, int totalRequests, Dialog dialog, List<String> addedTo, List<String> skippedFrom, List<String> failedFor) {
        completedRequests[0]++;
        if (completedRequests[0] >= totalRequests) {
            StringBuilder message = new StringBuilder();
            if (!addedTo.isEmpty()) {
                message.append(getApplication().getString(R.string.playlist_chooser_dialog_toast_added_to, String.join(", ", addedTo)));
            }
            if (!skippedFrom.isEmpty()) {
                if (message.length() > 0) message.append("\n");
                message.append(getApplication().getString(R.string.playlist_chooser_dialog_toast_skipped_from, String.join(", ", skippedFrom)));
            }
            if (!failedFor.isEmpty()) {
                if (message.length() > 0) message.append("\n");
                message.append(getApplication().getString(R.string.playlist_chooser_dialog_toast_failed_for, String.join(", ", failedFor)));
            }
            
            if (message.length() > 0) {
                android.widget.Toast.makeText(getApplication(), message.toString(), android.widget.Toast.LENGTH_LONG).show();
            }
            playlistRepository.updatePinnedPlaylists();
            dialog.dismiss();
        }
    }

    public void setSongsToAdd(ArrayList<Child> songs) {
        toAdd = songs;
    }

    public ArrayList<Child> getSongsToAdd() {
        return toAdd;
    }
}
