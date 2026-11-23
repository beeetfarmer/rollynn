package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.media3.common.Player
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
import org.robolectric.util.ReflectionHelpers

// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30])
@RunWith(RobolectricTestRunner::class)
class MinimizedPlayerTapTest {
    private lateinit var controller: ActivityController<MainActivity>
    private val activity get() = controller.get()
    private val fragment get() = activity.supportFragmentManager.findFragmentByTag("PlayerBottomSheet") as PlayerBottomSheetFragment
    private val minimized: View get() = fragment.requireView().findViewById(R.id.player_compact_layout)
    private val sheet get() = BottomSheetBehavior.from(activity.findViewById<View>(R.id.player_bottom_sheet))

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        App.getInstance().preferences.edit().putBoolean("dock_collapsible_player", true).commit()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        idle()
        sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun connect(playing: Boolean): Player {
        val player = mock(Player::class.java)
        `when`(player.isPlaying).thenReturn(playing)
        ReflectionHelpers.setField(fragment, "player", player)
        return player
    }

    @Test
    fun itStartsMinimized() {
        assertTrue(fragment.isCompactShown)
    }

    @Test
    fun oneTapOpensNowPlaying() {
        minimized.performClick()
        idle()

        assertEquals(BottomSheetBehavior.STATE_EXPANDED, sheet.state)
    }

    @Test
    fun oneTapDoesNotJustGrowIntoTheMiniPlayer() {
        minimized.performClick()
        idle()

        assertTrue("stays minimized underneath", activity.isMiniPlayerCompact)
    }

    @Test
    fun closingNowPlayingLeavesItMinimized() {
        minimized.performClick()
        idle()

        sheet.state = BottomSheetBehavior.STATE_COLLAPSED
        idle()

        assertTrue(fragment.isCompactShown)
    }

    @Test
    fun longPressPausesWhilePlaying() {
        val player = connect(playing = true)

        assertTrue(minimized.performLongClick())

        verify(player).pause()
        verify(player, never()).play()
    }

    @Test
    fun longPressPlaysWhilePaused() {
        val player = connect(playing = false)

        minimized.performLongClick()

        verify(player).play()
        verify(player, never()).pause()
    }

    @Test
    fun longPressDoesNotOpenNowPlaying() {
        connect(playing = true)

        minimized.performLongClick()
        idle()

        assertEquals(BottomSheetBehavior.STATE_COLLAPSED, sheet.state)
    }

    @Test
    fun longPressBeforeThePlayerConnectsDoesNothing() {
        assertFalse(minimized.performLongClick())
    }

    @Test
    fun screenReadersAreToldWhatBothGesturesDo() {
        val info = minimized.createAccessibilityNodeInfo()

        assertEquals("Open now playing", info.contentDescription)
        val longPress = info.actionList.single { it.id == AccessibilityNodeInfo.ACTION_LONG_CLICK }
        assertEquals("Play or pause", longPress.label)
    }
}
