package com.cappielloantonio.tempo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.cappielloantonio.tempo.repository.PlaylistFolderRepository

class PlaylistFolderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistFolderRepository()

    private val _currentFolderId = MutableLiveData<Long?>(null)
    val currentFolderId: LiveData<Long?> = _currentFolderId

    private val _currentFolder = MutableLiveData<PlaylistFolder?>(null)
    val currentFolder: LiveData<PlaylistFolder?> = _currentFolder

    private val _breadcrumbs = MutableLiveData<List<PlaylistFolder>>(emptyList())
    val breadcrumbs: LiveData<List<PlaylistFolder>> = _breadcrumbs

    val childFolders: LiveData<List<PlaylistFolder>>
        get() = repository.getChildFolders(_currentFolderId.value)

    private val _childFoldersLive = MediatorLiveData<List<PlaylistFolder>>()
    val childFoldersLive: LiveData<List<PlaylistFolder>> = _childFoldersLive

    private var childFoldersSource: LiveData<List<PlaylistFolder>>? = null

    private val _playlistIdsInFolder = MediatorLiveData<List<String>>()
    val playlistIdsInFolder: LiveData<List<String>> = _playlistIdsInFolder

    private var playlistIdsSource: LiveData<List<String>>? = null

    private val _allAssignedIds = MediatorLiveData<List<String>>()
    val allAssignedPlaylistIds: LiveData<List<String>> = _allAssignedIds

    init {
        val allSource = repository.getAllAssignedPlaylistIds()
        _allAssignedIds.addSource(allSource) { _allAssignedIds.value = it }
        refreshSources()
    }

    fun navigateToFolder(folderId: Long?) {
        _currentFolderId.value = folderId
        refreshSources()
        Thread {
            val folder = folderId?.let { repository.getFolderById(it) }
            _currentFolder.postValue(folder)
            val chain = folderId?.let { repository.getAncestorChain(it) } ?: emptyList()
            _breadcrumbs.postValue(chain)
        }.start()
    }

    fun navigateUp(): Boolean {
        val parentId = _currentFolder.value?.parentId
        if (_currentFolderId.value == null) return false
        navigateToFolder(parentId)
        return true
    }

    fun isAtRoot(): Boolean = _currentFolderId.value == null

    fun createFolder(name: String, callback: ((Long) -> Unit)? = null) {
        Thread {
            val id = repository.createFolder(name, _currentFolderId.value)
            callback?.invoke(id)
        }.start()
    }

    fun renameFolder(folder: PlaylistFolder, newName: String) {
        Thread { repository.renameFolder(folder, newName) }.start()
    }

    fun deleteFolder(folder: PlaylistFolder) {
        Thread { repository.deleteFolder(folder) }.start()
    }

    fun movePlaylistToFolder(playlistId: String, folderId: Long?) {
        Thread { repository.movePlaylistToFolder(playlistId, folderId) }.start()
    }

    fun getAllFoldersSync(): List<PlaylistFolder> {
        return repository.getAllFoldersSync()
    }

    fun getAncestorChain(folderId: Long): List<PlaylistFolder> {
        return repository.getAncestorChain(folderId)
    }

    private fun refreshSources() {
        val folderId = _currentFolderId.value

        childFoldersSource?.let { _childFoldersLive.removeSource(it) }
        val newFolderSource = repository.getChildFolders(folderId)
        childFoldersSource = newFolderSource
        _childFoldersLive.addSource(newFolderSource) { _childFoldersLive.value = it }

        playlistIdsSource?.let { _playlistIdsInFolder.removeSource(it) }
        val newPlaylistSource = repository.getPlaylistIdsInFolder(folderId)
        playlistIdsSource = newPlaylistSource
        _playlistIdsInFolder.addSource(newPlaylistSource) { _playlistIdsInFolder.value = it }
    }
}
