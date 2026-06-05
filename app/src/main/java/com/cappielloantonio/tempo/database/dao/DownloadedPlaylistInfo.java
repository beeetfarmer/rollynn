package com.cappielloantonio.tempo.database.dao;

import androidx.room.ColumnInfo;

public class DownloadedPlaylistInfo {
    @ColumnInfo(name = "playlist_id")
    public String playlistId;

    @ColumnInfo(name = "playlist_name")
    public String playlistName;

    @ColumnInfo(name = "song_count")
    public int songCount;

    @ColumnInfo(name = "cover_art_id")
    public String coverArtId;
}
