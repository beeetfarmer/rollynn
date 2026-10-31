package com.cappielloantonio.tempo.repository

import androidx.lifecycle.MutableLiveData
import com.cappielloantonio.tempo.model.Download
import com.cappielloantonio.tempo.model.DownloadPlaylistEntry
import com.cappielloantonio.tempo.subsonic.models.Playlist
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.AppTestEnv.db
import com.cappielloantonio.tempo.testutil.AppTestEnv.settle
import com.cappielloantonio.tempo.testutil.thenAnswerHttpError
import com.cappielloantonio.tempo.testutil.thenAnswerNetworkFailure
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import com.cappielloantonio.tempo.util.NetworkUtil
import com.cappielloantonio.tempo.util.PlaylistCoverCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.ArgumentMatchers.isNull
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistRepositoryTest {
    private lateinit var repository: PlaylistRepository
    private val client get() = AppTestEnv.subsonic.playlistClient

    private val serverPlaylists =
        """{"playlists":{"playlist":[{"id":"p1","name":"From server","songCount":3,"duration":60,"coverArt":"c1"}]}}"""

    private class Recorder : PlaylistRepository.AddToPlaylistCallback {
        val events = mutableListOf<String>()
        override fun onSuccess() { events += "success" }
        override fun onFailure() { events += "failure" }
        override fun onAllSkipped() { events += "skipped" }
    }

    @Before
    fun setUp() {
        AppTestEnv.install()
        AppTestEnv.setOnline()
        // A static shared by every instance, so it would otherwise carry over between tests.
        @Suppress("UNCHECKED_CAST")
        (PlaylistRepository::class.java.getDeclaredField("allPlaylistsLiveData")
            .apply { isAccessible = true }
            .get(null) as MutableLiveData<List<Playlist>?>).value = null
        repository = PlaylistRepository()
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun downloadInPlaylist(id: String, playlist: String = "p1", position: Int = 0, duration: Int = 30) {
        db.downloadDao().insert(Download(id).apply {
            downloadState = 1
            title = id
            this.duration = duration
        })
        db.downloadDao().insertPlaylistEntries(listOf(DownloadPlaylistEntry(id, playlist, "Downloaded", position)))
    }

    private fun allPlaylists(): List<Playlist> {
        val live = repository.getAllPlaylists(null)
        settle()
        return live.value!!
    }

    // refreshAllPlaylists / getAllPlaylists

    @Test
    fun unreachableServerListsDownloadedPlaylists() {
        downloadInPlaylist("s1", duration = 30)
        downloadInPlaylist("s2", position = 1, duration = 45)
        NetworkUtil.setServerReachable(false)

        val playlist = allPlaylists().single()

        assertEquals("p1", playlist.id)
        assertEquals("Downloaded", playlist.name)
        assertEquals(2, playlist.songCount)
        assertEquals(75L, playlist.duration)
        verify(client, never()).getPlaylists()
    }

    @Test
    fun offlinePlaylistPrefersThePinnedCoverOverTheCachedOne() {
        downloadInPlaylist("s1")
        db.playlistDao().insert(Playlist("p1", coverArtId = "pinned"))
        PlaylistCoverCache.save("p1", "cached")
        NetworkUtil.setServerReachable(false)

        assertEquals("pinned", allPlaylists().single().coverArtId)
    }

    @Test
    fun offlinePlaylistFallsBackToTheCachedCover() {
        downloadInPlaylist("s1")
        PlaylistCoverCache.save("p1", "cached")
        NetworkUtil.setServerReachable(false)

        assertEquals("cached", allPlaylists().single().coverArtId)
    }

    @Test
    fun serverPlaylistsReplaceDownloadsAndCacheTheirCovers() {
        downloadInPlaylist("s1", playlist = "downloaded")
        `when`(client.getPlaylists()).thenAnswerOk(serverPlaylists)

        val playlist = allPlaylists().single()

        assertEquals("From server", playlist.name)
        assertEquals("c1", PlaylistCoverCache.get("p1"))
    }

    @Test
    fun serverErrorFallsBackToDownloads() {
        downloadInPlaylist("s1")
        `when`(client.getPlaylists()).thenAnswerHttpError()

        assertEquals("Downloaded", allPlaylists().single().name)
    }

    @Test
    fun networkFailureFallsBackToDownloads() {
        downloadInPlaylist("s1")
        `when`(client.getPlaylists()).thenAnswerNetworkFailure()

        assertEquals("Downloaded", allPlaylists().single().name)
    }

    @Test
    fun responseWithoutPlaylistsFallsBackToDownloads() {
        downloadInPlaylist("s1")
        `when`(client.getPlaylists()).thenAnswerOk()

        assertEquals("Downloaded", allPlaylists().single().name)
    }

    // getPlaylists

    @Test
    fun getPlaylistsReturnsEverythingWhenNotRandom() {
        `when`(client.getPlaylists()).thenAnswerOk(serverPlaylists)

        assertEquals(listOf("p1"), repository.getPlaylists(false, 0).value!!.map { it.id })
    }

    @Test
    fun getPlaylistsLimitsARandomPick() {
        `when`(client.getPlaylists()).thenAnswerOk(
            """{"playlists":{"playlist":[{"id":"a"},{"id":"b"},{"id":"c"}]}}""",
        )

        assertEquals(2, repository.getPlaylists(true, 2).value!!.size)
    }

    @Test
    fun getPlaylistsStaysEmptyOnFailure() {
        `when`(client.getPlaylists()).thenAnswerNetworkFailure()

        assertTrue(repository.getPlaylists(false, 0).value!!.isEmpty())
    }

    // getPlaylistSongs / getPlaylist

    @Test
    fun getPlaylistSongsReturnsTheServerEntries() {
        `when`(client.getPlaylist("p1")).thenAnswerOk(
            """{"playlist":{"id":"p1","entry":[{"id":"a"},{"id":"b"}]}}""",
        )

        val songs = repository.getPlaylistSongs("p1")
        settle()

        assertEquals(listOf("a", "b"), songs.value!!.map { it.id })
    }

    @Test
    fun getPlaylistSongsUsesDownloadsInPlaylistOrderWhenUnreachable() {
        downloadInPlaylist("second", position = 1)
        downloadInPlaylist("first", position = 0)
        NetworkUtil.setServerReachable(false)

        val songs = repository.getPlaylistSongs("p1")
        settle()

        assertEquals(listOf("first", "second"), songs.value!!.map { it.id })
        verify(client, never()).getPlaylist(anyString())
    }

    @Test
    fun getPlaylistSongsFallsBackToDownloadsOnServerError() {
        downloadInPlaylist("s1")
        `when`(client.getPlaylist("p1")).thenAnswerHttpError()

        val songs = repository.getPlaylistSongs("p1")
        settle()

        assertEquals(listOf("s1"), songs.value!!.map { it.id })
    }

    @Test
    fun getPlaylistSongsFallsBackToDownloadsOnNetworkFailure() {
        downloadInPlaylist("s1")
        `when`(client.getPlaylist("p1")).thenAnswerNetworkFailure()

        val songs = repository.getPlaylistSongs("p1")
        settle()

        assertEquals(listOf("s1"), songs.value!!.map { it.id })
    }

    @Test
    fun getDownloadedPlaylistSongsNeverAsksTheServer() {
        downloadInPlaylist("s1")

        val songs = repository.getDownloadedPlaylistSongs("p1")
        settle()

        assertEquals(listOf("s1"), songs.value!!.map { it.id })
        verify(client, never()).getPlaylist(anyString())
    }

    @Test
    fun getPlaylistIsNullOnErrorOrFailure() {
        `when`(client.getPlaylist("p1")).thenAnswerHttpError().thenAnswerNetworkFailure()

        assertNull(repository.getPlaylist("p1").value)
        assertNull(repository.getPlaylist("p1").value)
    }

    @Test
    fun getPlaylistReturnsTheServerPlaylist() {
        `when`(client.getPlaylist("p1")).thenAnswerOk("""{"playlist":{"id":"p1","name":"Mine"}}""")

        assertEquals("Mine", repository.getPlaylist("p1").value!!.name)
    }

    // addSongToPlaylist

    @Test
    fun addingNothingIsSkippedWithoutAskingTheServer() {
        val recorder = Recorder()

        repository.addSongToPlaylist("p1", arrayListOf(), true, recorder)

        assertEquals(listOf("skipped"), recorder.events)
        verify(client, never()).updatePlaylist(any(), any(), any(), any(), any())
    }

    @Test
    fun addingSongsSendsThemAndRefreshes() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()
        val recorder = Recorder()

        repository.addSongToPlaylist("p1", arrayListOf("a"), false, recorder)

        assertEquals(listOf("success"), recorder.events)
        verify(client).updatePlaylist("p1", null, false, arrayListOf("a"), null)
        verify(client, atLeastOnce()).getPlaylists()
    }

    @Test
    fun addingSongsWithoutACallbackDoesNotCrash() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()

        repository.addSongToPlaylist("p1", arrayListOf("a"), true)

        verify(client).updatePlaylist("p1", null, true, arrayListOf("a"), null)
    }

    @Test
    fun addingSongsReportsAServerError() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerHttpError()
        val recorder = Recorder()

        repository.addSongToPlaylist("p1", arrayListOf("a"), true, recorder)

        assertEquals(listOf("failure"), recorder.events)
    }

    @Test
    fun addingSongsReportsANetworkFailure() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerNetworkFailure()
        val recorder = Recorder()

        repository.addSongToPlaylist("p1", arrayListOf("a"), true, recorder)

        assertEquals(listOf("failure"), recorder.events)
    }

    // removeSongFromPlaylist

    @Test
    fun removingASongSendsItsIndexAndRefreshes() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()
        val recorder = Recorder()

        repository.removeSongFromPlaylist("p1", 4, recorder)

        assertEquals(listOf("success"), recorder.events)
        verify(client).updatePlaylist(eq("p1"), isNull(), any(), isNull(), eq(arrayListOf(4)))
        verify(client, atLeastOnce()).getPlaylists()
    }

    @Test
    fun removingASongReportsErrorsAndFailures() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any()))
            .thenAnswerHttpError()
            .thenAnswerNetworkFailure()
        val recorder = Recorder()

        repository.removeSongFromPlaylist("p1", 0, recorder)
        repository.removeSongFromPlaylist("p1", 0, recorder)

        assertEquals(listOf("failure", "failure"), recorder.events)
    }

    @Test
    fun removingASongLeavesThePlaylistVisibilityAlone() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()

        repository.removeSongFromPlaylist("p1", 0, null)

        verify(client).updatePlaylist("p1", null, null, null, arrayListOf(0))
    }

    // createPlaylist

    @Test
    fun creatingAPlaylistRefreshes() {
        `when`(client.createPlaylist(any(), any(), any())).thenAnswerOk()
        val recorder = Recorder()

        repository.createPlaylist(null, "New", arrayListOf("a"), recorder)

        assertEquals(listOf("success"), recorder.events)
        verify(client).createPlaylist(null, "New", arrayListOf("a"))
        verify(client, atLeastOnce()).getPlaylists()
    }

    @Test
    fun creatingAPlaylistReportsErrorsAndFailures() {
        `when`(client.createPlaylist(any(), any(), any())).thenAnswerHttpError().thenAnswerNetworkFailure()
        val recorder = Recorder()

        repository.createPlaylist(null, "New", arrayListOf("a"), recorder)
        repository.createPlaylist(null, "New", arrayListOf("a"), recorder)

        assertEquals(listOf("failure", "failure"), recorder.events)
        verify(client, never()).getPlaylists()
    }

    // updatePlaylist

    @Test
    fun renamingUpdatesThePinnedCopy() {
        db.playlistDao().insert(Playlist("p1", name = "Old"))
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()

        repository.updatePlaylist("p1", "New", arrayListOf())
        settle()

        assertEquals("New", db.playlistDao().getAllSync().single().name)
        verify(client, atLeastOnce()).getPlaylists()
    }

    @Test
    fun aFailedRenameKeepsThePinnedName() {
        db.playlistDao().insert(Playlist("p1", name = "Old"))
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerHttpError()

        repository.updatePlaylist("p1", "New", arrayListOf())
        settle()

        assertEquals("Old", db.playlistDao().getAllSync().single().name)
    }

    @Test
    fun renamingLeavesThePlaylistVisibilityAlone() {
        `when`(client.updatePlaylist(any(), any(), any(), any(), any())).thenAnswerOk()

        repository.updatePlaylist("p1", "New", arrayListOf())
        settle()

        verify(client).updatePlaylist("p1", "New", null, null, null)
    }

    // deletePlaylist

    @Test
    fun deletingRefreshesOnlyWhenTheServerAgrees() {
        `when`(client.deletePlaylist("p1")).thenAnswerHttpError().thenAnswerOk()

        repository.deletePlaylist("p1")
        verify(client, never()).getPlaylists()

        repository.deletePlaylist("p1")
        verify(client, atLeastOnce()).getPlaylists()
    }

    @Test
    fun notifyingAChangeFiresTheUpdateTrigger() {
        repository.notifyPlaylistChanged()
        settle()

        assertEquals(true, repository.getPlaylistUpdateTrigger().value)
    }

    // Pins

    @Test
    fun insertAndDeleteChangeThePins() {
        val playlist = Playlist("p1")

        repository.insert(playlist)
        settle()
        assertEquals(listOf("p1"), db.playlistDao().getAllSync().map { it.id })

        repository.delete(playlist)
        settle()
        assertTrue(db.playlistDao().getAllSync().isEmpty())
    }

    @Test
    fun pinsTakeTheServersDetails() {
        db.playlistDao().insert(Playlist("p1", name = "Old", duration = 1, songCount = 1, coverArtId = "old"))
        `when`(client.getPlaylists()).thenAnswerOk(serverPlaylists)

        repository.updatePinnedPlaylists()
        settle()

        val pin = db.playlistDao().getAllSync().single()
        assertEquals("From server", pin.name)
        assertEquals(3, pin.songCount)
        assertEquals(60L, pin.duration)
        assertEquals("c1", pin.coverArtId)
    }

    @Test
    fun pinsGoneFromTheServerAreDropped() {
        db.playlistDao().insert(Playlist("gone"))
        db.playlistDao().insert(Playlist("p1"))
        `when`(client.getPlaylists()).thenAnswerOk(serverPlaylists)

        repository.updatePinnedPlaylists()
        settle()

        assertEquals(listOf("p1"), db.playlistDao().getAllSync().map { it.id })
    }

    @Test
    fun pinsSurviveAServerError() {
        db.playlistDao().insert(Playlist("p1", name = "Old"))
        `when`(client.getPlaylists()).thenAnswerHttpError()

        repository.updatePinnedPlaylists()
        settle()

        assertEquals("Old", db.playlistDao().getAllSync().single().name)
    }

    @Test
    fun pinsSurviveANetworkFailure() {
        db.playlistDao().insert(Playlist("p1", name = "Old"))
        `when`(client.getPlaylists()).thenAnswerNetworkFailure()

        repository.updatePinnedPlaylists()
        settle()

        assertEquals("Old", db.playlistDao().getAllSync().single().name)
    }

    @Test
    fun noPinsMeansNoRequest() {
        repository.updatePinnedPlaylists()
        settle()

        verify(client, never()).getPlaylists()
    }
}
