package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Bundle
import android.os.Looper
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class ScrobbleSettingsTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun open(screen: String): SettingsFragment {
        val args = Bundle().apply { putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, screen) }
        activity.navController.navigate(R.id.settingsFragment, args)
        idle()
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as SettingsFragment
    }

    private fun SettingsFragment.preference(key: String) = findPreference<Preference>(key)!!

    private fun text(id: Int) = activity.getString(id)

    @Test
    fun theProNoteShowsOnTheLastFmEntry() {
        assertEquals(
            "Needs Pro from Dec 1 to fetch scrobbles",
            open("screen_general").preference("screen_lastfm").summary.toString(),
        )
    }

    @Test
    fun theProNoteShowsInsideTheLastFmScreen() {
        assertEquals(
            text(R.string.settings_lastfm_pro_note),
            open("screen_lastfm").preference("last_fm_status").summary.toString(),
        )
    }

    @Test
    fun aRefusedKeyShowsThatLastFmIsOff() {
        Preferences.setLastFmRejectedAt(1)

        assertEquals(
            text(R.string.settings_lastfm_rejected),
            open("screen_general").preference("screen_lastfm").summary.toString(),
        )
    }

    @Test
    fun enteringAKeyClearsTheOffMessage() {
        Preferences.setLastFmRejectedAt(1)
        val settings = open("screen_lastfm")

        settings.preference("last_fm_api_key").onPreferenceChangeListener!!
            .onPreferenceChange(settings.preference("last_fm_api_key"), "new key")

        assertEquals(text(R.string.settings_lastfm_pro_note), settings.preference("last_fm_status").summary.toString())
    }
}
