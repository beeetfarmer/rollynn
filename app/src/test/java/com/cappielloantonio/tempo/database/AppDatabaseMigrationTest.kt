package com.cappielloantonio.tempo.database

import android.database.sqlite.SQLiteDatabase
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * Builds an old database from the exported schemas and opens it through the real
 * [AppDatabase.getInstance], so these cover the registered migrations and Room's own check that
 * the result matches the current entities.
 */
@RunWith(RobolectricTestRunner::class)
class AppDatabaseMigrationTest {
    private val schemas = File("schemas/com.cappielloantonio.tempo.database.AppDatabase")
    private val currentVersion = 20
    private val roomThread = Executors.newSingleThreadExecutor()

    private fun schema(version: Int): JsonObject =
        JsonParser.parseString(File(schemas, "$version.json").readText()).asJsonObject["database"].asJsonObject

    private fun createDatabase(version: Int, seed: SQLiteDatabase.() -> Unit = {}) {
        val schema = schema(version)
        val path = App.getContext().getDatabasePath("tempo_db")
        path.parentFile!!.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            for (element in schema["entities"].asJsonArray) {
                val entity = element.asJsonObject
                val table = entity["tableName"].asString
                db.execSQL(entity["createSql"].asString.replace("\${TABLE_NAME}", table))
                for (index in entity["indices"]?.asJsonArray ?: JsonArray()) {
                    db.execSQL(index.asJsonObject["createSql"].asString.replace("\${TABLE_NAME}", table))
                }
            }
            schema["setupQueries"].asJsonArray.forEach { db.execSQL(it.asString) }
            db.version = version
            db.seed()
        }
    }

    /** Room refuses to run on the main thread outside of tests that opt in. */
    private fun <T> onRoomThread(block: AppDatabase.() -> T): T =
        roomThread.submit(Callable { AppDatabase.getInstance().block() }).get()

    private fun rows(sql: String): List<List<String?>> = onRoomThread {
        openHelper.readableDatabase.query(sql).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor else null }
                .map { row -> (0 until row.columnCount).map(row::getString) }
                .toList()
        }
    }

    private fun columns(table: String) = rows("PRAGMA table_info(`$table`)").map { it[1] }

    @After
    fun tearDown() {
        onRoomThread { close() }
        AppTestEnv.setStatic(AppDatabase::class.java, "instance", null)
        roomThread.shutdown()
    }

    private fun SQLiteDatabase.insertDownload(id: String, playlistId: String? = null, playlistName: String? = null) {
        execSQL(
            "INSERT INTO download (id, playlist_id, playlist_name, download_state, is_dir, is_video, title) " +
                "VALUES (?, ?, ?, 1, 0, 0, ?)",
            arrayOf(id, playlistId, playlistName, "Title $id"),
        )
    }

    private fun SQLiteDatabase.insertUserData() {
        insertDownload("song")
        execSQL("INSERT INTO queue (id, track_order, last_play, playing_changed, is_dir, is_video) VALUES ('queued', 0, 0, 0, 0, 0)")
        execSQL("INSERT INTO scrobble (id, timestamp, submission, server) VALUES ('unsent', 100, 1, 'server1')")
        execSQL("INSERT INTO playlist (id, name, duration, songCount) VALUES ('pinned', 'Pinned', 0, 0)")
    }

    private fun assertUserDataKept() {
        assertEquals(listOf("song"), rows("SELECT id FROM download").map { it[0] })
        assertEquals(listOf("queued"), rows("SELECT id FROM queue").map { it[0] })
        assertEquals(listOf("unsent"), rows("SELECT id FROM scrobble").map { it[0] })
        assertEquals(listOf("pinned"), rows("SELECT id FROM playlist").map { it[0] })
    }

    @Test
    fun version16KeepsUserData() {
        createDatabase(16) { insertUserData() }
        assertUserDataKept()
    }

    @Test
    fun version17KeepsUserData() {
        createDatabase(17) { insertUserData() }
        assertUserDataKept()
    }

    @Test
    fun version18KeepsUserData() {
        createDatabase(18) { insertUserData() }
        assertUserDataKept()
    }

    @Test
    fun version19KeepsUserData() {
        createDatabase(19) { insertUserData() }
        assertUserDataKept()
    }

    @Test
    fun theCurrentVersionOpensUntouched() {
        createDatabase(currentVersion) { insertUserData() }
        assertUserDataKept()
    }

    @Test
    fun migratingAddsTheFolderTables() {
        createDatabase(16)

        assertEquals(listOf("0"), rows("SELECT COUNT(*) FROM playlist_folder").single())
        assertEquals(listOf("0"), rows("SELECT COUNT(*) FROM playlist_folder_entry").single())
    }

    @Test
    fun savedServersLoseOnlyTheirStoredPassword() {
        createDatabase(16) {
            execSQL(
                "INSERT INTO server (id, server_name, username, password, address, local_address, timestamp, low_security) " +
                    "VALUES ('s1', 'Home', 'me', 'secret', 'http://home', 'http://local', 5, 1)",
            )
        }

        assertEquals(
            listOf(listOf("s1", "Home", "me", "http://home", "http://local", "5", "1")),
            rows("SELECT id, server_name, username, address, local_address, timestamp, low_security FROM server"),
        )
        assertFalse(columns("server").contains("password"))
    }

    @Test
    fun downloadedPlaylistMembershipMovesToItsOwnTable() {
        createDatabase(19) {
            insertDownload("in_playlist", "p1", "Road trip")
            insertDownload("loose")
            execSQL("UPDATE download SET playlist_position = 7 WHERE id = 'in_playlist'")
        }

        assertEquals(
            listOf(listOf("in_playlist", "p1", "Road trip", "7")),
            rows("SELECT download_id, playlist_id, playlist_name, position FROM download_playlist"),
        )
        assertEquals(2, rows("SELECT id FROM download").size)
    }

    @Test
    fun downloadsFromBeforePlaylistPositionsSortLast() {
        createDatabase(18) { insertDownload("in_playlist", "p1", "Road trip") }

        assertEquals(listOf("-1"), rows("SELECT position FROM download_playlist").single())
    }

    @Test
    fun migratedDownloadsAreFoundByPlaylist() {
        createDatabase(16) { insertDownload("in_playlist", "p1", "Road trip") }

        val songs = onRoomThread { downloadDao().getByPlaylistIdSync("p1") }

        assertEquals(listOf("in_playlist"), songs.map { it.id })
        assertEquals("Title in_playlist", songs.single().title)
    }

    @Test
    fun aVersionWithoutAMigrationStartsEmptyInsteadOfCrashing() {
        createDatabase(15) { insertDownload("old") }

        assertTrue(rows("SELECT id FROM download").isEmpty())
    }

    @Test
    fun aFreshInstallMatchesTheLatestExportedSchema() {
        assertEquals(
            schema(currentVersion)["identityHash"].asString,
            rows("SELECT identity_hash FROM room_master_table").single()[0],
        )
        assertEquals(currentVersion, onRoomThread { openHelper.readableDatabase.version })
    }
}
