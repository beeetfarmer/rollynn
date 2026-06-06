package com.cappielloantonio.tempo.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_folder_entry",
    foreignKeys = [ForeignKey(
        entity = PlaylistFolder::class,
        parentColumns = ["id"],
        childColumns = ["folder_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("folder_id")]
)
data class PlaylistFolderEntry(
    @PrimaryKey
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "folder_id")
    var folderId: Long? = null,

    @ColumnInfo(name = "sort_order")
    var sortOrder: Int = 0
)
