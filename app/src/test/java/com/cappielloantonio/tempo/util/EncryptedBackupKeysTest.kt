package com.cappielloantonio.tempo.util

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Base64
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.testutil.AppTestEnv
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class EncryptedBackupKeysTest {
    private val context get() = App.getContext()
    private val preferences: SharedPreferences get() = App.getInstance().preferences
    private val uri = Uri.parse("content://test/backup.json")
    private val password = "correct horse".toCharArray()

    @Before
    fun setUp() {
        // The Android keystore is missing under Robolectric, so a second file stands in for the encrypted store.
        val encrypted = context.getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE)
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", encrypted)
    }

    private fun setAllKeys() {
        Preferences.setLastFmApiKey("lastfm-secret")
        Preferences.setTranslationApiKey("openrouter-secret")
        Preferences.setKoitoApiKey("koito-secret")
        Preferences.setAudioMuseApiToken("audiomuse-secret")
        Preferences.setPopinnPassword("popinn-secret")
    }

    private fun clearAllKeys() {
        Preferences.setLastFmApiKey(null)
        Preferences.setTranslationApiKey(null)
        Preferences.setKoitoApiKey(null)
        Preferences.setAudioMuseApiToken(null)
        Preferences.setPopinnPassword(null)
    }

    private fun export(password: CharArray?): String {
        val output = ByteArrayOutputStream()
        shadowOf(context.contentResolver).registerOutputStream(uri, output)
        assertTrue(SettingsBackupUtil.export(context, uri, password) >= 0)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun exportCount(password: CharArray?): Int {
        shadowOf(context.contentResolver).registerOutputStream(uri, ByteArrayOutputStream())
        return SettingsBackupUtil.export(context, uri, password)
    }

    private fun register(backup: String) {
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(backup.toByteArray()))
    }

    private fun restore(backup: String, password: CharArray?): Int {
        register(backup)
        return SettingsBackupUtil.restore(context, uri, password)
    }

    private fun tampered(backup: String, change: JSONObject.() -> Unit) =
        JSONObject(backup).apply { getJSONObject("apiKeys").change() }.toString()

    @Test
    fun everyKeySurvivesARoundTrip() {
        setAllKeys()
        val backup = export(password)
        clearAllKeys()

        restore(backup, password)

        assertEquals("lastfm-secret", Preferences.getLastFmApiKey())
        assertEquals("openrouter-secret", Preferences.getTranslationApiKey())
        assertEquals("koito-secret", Preferences.getKoitoApiKey())
        assertEquals("audiomuse-secret", Preferences.getAudioMuseApiToken())
        assertEquals("popinn-secret", Preferences.getPopinnPassword())
    }

    @Test
    fun aNonAsciiPasswordWorks() {
        setAllKeys()
        val unicode = "pässwörd-日本語".toCharArray()
        val backup = export(unicode.clone())
        clearAllKeys()

        restore(backup, unicode)

        assertEquals("koito-secret", Preferences.getKoitoApiKey())
    }

    @Test
    fun theFileNeverHoldsAKeyInTheClear() {
        setAllKeys()

        val backup = export(password)

        for (secret in listOf("lastfm", "openrouter", "koito-secret", "audiomuse-secret", "popinn")) {
            assertFalse(secret, backup.contains(secret))
        }
    }

    @Test
    fun theLoginIsNeverCarried() {
        setAllKeys()
        Preferences.setPassword("login-password")
        val backup = export(password)
        Preferences.setPassword(null)

        restore(backup, password)

        assertNull(Preferences.getPassword())
    }

    @Test
    fun withoutAPasswordTheFileHoldsNoKeys() {
        setAllKeys()

        assertFalse(JSONObject(export(null)).has("apiKeys"))
    }

    @Test
    fun aPasswordWithNoKeysAddsNothing() {
        assertFalse(JSONObject(export(password)).has("apiKeys"))
    }

    @Test
    fun keysCountTowardsWhatWasBackedUp() {
        Preferences.setKoitoApiKey("koito-secret")
        Preferences.setLastFmApiKey("lastfm-secret")
        preferences.edit().putString("theme", "dark").commit()

        assertEquals(3, exportCount(password))
    }

    @Test
    fun keysCountTowardsWhatWasRestored() {
        Preferences.setKoitoApiKey("koito-secret")
        preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)

        assertEquals(2, restore(backup, password))
    }

    @Test
    fun everyBackupUsesAFreshSaltAndIv() {
        setAllKeys()

        val first = JSONObject(export(password)).getJSONObject("apiKeys")
        val second = JSONObject(export(password)).getJSONObject("apiKeys")

        assertNotEquals(first.getString("salt"), second.getString("salt"))
        assertNotEquals(first.getString("iv"), second.getString("iv"))
        assertNotEquals(first.getString("data"), second.getString("data"))
    }

    @Test
    fun aWrongPasswordChangesNothing() {
        setAllKeys()
        preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)
        preferences.edit().putString("theme", "light").commit()
        Preferences.setKoitoApiKey("current")

        assertEquals(SettingsBackupUtil.RESTORE_WRONG_PASSWORD, restore(backup, "wrong password".toCharArray()))

        assertEquals("light", preferences.getString("theme", null))
        assertEquals("current", Preferences.getKoitoApiKey())
    }

    @Test
    fun skippingThePasswordRestoresOnlyTheSettings() {
        setAllKeys()
        preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)
        preferences.edit().clear().commit()
        Preferences.setKoitoApiKey("current")

        assertEquals(1, restore(backup, null))

        assertEquals("dark", preferences.getString("theme", null))
        assertEquals("current", Preferences.getKoitoApiKey())
    }

    @Test
    fun keysMissingFromTheBackupAreLeftAlone() {
        Preferences.setKoitoApiKey("koito-secret")
        val backup = export(password)
        Preferences.setLastFmApiKey("set later")

        restore(backup, password)

        assertEquals("set later", Preferences.getLastFmApiKey())
    }

    @Test
    fun aPasswordOnABackupWithoutKeysIsHarmless() {
        preferences.edit().putString("theme", "dark").commit()
        val backup = export(null)

        assertEquals(1, restore(backup, password))
    }

    @Test
    fun alteredKeysAreRefusedAndChangeNothing() {
        setAllKeys()
        val backup = export(password)
        Preferences.setKoitoApiKey("current")
        val forged = tampered(backup) {
            val data = Base64.decode(getString("data"), Base64.NO_WRAP)
            data[0] = (data[0] + 1).toByte()
            put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        }

        assertEquals(SettingsBackupUtil.RESTORE_WRONG_PASSWORD, restore(forged, password))
        assertEquals("current", Preferences.getKoitoApiKey())
    }

    @Test
    fun aDamagedKeySectionIsRefusedWithAPassword() {
        setAllKeys()
        val backup = export(password)

        assertEquals(-1, restore(tampered(backup) { remove("iv") }, password))
        assertEquals(-1, restore(tampered(backup) { put("salt", "not base64 !!") }, password))
    }

    @Test
    fun anAbsurdIterationCountIsRefusedWithoutRunningIt() {
        setAllKeys()
        val backup = export(password)

        assertEquals(-1, restore(tampered(backup) { put("iterations", Int.MAX_VALUE) }, password))
        assertEquals(-1, restore(tampered(backup) { put("iterations", 0) }, password))
    }

    @Test
    fun aDamagedKeySectionDoesNotBlockTheSettings() {
        setAllKeys()
        preferences.edit().putString("theme", "dark").commit()
        val backup = export(password)
        preferences.edit().clear().commit()

        assertEquals(1, restore(tampered(backup) { remove("iv") }, null))
    }

    @Test
    fun aBackupFromBeforeKeysStillRestores() {
        assertEquals(1, restore("""{"format":1,"settings":{"theme":{"type":"string","value":"dark"}}}""", null))
    }

    @Test
    fun hasApiKeysFollowsWhatIsSet() {
        assertFalse(SettingsBackupUtil.hasApiKeys())

        Preferences.setKoitoApiKey("koito-secret")

        assertTrue(SettingsBackupUtil.hasApiKeys())
    }

    @Test
    fun tellsWhetherAFileHoldsKeys() {
        setAllKeys()
        register(export(password))
        assertTrue(SettingsBackupUtil.hasEncryptedApiKeys(context, uri))

        register(export(null))
        assertFalse(SettingsBackupUtil.hasEncryptedApiKeys(context, uri))

        register("not json")
        assertFalse(SettingsBackupUtil.hasEncryptedApiKeys(context, uri))
    }
}
