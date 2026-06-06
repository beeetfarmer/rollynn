package com.cappielloantonio.tempo.repository

import androidx.lifecycle.LiveData
import com.cappielloantonio.tempo.database.AppDatabase
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.cappielloantonio.tempo.model.PlaylistFolderEntry

class PlaylistFolderRepository {

    private val dao = AppDatabase.getInstance().playlistFolderDao()

    fun getChildFolders(parentId: Long?): LiveData<List<PlaylistFolder>> {
        return dao.getChildFolders(parentId)
    }

    fun getChildFoldersSync(parentId: Long?): List<PlaylistFolder> {
        return dao.getChildFoldersSync(parentId)
    }

    fun getAllFolders(): LiveData<List<PlaylistFolder>> {
        return dao.getAllFolders()
    }

    fun getAllFoldersSync(): List<PlaylistFolder> {
        return dao.getAllFoldersSync()
    }

    fun getFolderById(id: Long): PlaylistFolder? {
        return dao.getFolderById(id)
    }

    fun getPlaylistIdsInFolder(folderId: Long?): LiveData<List<String>> {
        return dao.getPlaylistIdsInFolder(folderId)
    }

    fun getAllAssignedPlaylistIds(): LiveData<List<String>> {
        return dao.getAllAssignedPlaylistIds()
    }

    fun getChildFolderCount(parentId: Long?): Int {
        return dao.getChildFolderCount(parentId)
    }

    fun getPlaylistCountInFolder(folderId: Long?): Int {
        return dao.getPlaylistCountInFolder(folderId)
    }

    fun createFolder(name: String, parentId: Long?): Long {
        return dao.insertFolder(PlaylistFolder(name = name, parentId = parentId))
    }

    fun renameFolder(folder: PlaylistFolder, newName: String) {
        folder.name = newName
        dao.updateFolder(folder)
    }

    fun deleteFolder(folder: PlaylistFolder) {
        dao.deleteFolder(folder)
    }

    fun movePlaylistToFolder(playlistId: String, folderId: Long?) {
        if (folderId == null) {
            dao.removePlaylistFromFolder(playlistId)
        } else {
            dao.assignPlaylist(PlaylistFolderEntry(playlistId = playlistId, folderId = folderId))
        }
    }

    fun getAncestorChain(folderId: Long): List<PlaylistFolder> {
        val chain = mutableListOf<PlaylistFolder>()
        var current = dao.getFolderById(folderId)
        while (current != null) {
            chain.add(0, current)
            current = current.parentId?.let { dao.getFolderById(it) }
        }
        return chain
    }
}
