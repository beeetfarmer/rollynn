package com.cappielloantonio.tempo.ui.dialog

import android.content.ComponentName
import android.content.DialogInterface
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
class WhatsNewDialogTest {
    private var controller: ActivityController<MainActivity>? = null
    private val activity get() = controller!!.get()

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun start() {
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
    }

    private fun showNotes(): AlertDialog {
        WhatsNewDialog.newInstance("v9.0.0", "• New thing\n• Fixed thing").show(activity.supportFragmentManager, WhatsNewDialog.TAG)
        idle()
        return shownDialog()!!
    }

    private fun shownDialog(): AlertDialog? =
        (activity.supportFragmentManager.findFragmentByTag(WhatsNewDialog.TAG) as WhatsNewDialog?)?.dialog as AlertDialog?

    private fun AlertDialog.text(id: Int) = findViewById<TextView>(id)!!.text.toString()

    @Test
    fun itShowsTheVersionAndItsNotes() {
        start()
        val dialog = showNotes()

        assertEquals("What's new in v9.0.0", dialog.text(androidx.appcompat.R.id.alertTitle))
        assertEquals("• New thing\n• Fixed thing", dialog.text(android.R.id.message))
    }

    @Test
    fun itHasASingleButtonThatClosesIt() {
        start()
        val dialog = showNotes()

        assertFalse(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isShown)
        assertFalse(dialog.getButton(DialogInterface.BUTTON_NEUTRAL).isShown)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        idle()

        assertFalse(dialog.isShowing)
        assertNull(activity.supportFragmentManager.findFragmentByTag(WhatsNewDialog.TAG))
    }

    @Test
    fun itSurvivesTheScreenBeingRecreated() {
        start()
        showNotes()

        controller!!.recreate()
        idle()

        val dialog = shownDialog()!!
        assertTrue(dialog.isShowing)
        assertEquals("• New thing\n• Fixed thing", dialog.text(android.R.id.message))
    }

    @Test
    fun launchingAVersionWithoutNotesShowsNothingAndRemembersIt() {
        Preferences.setWhatsNewSeenVersion("v0.0.1")

        start()

        assertNull(shownDialog())
        assertEquals(BuildConfig.VERSION_NAME, Preferences.getWhatsNewSeenVersion())
    }

    @Test
    fun aFreshInstallLaunchShowsNothing() {
        start()

        assertNull(shownDialog())
        assertEquals(BuildConfig.VERSION_NAME, Preferences.getWhatsNewSeenVersion())
    }
}
