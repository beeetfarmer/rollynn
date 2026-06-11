package com.cappielloantonio.tempo.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playlist_folder",
    foreignKeys = [ForeignKey(
        entity = PlaylistFolder::class,
        parentColumns = ["id"],
        childColumns = ["parent_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("parent_id")]
)
data class PlaylistFolder(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    var name: String,

    @ColumnInfo(name = "parent_id")
    var parentId: Long? = null,

    @ColumnInfo(name = "sort_order")
    var sortOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    var createdAt: Long = System.currentTimeMillis()
)
