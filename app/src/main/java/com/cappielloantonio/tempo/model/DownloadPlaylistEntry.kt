package com.cappielloantonio.tempo.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Links a downloaded track to a downloaded playlist. A track can sit in several
 * playlists, so this lives apart from the download row, which holds one copy of it.
 */
@Entity(
    tableName = "download_playlist",
    primaryKeys = ["download_id", "playlist_id"],
    indices = [Index("playlist_id")]
)
data class DownloadPlaylistEntry(
    @ColumnInfo(name = "download_id")
    val downloadId: String,

    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "playlist_name")
    val playlistName: String?,

    @ColumnInfo(name = "position", defaultValue = "-1")
    val position: Int = -1
)
