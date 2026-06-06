package com.cappielloantonio.tempo.database.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.cappielloantonio.tempo.model.PlaylistFolderEntry

@Dao
interface PlaylistFolderDao {

    @Query("SELECT * FROM playlist_folder WHERE parent_id IS :parentId ORDER BY sort_order, name")
    fun getChildFolders(parentId: Long?): LiveData<List<PlaylistFolder>>

    @Query("SELECT * FROM playlist_folder WHERE parent_id IS :parentId ORDER BY sort_order, name")
    fun getChildFoldersSync(parentId: Long?): List<PlaylistFolder>

    @Query("SELECT * FROM playlist_folder ORDER BY name")
    fun getAllFolders(): LiveData<List<PlaylistFolder>>

    @Query("SELECT * FROM playlist_folder ORDER BY name")
    fun getAllFoldersSync(): List<PlaylistFolder>

    @Query("SELECT * FROM playlist_folder WHERE id = :id")
    fun getFolderById(id: Long): PlaylistFolder?

    @Query("SELECT playlist_id FROM playlist_folder_entry WHERE folder_id IS :folderId")
    fun getPlaylistIdsInFolder(folderId: Long?): LiveData<List<String>>

    @Query("SELECT playlist_id FROM playlist_folder_entry WHERE folder_id IS :folderId")
    fun getPlaylistIdsInFolderSync(folderId: Long?): List<String>

    @Query("SELECT * FROM playlist_folder_entry WHERE playlist_id = :playlistId")
    fun getEntryForPlaylist(playlistId: String): PlaylistFolderEntry?

    @Query("SELECT playlist_id FROM playlist_folder_entry")
    fun getAllAssignedPlaylistIds(): LiveData<List<String>>

    @Query("SELECT playlist_id FROM playlist_folder_entry")
    fun getAllAssignedPlaylistIdsSync(): List<String>

    @Query("SELECT COUNT(*) FROM playlist_folder WHERE parent_id IS :parentId")
    fun getChildFolderCount(parentId: Long?): Int

    @Query("SELECT COUNT(*) FROM playlist_folder_entry WHERE folder_id IS :folderId")
    fun getPlaylistCountInFolder(folderId: Long?): Int

    @Insert
    fun insertFolder(folder: PlaylistFolder): Long

    @Update
    fun updateFolder(folder: PlaylistFolder)

    @Delete
    fun deleteFolder(folder: PlaylistFolder)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun assignPlaylist(entry: PlaylistFolderEntry)

    @Query("DELETE FROM playlist_folder_entry WHERE playlist_id = :playlistId")
    fun removePlaylistFromFolder(playlistId: String)
}
