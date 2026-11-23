package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.ui.view.BlobBackgroundView
import com.cappielloantonio.tempo.ui.view.TouchWatchConstraintLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.util.ReflectionHelpers

/** Opening Now Playing builds the player screen, lyrics mode included. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class NowPlayingOpensTest {
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

    /** The player screen, built as the player's pager builds it when Now Playing opens. */
    private fun openNowPlaying(): PlayerControllerFragment {
        val player = PlayerControllerFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, player).commitNow()
        idle()
        return player
    }

    @Test
    fun theLyricsBackgroundSitsBehindTheControls() {
        val player = openNowPlaying()

        val controlView = player.requireView() as ViewGroup
        assertTrue(controlView.getChildAt(0) is BlobBackgroundView)
        assertEquals(View.GONE, controlView.getChildAt(0).visibility)
    }

    @Test
    fun touchesOnThePlayerAreWatched() {
        val player = openNowPlaying()

        val root = player.requireView().findViewById<TouchWatchConstraintLayout>(R.id.now_playing_media_controller_layout)
        assertNotNull(root.touchWatcher)
    }

    @Test
    fun openingTheLyricsShowsTheirBackground() {
        val player = openNowPlaying()

        player.requireView().findViewById<ViewPager2>(R.id.player_media_cover_view_pager).setCurrentItem(1, false)
        idle()

        val mode = ReflectionHelpers.getField<LyricsModeController>(player, "lyricsMode")
        assertTrue(mode.lyricsShown)
        assertEquals(View.VISIBLE, (player.requireView() as ViewGroup).getChildAt(0).visibility)
    }

    private fun navigationBar(view: View, height: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 0, 0, height))
            .build()
        ViewCompat.dispatchApplyWindowInsets(view, insets)
    }

    @Test
    fun theControlsStayAboveTheNavigationBar() {
        val player = openNowPlaying()

        navigationBar(player.requireView(), 63)

        val controls = player.requireView().findViewById<View>(R.id.now_playing_media_controller_layout)
        assertEquals(63, controls.paddingBottom)
    }

    @Test
    fun theLyricsBackgroundRunsUnderTheNavigationBar() {
        val player = openNowPlaying()

        navigationBar(player.requireView(), 63)

        val controlView = player.requireView() as ViewGroup
        assertEquals(0, controlView.paddingBottom)
        val background = controlView.getChildAt(0)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, background.layoutParams.height)
    }

    @Test
    fun theQueueStaysAboveTheNavigationBar() {
        val queue = PlayerQueueFragment()
        activity.supportFragmentManager.beginTransaction().add(android.R.id.content, queue).commitNow()
        idle()
        val list = queue.requireView().findViewById<View>(R.id.player_queue_recycler_view)
        val bottom = list.paddingBottom

        navigationBar(queue.requireView(), 63)

        assertEquals(bottom + 63, list.paddingBottom)
    }

    @Test
    fun thePlayerPagesAreNotPaddedForThem() {
        val sheet = activity.supportFragmentManager.findFragmentByTag("PlayerBottomSheet") as PlayerBottomSheetFragment
        val pager = sheet.requireView().findViewById<View>(R.id.player_body_layout)

        navigationBar(sheet.requireView(), 63)

        assertEquals(0, pager.paddingBottom)
    }

    @Test
    fun theSheetBackgroundFadesWithThePlayerSoNoBlockShowsWhileClosing() {
        val sheet = activity.supportFragmentManager.findFragmentByTag("PlayerBottomSheet") as PlayerBottomSheetFragment
        fun background() = (sheet.requireView().background as android.graphics.drawable.ColorDrawable).color
        val full = com.cappielloantonio.tempo.util.UIUtil.getPlayerBackgroundColor(activity)

        sheet.setBodyVisibility(true)
        sheet.setBodyAlpha(1f)
        assertEquals(full, background())

        // Closing: the player has faded out, but the sheet is still settling.
        sheet.setBodyAlpha(0f)
        assertEquals(0, android.graphics.Color.alpha(background()))

        sheet.setBodyAlpha(0.5f)
        assertEquals(Math.round(android.graphics.Color.alpha(full) * 0.5f), android.graphics.Color.alpha(background()))

        sheet.setBodyVisibility(false)
        assertEquals(android.graphics.Color.TRANSPARENT, background())
    }
}
