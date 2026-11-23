package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.content.Context
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** The screen is recreated by a theme or dark-mode change, a rotation, or the process being restored. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class SettingsTabRecreateTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var settings: SettingsFragment

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
        controller.get().navController.navigate(R.id.settingsFragment)
        idle()
        findSettings()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.setStatic(App::class.java, "encryptedPreferences", null)
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun findSettings() {
        val host = controller.get().supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        settings = host.childFragmentManager.primaryNavigationFragment as SettingsFragment
    }

    private fun switchTo(tab: String) {
        ReflectionHelpers.callInstanceMethod<Any?>(settings, "switchCategory", ClassParameter.from(String::class.java, tab))
        idle()
    }

    private fun recreate() {
        // Set just before, as the app logs out when it can't reach the test server. Without a
        // login, recreating the activity goes to the login screen.
        Preferences.setServer("http://server")
        Preferences.setUser("user")
        Preferences.setPassword("password")
        controller.recreate()
        idle()
        findSettings()
    }

    private fun shownTab(): String = settings.preferenceScreen.key

    private fun box(): EditText {
        val fragment = settings.parentFragmentManager.fragments.filterIsInstance<EditTextPreferenceDialogFragmentCompat>().last()
        return (fragment.dialog as AlertDialog).findViewById(android.R.id.edit)!!
    }

    private fun typeInDialogAndRecreate(tab: String, key: String, text: String = "half typed") {
        switchTo(tab)
        settings.preferenceManager.showDialog(settings.findPreference<EditTextPreference>(key)!!)
        idle()
        box().setText(text)

        recreate()
    }

    @Test
    fun aPlainTextDialogSurvives() {
        typeInDialogAndRecreate("screen_playback", "koito_server_url")

        assertEquals("half typed", box().text.toString())
    }

    @Test
    fun aSecretDialogSurvives() {
        typeInDialogAndRecreate("screen_playback", "koito_api_key")

        assertEquals("half typed", box().text.toString())
    }

    @Test
    fun aDialogOnTheGeneralTabSurvives() {
        // This box only takes digits.
        typeInDialogAndRecreate("screen_general", "network_ping_timeout_base", "15")

        assertEquals("15", box().text.toString())
    }

    @Test
    fun theChosenTabIsKept() {
        for (tab in listOf("screen_library", "screen_playback", "screen_general", "screen_appearance")) {
            switchTo(tab)

            recreate()

            assertEquals(tab, shownTab())
        }
    }

    @Test
    fun theKeptTabStillWorks() {
        switchTo("screen_playback")
        recreate()

        assertNotNull(settings.findPreference("koito_server_url"))
        switchTo("screen_library")
        assertEquals("screen_library", shownTab())
    }

    @Test
    fun aFreshScreenStartsOnAppearance() {
        assertEquals("screen_appearance", shownTab())
    }
}
