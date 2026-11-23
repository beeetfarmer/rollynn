package com.cappielloantonio.tempo.ui.fragment

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.constraintlayout.widget.ConstraintLayout
import org.robolectric.RuntimeEnvironment
import com.cappielloantonio.tempo.ui.view.BlobBackgroundView
import com.cappielloantonio.tempo.ui.view.TouchWatchConstraintLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class LyricsModeControllerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var root: TouchWatchConstraintLayout
    private lateinit var pager: View
    private lateinit var guideline: View
    private lateinit var controls: View
    private lateinit var background: BlobBackgroundView
    private val changes = mutableListOf<Boolean>()

    @Before
    fun setUp() {
        root = TouchWatchConstraintLayout(context)
        guideline = View(context).apply { id = View.generateViewId() }
        pager = View(context).apply { id = View.generateViewId() }
        controls = View(context).apply { id = View.generateViewId() }
        root.addView(pager, ConstraintLayout.LayoutParams(0, 0).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToTop = guideline.id
        })
        root.addView(guideline)
        root.addView(controls)
        background = BlobBackgroundView(context).apply { visibility = View.GONE }
    }

    private var idleMs = IDLE_MS

    private fun controller(immersiveAllowed: Boolean = true) =
        LyricsModeController(root, pager, setOf(pager, guideline), background, immersiveAllowed, { idleMs }) { changes += it }

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun touch(action: Int): Boolean {
        val now = SystemClock.uptimeMillis()
        return root.dispatchTouchEvent(MotionEvent.obtain(now, now, action, 10f, 10f, 0))
    }

    private fun playingLyrics() = controller().apply {
        setLyricsShown(true)
        setPlaying(true)
    }

    @Test
    fun theControlsHideAfterFourQuietSeconds() {
        val mode = playingLyrics()

        pass(3_900)
        assertFalse(mode.immersive)
        pass(200)

        assertTrue(mode.immersive)
        assertEquals(listOf(true), changes)
    }

    @Test
    fun whenHiddenTheyFadeAndSlideDownAndThePagerTakesTheScreen() {
        val mode = playingLyrics()
        pass(IDLE_MS + 1_000)

        assertTrue(mode.immersive)
        assertEquals(0f, controls.alpha, 0f)
        assertTrue(controls.translationY > 0)
        assertEquals(1f, pager.alpha, 0f)
        val params = pager.layoutParams as ConstraintLayout.LayoutParams
        assertEquals(ConstraintLayout.LayoutParams.PARENT_ID, params.bottomToBottom)
    }

    @Test
    fun aTouchKeepsThemUp() {
        val mode = playingLyrics()

        pass(3_000)
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)
        pass(3_000)

        assertFalse(mode.immersive)
        pass(1_500)
        assertTrue(mode.immersive)
    }

    @Test
    fun aTouchWhileHiddenBringsThemBackAndGoesNoFurther() {
        val mode = playingLyrics()
        pass(IDLE_MS + 100)
        var reached = 0
        controls.setOnTouchListener { _, _ -> reached++; true }

        assertTrue("taken", touch(MotionEvent.ACTION_DOWN))
        assertTrue(touch(MotionEvent.ACTION_MOVE))
        assertTrue(touch(MotionEvent.ACTION_UP))
        pass(1_000)

        assertFalse(mode.immersive)
        assertEquals(0, reached)
        assertEquals(1f, controls.alpha, 0f)
        assertEquals(0f, controls.translationY, 0f)
        assertEquals(guideline.id, (pager.layoutParams as ConstraintLayout.LayoutParams).bottomToTop)
        assertEquals(listOf(true, false), changes)
    }

    @Test
    fun touchesPassThroughWhileTheControlsShow() {
        playingLyrics()

        assertFalse(touch(MotionEvent.ACTION_DOWN))
    }

    @Test
    fun theyStayUpWhilePaused() {
        val mode = controller().apply { setLyricsShown(true) }

        pass(10_000)

        assertFalse(mode.immersive)
    }

    @Test
    fun pausingFromElsewhereBringsThemBack() {
        val mode = playingLyrics()
        pass(IDLE_MS + 100)

        mode.setPlaying(false)

        assertFalse(mode.immersive)
    }

    @Test
    fun theyStayUpOffTheLyricsPage() {
        val mode = controller().apply { setPlaying(true) }

        pass(10_000)

        assertFalse(mode.immersive)
    }

    @Test
    fun leavingTheLyricsPageBringsThemBack() {
        val mode = playingLyrics()
        pass(IDLE_MS + 100)

        mode.setLyricsShown(false)

        assertFalse(mode.immersive)
        assertEquals(1f, controls.alpha, 0f)
    }

    @Test
    fun theyStayUpWithTalkBack() {
        shadowOf(context.getSystemService(AccessibilityManager::class.java)).setTouchExplorationEnabled(true)
        val mode = playingLyrics()

        pass(10_000)

        assertFalse(mode.immersive)
    }

    @Test
    fun theyStayUpInLandscape() {
        val mode = controller(immersiveAllowed = false).apply {
            setLyricsShown(true)
            setPlaying(true)
        }

        pass(10_000)

        assertFalse(mode.immersive)
    }

    @Test
    fun theyStayUpWhileThePlayerIsAway() {
        val mode = playingLyrics()
        mode.setActive(false)

        pass(10_000)
        assertFalse(mode.immersive)

        mode.setActive(true)
        pass(IDLE_MS + 100)
        assertTrue(mode.immersive)
    }

    @Test
    fun theColourBackgroundShowsOnlyWithTheLyrics() {
        val mode = controller()

        mode.setLyricsShown(true)
        pass(1_000)
        assertEquals(View.VISIBLE, background.visibility)
        assertEquals(1f, background.alpha, 0f)

        mode.setLyricsShown(false)
        pass(1_000)
        assertEquals(View.GONE, background.visibility)
    }

    @Test
    fun theScrimDarkensWhenTheControlsHide() {
        val mode = playingLyrics()
        assertEquals(0.28f, background.scrim, 0f)

        pass(IDLE_MS + 100)

        assertTrue(mode.immersive)
        assertEquals(0.30f, background.scrim, 0f)
    }

    @Test
    fun releaseStopsWatching() {
        playingLyrics().release()

        assertEquals(null, root.touchWatcher)
        pass(10_000)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun theWaitIsTheChosenOne() {
        idleMs = 7_000
        val mode = playingLyrics()

        pass(6_900)
        assertFalse(mode.immersive)
        pass(200)
        assertTrue(mode.immersive)
    }

    @Test
    fun zeroNeverHidesTheControls() {
        idleMs = 0
        val mode = playingLyrics()

        pass(60_000)

        assertFalse(mode.immersive)
        assertTrue(changes.isEmpty())
    }

    @Test
    fun aChangedSettingAppliesFromTheNextTouch() {
        val mode = playingLyrics()
        idleMs = 0
        touch(MotionEvent.ACTION_DOWN)
        touch(MotionEvent.ACTION_UP)

        pass(60_000)

        assertFalse(mode.immersive)
    }

    private companion object {
        const val IDLE_MS = 4_000L
    }
}
