package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.text.method.PasswordTransformationMethod
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import androidx.preference.PreferenceFragmentCompat
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Drives the real dialog of every setting that holds a secret. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(ParameterizedRobolectricTestRunner::class)
class SecretSettingsDialogTest(private val key: String, private val screen: String) {
    companion object {
        @JvmStatic
        @Parameters(name = "{0}")
        fun secrets() = listOf(
            arrayOf("last_fm_api_key", "screen_lastfm"),
            arrayOf("koito_api_key", "screen_playback"),
            arrayOf("audiomuse_api_token", "screen_playback"),
            arrayOf("translation_api_key", "screen_playback"),
            arrayOf("popinn_password", "screen_popinn"),
        )
    }

    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var settings: SettingsFragment
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        // The Android keystore is missing under Robolectric, so a second file stands in for the encrypted store.
        AppTestEnv.setStatic(
            App::class.java,
            "encryptedPreferences",
            app.getSharedPreferences("fake_encrypted", Context.MODE_PRIVATE),
        )
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", null)
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun save(value: String) = when (key) {
        "last_fm_api_key" -> Preferences.setLastFmApiKey(value)
        "koito_api_key" -> Preferences.setKoitoApiKey(value)
        "audiomuse_api_token" -> Preferences.setAudioMuseApiToken(value)
        "translation_api_key" -> Preferences.setTranslationApiKey(value)
        else -> Preferences.setPopinnPassword(value)
    }

    private fun saved(): String = when (key) {
        "last_fm_api_key" -> Preferences.getLastFmApiKey()
        "koito_api_key" -> Preferences.getKoitoApiKey()
        "audiomuse_api_token" -> Preferences.getAudioMuseApiToken()
        "translation_api_key" -> Preferences.getTranslationApiKey()
        else -> Preferences.getPopinnPassword()
    }.orEmpty()

    private fun openScreen() {
        val args = Bundle().apply { putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screen) }
        activity.navController.navigate(R.id.settingsFragment, args)
        idle()
        findSettings()
    }

    private fun findSettings() {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        settings = host.childFragmentManager.primaryNavigationFragment as SettingsFragment
    }

    private fun shownDialog(): AlertDialog {
        val fragment = settings.parentFragmentManager.fragments.filterIsInstance<EditTextPreferenceDialogFragmentCompat>().last()
        return fragment.dialog as AlertDialog
    }

    private fun openDialog(): AlertDialog {
        val preference = settings.findPreference<EditTextPreference>(key)!!
        settings.preferenceManager.showDialog(preference)
        idle()
        return shownDialog()
    }

    private fun AlertDialog.box(): EditText = findViewById(android.R.id.edit)!!

    private fun AlertDialog.press(button: Int) {
        getButton(button).performClick()
        idle()
    }

    private fun openWithASavedSecret(): AlertDialog {
        save("saved-secret")
        openScreen()
        return openDialog()
    }

    @Test
    fun okWithoutTypingKeepsTheSecret() {
        openWithASavedSecret().press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("saved-secret", saved())
    }

    @Test
    fun theBoxOpensWithTheSavedSecret() {
        assertEquals("saved-secret", openWithASavedSecret().box().text.toString())
    }

    @Test
    fun theSecretIsMasked() {
        assertTrue(openWithASavedSecret().box().transformationMethod is PasswordTransformationMethod)
    }

    @Test
    fun theCursorStartsAtTheEnd() {
        assertEquals("saved-secret".length, openWithASavedSecret().box().selectionStart)
    }

    @Test
    fun theSecretCanStillBeClearedOnPurpose() {
        val dialog = openWithASavedSecret()

        dialog.box().setText("")
        dialog.press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("", saved())
    }

    @Test
    fun typingReplacesTheSecret() {
        val dialog = openWithASavedSecret()

        dialog.box().setText("new-secret")
        dialog.press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("new-secret", saved())
    }

    @Test
    fun cancelKeepsTheSecret() {
        val dialog = openWithASavedSecret()

        dialog.box().setText("half typed")
        dialog.press(DialogInterface.BUTTON_NEGATIVE)

        assertEquals("saved-secret", saved())
    }

    @Test
    fun reopeningShowsWhatWasJustSaved() {
        val dialog = openWithASavedSecret()
        dialog.box().setText("new-secret")
        dialog.press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("new-secret", openDialog().box().text.toString())
    }

    @Test
    fun reopeningAfterClearingIsEmpty() {
        val dialog = openWithASavedSecret()
        dialog.box().setText("")
        dialog.press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("", openDialog().box().text.toString())
    }

    @Test
    fun withNothingSavedTheBoxIsEmpty() {
        openScreen()

        assertEquals("", openDialog().box().text.toString())
    }

    @Test
    fun theSecretNeverReachesPlainStorage() {
        openWithASavedSecret().press(DialogInterface.BUTTON_POSITIVE)

        assertFalse(App.getInstance().preferences.contains(key))
    }

    @Test
    fun whatWasTypedSurvivesTheScreenBeingRecreated() {
        // Without a login, recreating the activity goes to the login screen.
        Preferences.setServer("http://server")
        Preferences.setUser("user")
        Preferences.setPassword("password")
        openWithASavedSecret().box().setText("half typed")

        controller.recreate()
        idle()
        findSettings()

        val box = shownDialog().box()
        assertEquals("half typed", box.text.toString())
        assertTrue("still masked", box.transformationMethod is PasswordTransformationMethod)
    }

    @Test
    fun itWorksAfterSwitchingToTheTab() {
        if (screen != "screen_playback") return
        save("saved-secret")
        activity.navController.navigate(R.id.settingsFragment)
        idle()
        findSettings()
        ReflectionHelpers.callInstanceMethod<Any?>(
            settings, "switchCategory", ClassParameter.from(String::class.java, screen),
        )
        idle()

        val box = openDialog().box()

        assertEquals("saved-secret", box.text.toString())
        assertTrue(box.transformationMethod is PasswordTransformationMethod)
    }

    @Test
    fun okOnTheSameLastFmKeyRetriesIt() {
        if (key != "last_fm_api_key") return
        save("saved-secret")
        Preferences.setLastFmRejectedAt(System.currentTimeMillis())
        openScreen()

        openDialog().press(DialogInterface.BUTTON_POSITIVE)

        assertEquals("saved-secret", saved())
        assertEquals(0L, Preferences.getLastFmRejectedAt())
    }
}
