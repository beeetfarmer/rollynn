package com.cappielloantonio.tempo.ui.dialog

import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.github.Github
import com.cappielloantonio.tempo.github.models.LatestRelease
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.ui.fragment.SettingsFragment
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.RETURNS_DEEP_STUBS
import org.mockito.Mockito.clearInvocations
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.mock.Calls

// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class GithubTempoUpdateDialogTest {
    private val releaseUrl = "https://github.com/beeetfarmer/rollynn/releases/tag/v99.0.0"
    private var controller: ActivityController<MainActivity>? = null
    private val activity get() = controller!!.get()
    private lateinit var github: Github

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        github = mock(Github::class.java, RETURNS_DEEP_STUBS)
        AppTestEnv.setStatic(App::class.java, "github", github)
        // A fresh install, so the "What's new" notes don't take this launch.
        Preferences.setWhatsNewSeenVersion(com.cappielloantonio.tempo.BuildConfig.VERSION_NAME)
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        AppTestEnv.setStatic(App::class.java, "github", null)
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun githubHas(tag: String) {
        `when`(github.releaseClient.latestRelease).thenReturn(Calls.response(LatestRelease(htmlUrl = releaseUrl, tagName = tag)))
    }

    private fun launch(checksEnabled: Boolean = true) {
        App.getInstance().preferences.edit().putBoolean("github_update_check", checksEnabled).commit()
        clearInvocations(github)
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    private fun shownDialog(): AlertDialog? =
        (activity.supportFragmentManager.findFragmentByTag(GithubTempoUpdateDialog.TAG) as GithubTempoUpdateDialog?)?.dialog as AlertDialog?

    @Test
    fun aNewerReleaseIsAnnounced() {
        githubHas("v99.0.0")

        launch()

        assertTrue(shownDialog()!!.isShowing)
    }

    @Test
    fun withoutInternetNothingIsShownAndTheAppCarriesOn() {
        // A phone on a LAN with no route out: the request fails.
        `when`(github.releaseClient.latestRelease).thenReturn(Calls.failure(java.net.UnknownHostException("api.github.com")))

        launch()

        assertNull(shownDialog())
        assertFalse(activity.isFinishing)
        assertTrue("tried again on the next launch", Preferences.showTempusUpdateDialog())
    }

    @Test
    fun aGithubErrorReplyShowsNothing() {
        // e.g. rate limited, or a captive portal answering instead of GitHub.
        `when`(github.releaseClient.latestRelease).thenReturn(
            Calls.response(retrofit2.Response.error<LatestRelease>(403, "".toResponseBody())),
        )

        launch()

        assertNull(shownDialog())
    }

    @Test
    fun theSameVersionIsNotAnnounced() {
        githubHas(com.cappielloantonio.tempo.BuildConfig.VERSION_NAME)

        launch()

        assertNull(shownDialog())
    }

    @Test
    fun anOlderReleaseIsNotAnnounced() {
        githubHas("v0.0.1")

        launch()

        assertNull(shownDialog())
    }

    @Test
    fun turningChecksOffMeansGithubIsNotAsked() {
        githubHas("v99.0.0")

        launch(checksEnabled = false)

        assertNull(shownDialog())
        verify(github, never()).releaseClient
    }

    @Test
    fun remindMeLaterWaitsADay() {
        githubHas("v99.0.0")
        launch()

        shownDialog()!!.getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        idle()

        assertFalse(Preferences.showTempusUpdateDialog())
        controller!!.pause().stop().destroy()
        launch()
        assertNull(shownDialog())
    }

    @Test
    fun downloadOpensTheReleasePage() {
        githubHas("v99.0.0")
        launch()

        shownDialog()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        val opened = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals(releaseUrl, opened.dataString)
    }

    @Test
    fun thereIsNoSupportButton() {
        githubHas("v99.0.0")
        launch()

        assertFalse(shownDialog()!!.getButton(DialogInterface.BUTTON_NEUTRAL).isShown)
    }

    @Test
    fun itSurvivesTheScreenBeingRecreatedWithoutDoubling() {
        githubHas("v99.0.0")
        launch()

        controller!!.recreate()
        idle()

        assertTrue(shownDialog()!!.isShowing)
        assertEquals(
            1,
            activity.supportFragmentManager.fragments.count { it is GithubTempoUpdateDialog },
        )
        shownDialog()!!.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()
        assertEquals(releaseUrl, shadowOf(activity).nextStartedActivity.dataString)
    }

    @Test
    fun itWaitsWhileTheWhatsNewNotesAreOpen() {
        githubHas("v99.0.0")
        launch(checksEnabled = false)
        WhatsNewDialog.newInstance("v1.0.0", "• New thing").show(activity.supportFragmentManager, WhatsNewDialog.TAG)
        idle()
        App.getInstance().preferences.edit().putBoolean("github_update_check", true).commit()

        controller!!.recreate()
        idle()

        assertNotNull(activity.supportFragmentManager.findFragmentByTag(WhatsNewDialog.TAG))
        assertNull(shownDialog())
    }

    @Test
    fun settingsHasASwitchForTheCheck() {
        launch(checksEnabled = false)
        val args = Bundle().apply { putString(PreferenceFragmentCompat.ARG_PREFERENCE_ROOT, "screen_general") }
        activity.navController.navigate(R.id.settingsFragment, args)
        idle()
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val settings = host.childFragmentManager.primaryNavigationFragment as SettingsFragment

        val switch = settings.findPreference<SwitchPreference>("github_update_check")!!
        assertFalse(switch.isChecked)
        switch.performClick()

        assertTrue(Preferences.isGithubUpdateEnabled())
    }
}
