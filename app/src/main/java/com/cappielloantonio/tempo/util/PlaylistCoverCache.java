package com.cappielloantonio.tempo.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.cappielloantonio.tempo.App;

public class PlaylistCoverCache {
    private static final String PREFS_NAME = "playlist_cover_art";

    private static SharedPreferences getPrefs() {
        return App.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void save(String playlistId, String coverArtId) {
        if (playlistId == null || coverArtId == null) return;
        getPrefs().edit().putString(playlistId, coverArtId).apply();
    }

    public static String get(String playlistId) {
        if (playlistId == null) return null;
        return getPrefs().getString(playlistId, null);
    }

    public static void remove(String playlistId) {
        if (playlistId == null) return;
        getPrefs().edit().remove(playlistId).apply();
    }
}
