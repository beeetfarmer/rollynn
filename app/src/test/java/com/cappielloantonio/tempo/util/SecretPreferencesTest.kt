package com.cappielloantonio.tempo.util

import android.content.Context
import android.content.SharedPreferences
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.testutil.AppTestEnv
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SecretPreferencesTest {
    // The Android keystore is missing under Robolectric, so a second file stands in for the encrypted store.
    private lateinit var encrypted: SharedPreferences
    private val plain get() = App.getInstance().preferences

    @Before
    fun setUp() {
        encrypted = App.getContext().getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE)
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", encrypted)
    }

    private fun runStartupMigration() {
        App::class.java.getDeclaredMethod("migrateSensitivePreferences")
            .apply { isAccessible = true }
            .invoke(App.getInstance())
    }

    @Test
    fun theKoitoKeyIsStoredEncrypted() {
        Preferences.setKoitoApiKey("koito")

        assertEquals("koito", encrypted.getString("koito_api_key", null))
        assertFalse(plain.contains("koito_api_key"))
        assertEquals("koito", Preferences.getKoitoApiKey())
    }

    @Test
    fun theAudioMuseTokenIsStoredEncrypted() {
        Preferences.setAudioMuseApiToken("audiomuse")

        assertEquals("audiomuse", encrypted.getString("audiomuse_api_token", null))
        assertFalse(plain.contains("audiomuse_api_token"))
        assertEquals("audiomuse", Preferences.getAudioMuseApiToken())
    }

    @Test
    fun plainKeysMoveToTheEncryptedStoreOnStartup() {
        plain.edit().putString("koito_api_key", "koito").putString("audiomuse_api_token", "audiomuse").commit()

        runStartupMigration()

        assertFalse(plain.contains("koito_api_key"))
        assertFalse(plain.contains("audiomuse_api_token"))
        assertEquals("koito", Preferences.getKoitoApiKey())
        assertEquals("audiomuse", Preferences.getAudioMuseApiToken())
    }

    @Test
    fun keysStillWorkWhenEncryptionIsUnavailable() {
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", null)

        Preferences.setKoitoApiKey("koito")

        assertEquals("koito", Preferences.getKoitoApiKey())
    }

    @Test
    fun aKeyThatWasNeverSetIsNull() {
        assertNull(Preferences.getKoitoApiKey())
        assertNull(Preferences.getAudioMuseApiToken())
    }
}
