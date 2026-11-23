package com.cappielloantonio.tempo.util

import android.net.Uri
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.model.PlaylistFolder
import com.cappielloantonio.tempo.model.PlaylistFolderEntry
import com.cappielloantonio.tempo.subsonic.models.Playlist
import com.cappielloantonio.tempo.testutil.AppTestEnv
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Pinned playlists and playlist folders, which live in the database rather than the settings. */
@RunWith(RobolectricTestRunner::class)
class SettingsBackupPlaylistsTest {
    private val context get() = App.getContext()
    private val uri = Uri.parse("content://test/backup.json")
    private val pins get() = AppTestEnv.db.playlistDao()
    private val folders get() = AppTestEnv.db.playlistFolderDao()

    @Before
    fun setUp() = AppTestEnv.install()

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun export(): String {
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        assertTrue(SettingsBackupUtil.export(context, uri, null) >= 0)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun restore(content: String): Int {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(content.toByteArray()))
        return SettingsBackupUtil.restore(context, uri, null)
    }

    private fun clearAll() {
        folders.deleteAllEntries()
        folders.deleteAllFolders()
        pins.deleteAll()
    }

    /** Rock > Live, an empty Jazz folder, a playlist in each of Rock and Live, and one at the top level. */
    private fun makeLibrary() {
        pins.insert(Playlist("p1", "Road trip", 3600, 12, "cover-1"))
        pins.insert(Playlist("p2", "Focus", 1800, 6, null))
        val rock = folders.insertFolder(PlaylistFolder(name = "Rock", sortOrder = 2, createdAt = 111))
        val live = folders.insertFolder(PlaylistFolder(name = "Live", parentId = rock, sortOrder = 0, createdAt = 222))
        folders.insertFolder(PlaylistFolder(name = "Jazz", sortOrder = 1, createdAt = 333))
        folders.assignPlaylist(PlaylistFolderEntry("p1", rock, 1))
        folders.assignPlaylist(PlaylistFolderEntry("s9", live, 0))
        folders.assignPlaylist(PlaylistFolderEntry("s8", null, 4))
    }

    private fun pinSummary() = pins.allSync.map { listOf(it.id, it.name, it.duration, it.songCount, it.coverArtId) }.toSet()
    private fun folderSummary() = folders.getAllFoldersSync().map { listOf(it.id, it.name, it.parentId, it.sortOrder, it.createdAt) }.toSet()
    private fun entrySummary() = folders.getAllEntriesSync().map { listOf(it.playlistId, it.folderId, it.sortOrder) }.toSet()

    @Test
    fun pinsFoldersAndTheirContentsSurviveARoundTrip() {
        makeLibrary()
        val pinsBefore = pinSummary()
        val foldersBefore = folderSummary()
        val entriesBefore = entrySummary()
        val backup = export()

        clearAll()
        restore(backup)

        assertEquals(pinsBefore, pinSummary())
        assertEquals(foldersBefore, folderSummary())
        assertEquals(entriesBefore, entrySummary())
    }

    @Test
    fun restoringReplacesWhatIsThereNow() {
        makeLibrary()
        val backup = export()
        clearAll()
        pins.insert(Playlist("new", "Pinned after the backup"))
        folders.insertFolder(PlaylistFolder(name = "Made after the backup"))

        restore(backup)

        assertEquals(setOf("p1", "p2"), pins.allSync.map { it.id }.toSet())
        assertEquals(setOf("Rock", "Live", "Jazz"), folders.getAllFoldersSync().map { it.name }.toSet())
    }

    @Test
    fun anEmptyLibraryIsRestoredAsEmpty() {
        val backup = export()
        makeLibrary()

        restore(backup)

        assertTrue(pins.allSync.isEmpty())
        assertTrue(folders.getAllFoldersSync().isEmpty())
        assertTrue(folders.getAllEntriesSync().isEmpty())
    }

    @Test
    fun anOlderBackupWithoutPlaylistsLeavesThemAlone() {
        makeLibrary()
        val before = Triple(pinSummary(), folderSummary(), entrySummary())

        restore("""{"format":1,"settings":{}}""")

        assertEquals(before, Triple(pinSummary(), folderSummary(), entrySummary()))
    }

    @Test
    fun aDamagedPlaylistsSectionChangesNothing() {
        makeLibrary()
        App.getInstance().preferences.edit().putString("theme", "dark").commit()
        val before = Triple(pinSummary(), folderSummary(), entrySummary())

        // The second folder has no name.
        val result = restore(
            """{"format":1,"settings":{"theme":{"type":"string","value":"light"}},
               "playlists":{"pinned":[],"folders":[{"id":1,"name":"A"},{"id":2}],"folderEntries":[]}}""",
        )

        assertEquals(-1, result)
        assertEquals(before, Triple(pinSummary(), folderSummary(), entrySummary()))
        assertEquals("dark", App.getInstance().preferences.getString("theme", null))
    }

    @Test
    fun aChildListedBeforeItsParentIsStillRestored() {
        restore(
            """{"format":1,"settings":{},"playlists":{"pinned":[],"folderEntries":[],"folders":[
               {"id":7,"name":"Live","parentId":3},{"id":3,"name":"Rock","parentId":null}]}}""",
        )

        assertEquals(3L, folders.getFolderById(7)!!.parentId)
    }

    @Test
    fun aMissingParentPutsTheFolderAtTheTop() {
        restore(
            """{"format":1,"settings":{},"playlists":{"pinned":[],"folderEntries":[],"folders":[
               {"id":5,"name":"Orphan","parentId":99}]}}""",
        )

        assertNull(folders.getFolderById(5)!!.parentId)
    }

    @Test
    fun aLoopOfFoldersDoesNotHang() {
        restore(
            """{"format":1,"settings":{},"playlists":{"pinned":[],"folderEntries":[],"folders":[
               {"id":1,"name":"A","parentId":2},{"id":2,"name":"B","parentId":1}]}}""",
        )

        assertEquals(setOf("A", "B"), folders.getAllFoldersSync().map { it.name }.toSet())
    }

    @Test
    fun anEntryForAFolderNotInTheFileIsSkipped() {
        restore(
            """{"format":1,"settings":{},"playlists":{"pinned":[],"folders":[{"id":1,"name":"A"}],"folderEntries":[
               {"playlistId":"x","folderId":1},{"playlistId":"y","folderId":42}]}}""",
        )

        assertEquals(listOf("x"), folders.getAllEntriesSync().map { it.playlistId })
    }

    @Test
    fun theBackupFileListsThem() {
        makeLibrary()

        val playlists = JSONObject(export()).getJSONObject("playlists")

        assertEquals(2, playlists.getJSONArray("pinned").length())
        assertEquals(3, playlists.getJSONArray("folders").length())
        assertEquals(3, playlists.getJSONArray("folderEntries").length())
    }
}
