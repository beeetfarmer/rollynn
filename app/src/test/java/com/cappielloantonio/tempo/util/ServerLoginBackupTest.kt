package com.cappielloantonio.tempo.util

import android.content.Context
import android.net.Uri
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.testutil.AppTestEnv
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
class ServerLoginBackupTest {
    private val context get() = App.getContext()
    private val uri = Uri.parse("content://test/backup.json")
    private val password = "correct horse".toCharArray()
    private val servers get() = AppTestEnv.db.serverDao()

    private val home = Server("home-id", "Home", "neil", "https://music.example.org", "http://192.168.1.5:4533", 111, false)
    private val friend = Server("friend-id", "Friend's", "guest", "https://friend.example.net", null, 222, true)

    @Before
    fun setUp() {
        AppTestEnv.install()
        // The Android keystore is missing under Robolectric, so a second file stands in for the encrypted store.
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", context.getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE))
    }

    @After
    fun tearDown() {
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", null)
        AppTestEnv.uninstall()
    }

    /** Two saved servers, logged into Home. */
    private fun twoServersLoggedIntoHome() {
        servers.insert(home)
        servers.insert(friend)
        Preferences.setServerId(friend.serverId)
        Preferences.setPassword("friend-pass")
        Preferences.setServerId(home.serverId)
        Preferences.setServer(home.address)
        Preferences.setUser(home.username)
        Preferences.setPassword("home-pass")
    }

    private fun export(password: CharArray?): String {
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        assertTrue(SettingsBackupUtil.export(context, uri, password) >= 0)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun restore(backup: String, password: CharArray?, loginServer: AtomicReference<String>? = null): Int {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(backup.toByteArray()))
        return SettingsBackupUtil.restore(context, uri, password, loginServer)
    }

    /** As after uninstalling and installing again. */
    private fun freshInstall() {
        for (server in servers.allSync) servers.delete(server)
        App.getInstance().preferences.edit().clear().commit()
        App.getInstance().encryptedPreferences.edit().clear().commit()
    }

    @Test
    fun withAPasswordTheLoginsGoInEncrypted() {
        twoServersLoggedIntoHome()

        val backup = export(password)

        assertTrue(JSONObject(backup).has("servers"))
        for (secret in listOf("home-pass", "friend-pass", "music.example.org", "friend.example.net", "neil", "guest", "Home")) {
            assertFalse("$secret must not be readable in the file", backup.contains(secret))
        }
    }

    @Test
    fun skippingThePasswordLeavesTheLoginsOut() {
        twoServersLoggedIntoHome()

        val backup = export(null)

        assertFalse(JSONObject(backup).has("servers"))
        for (secret in listOf("home-pass", "music.example.org", "neil")) assertFalse(backup.contains(secret))
    }

    @Test
    fun aFreshInstallGetsTheServersAndPasswordsBack() {
        twoServersLoggedIntoHome()
        val backup = export(password)
        freshInstall()

        restore(backup, password)

        assertEquals(setOf(home, friend), servers.allSync.toSet())
        assertEquals("home-pass", Preferences.getPasswordForServer(home.serverId))
        assertEquals("friend-pass", Preferences.getPasswordForServer(friend.serverId))
    }

    @Test
    fun itNamesTheServerThatWasInUse() {
        twoServersLoggedIntoHome()
        val backup = export(password)
        freshInstall()
        val loginServer = AtomicReference<String>()

        restore(backup, password, loginServer)

        assertEquals(home.serverId, loginServer.get())
    }

    @Test
    fun theRestoreDoesNotLogInByItself() {
        twoServersLoggedIntoHome()
        val backup = export(password)
        freshInstall()

        restore(backup, password)

        assertNull("logging in is left to the login screen", Preferences.getServerId())
        assertNull(Preferences.getPassword())
    }

    @Test
    fun skippingThePasswordOnRestoreRestoresEverythingElse() {
        twoServersLoggedIntoHome()
        App.getInstance().preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)
        freshInstall()
        val loginServer = AtomicReference<String>()

        assertTrue(restore(backup, null, loginServer) > 0)

        assertTrue(servers.allSync.isEmpty())
        assertNull(loginServer.get())
        assertEquals("dark", App.getInstance().preferences.getString("theme", null))
    }

    @Test
    fun aWrongPasswordChangesNothing() {
        twoServersLoggedIntoHome()
        App.getInstance().preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)
        freshInstall()
        App.getInstance().preferences.edit().putString("theme", "light").commit()

        assertEquals(SettingsBackupUtil.RESTORE_WRONG_PASSWORD, restore(backup, "wrong".toCharArray()))

        assertTrue(servers.allSync.isEmpty())
        assertEquals("light", App.getInstance().preferences.getString("theme", null))
    }

    @Test
    fun aServerAlreadyHereIsLeftAsItIs() {
        twoServersLoggedIntoHome()
        val backup = export(password)
        freshInstall()
        val renamed = home.copy(serverName = "Renamed here", address = "https://new.example.org")
        servers.insert(renamed)
        App.getInstance().encryptedPreferences.edit().putString("password_home-id", "changed-here").commit()

        restore(backup, password)

        assertEquals(setOf(renamed, friend), servers.allSync.toSet())
        assertEquals("changed-here", Preferences.getPasswordForServer(home.serverId))
    }

    @Test
    fun restoringWhileLoggedInKeepsTheCurrentLogin() {
        twoServersLoggedIntoHome()
        val backup = export(password)
        servers.delete(friend)
        val loginServer = AtomicReference<String>()

        restore(backup, password, loginServer)

        assertEquals(home.serverId, Preferences.getServerId())
        assertEquals(home.address, Preferences.getServer())
        assertEquals("home-pass", Preferences.getPassword())
        assertTrue("the missing server is added back", servers.allSync.contains(friend))
    }

    @Test
    fun aServerSavedWithoutAPasswordIsNotLoggedIntoAutomatically() {
        servers.insert(home)
        Preferences.setServerId(home.serverId)
        val backup = export(password)
        freshInstall()
        val loginServer = AtomicReference<String>()

        restore(backup, password, loginServer)

        assertEquals(listOf(home), servers.allSync)
        assertNull(loginServer.get())
    }

    @Test
    fun aFileWithOnlyLoginsAsksForThePassword() {
        twoServersLoggedIntoHome()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(export(password).toByteArray()))

        assertTrue(SettingsBackupUtil.hasEncryptedSecrets(context, uri))
    }

    @Test
    fun aFileWithoutSecretsDoesNotAskForAPassword() {
        twoServersLoggedIntoHome()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(export(null).toByteArray()))

        assertFalse(SettingsBackupUtil.hasEncryptedSecrets(context, uri))
    }
}
