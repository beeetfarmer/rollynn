package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.subsonic.models.Line
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.ui.adapter.LyricsAdapter
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel
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
import java.time.Duration

/** The lyrics page of the player, fed lyrics and positions directly. */
// Dynamic colours crash Robolectric's theme code, and only exist from SDK 31.
@Config(sdk = [30], qualifiers = "w390dp-h844dp")
@RunWith(RobolectricTestRunner::class)
class LyricsViewTest {
    class Host : AppCompatActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            setTheme(R.style.AppTheme)
            super.onCreate(savedInstanceState)
        }
    }

    private lateinit var controller: ActivityController<Host>
    private lateinit var fragment: PlayerLyricsFragment
    private lateinit var viewModel: PlayerBottomSheetViewModel

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))
        controller = Robolectric.buildActivity(Host::class.java).setup()
        viewModel = ViewModelProvider(controller.get())[PlayerBottomSheetViewModel::class.java]
        fragment = PlayerLyricsFragment()
        controller.get().supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment).commitNow()
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle(ms: Long = 0) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Suppress("UNCHECKED_CAST")
    private fun <T> liveData(name: String): MutableLiveData<T> {
        val field = PlayerBottomSheetViewModel::class.java.getDeclaredField(name)
        field.isAccessible = true
        return field.get(viewModel) as MutableLiveData<T>
    }

    private fun line(start: Int, text: String, background: String? = null) = Line().apply {
        this.start = start
        value = text
        this.background = background
    }

    private fun show(synced: Boolean, vararg lines: Line) {
        val structured = StructuredLyrics().apply {
            this.synced = synced
            line = lines.toList()
        }
        liveData<LyricsList>("lyricsListLiveData").value = LyricsList().apply { structuredLyrics = listOf(structured) }
        idle()
    }

    /** Four lines and a long break: 0 s, 3 s (with background vocals), break from 6 s, 13 s. */
    private fun showSong() = show(
        true,
        line(0, "First line"),
        line(3000, "Second line", "(ooh)"),
        line(6000, ""),
        line(13000, "After the break"),
    )

    private fun frame(position: Long) {
        fragment.onFrame(position, 60_000)
        idle(600)
    }

    private val root get() = fragment.requireView()
    private val list get() = root.findViewById<RecyclerView>(R.id.lyrics_list)
    private val adapter get() = list.adapter as LyricsAdapter
    private fun holder(i: Int) = list.findViewHolderForAdapterPosition(i)!!
    private fun line(i: Int) = holder(i) as LyricsAdapter.LineHolder
    private fun <T : View> view(id: Int) = root.findViewById<T>(id)

    @Test
    fun syncedLinesAndTheBreakAreListed() {
        showSong()

        assertTrue(adapter.synced)
        assertEquals(listOf("First line", "Second line", "", "After the break"), adapter.items.map { it.text })
        assertTrue(adapter.items[2].isInstrumental)
        assertEquals(View.GONE, view<View>(R.id.lyrics_not_synced_label).visibility)
        assertEquals(View.GONE, view<View>(R.id.lyrics_empty_label).visibility)
    }

    @Test
    fun theCurrentLineFillsAsItIsSung() {
        showSong()

        frame(4500)

        assertEquals(1, adapter.current)
        assertEquals("half of \"Second line\"", 5.5f, line(1).text.sungChars, 0.01f)
        assertEquals(-1f, line(0).text.sungChars, 0f)
        assertEquals(-1f, line(3).text.sungChars, 0f)
    }

    @Test
    fun otherLinesAreSmallerAndDimmed() {
        showSong()

        frame(4500)

        assertEquals(1f, holder(1).itemView.scaleX, 0f)
        assertEquals(LyricsAdapter.DISTANT_SCALE, holder(0).itemView.scaleX, 0.001f)
        assertEquals(LyricsAdapter.DISTANT_SCALE, holder(3).itemView.scaleX, 0.001f)
        assertEquals(LyricsAdapter.UNSUNG, line(0).text.currentTextColor)
    }

    @Test
    fun backgroundVocalsShowUnderTheirLine() {
        showSong()
        frame(4500)

        val secondary = holder(1).itemView.findViewById<TextView>(R.id.lyrics_line_secondary)
        assertEquals(View.VISIBLE, secondary.visibility)
        assertEquals("(ooh)", secondary.text.toString())
        assertEquals(LyricsAdapter.SECONDARY_CURRENT, secondary.currentTextColor)
        assertEquals(View.GONE, holder(0).itemView.findViewById<View>(R.id.lyrics_line_secondary).visibility)
    }

    @Test
    fun theBreakLightsItsDotsAsItPasses() {
        showSong()

        frame(6100)
        val dots = listOf(R.id.lyrics_dot_1, R.id.lyrics_dot_2, R.id.lyrics_dot_3).map { holder(2).itemView.findViewById<View>(it) }
        assertEquals(2, adapter.current)
        assertEquals(listOf(1f, 0.45f, 0.45f), dots.map { it.alpha })

        frame(11_000)
        assertEquals(listOf(1f, 1f, 1f), dots.map { it.alpha })
    }

    @Test
    fun aPassedLineLosesItsFill() {
        showSong()
        frame(4500)

        frame(13_500)

        assertEquals(-1f, line(1).text.sungChars, 0f)
        assertEquals(3, adapter.current)
    }

    @Test
    fun theCurrentLineSitsAtTheAnchor() {
        showSong()

        frame(13_500)

        val anchor = (110 * root.resources.displayMetrics.density).toInt()
        assertEquals(anchor, list.paddingTop)
        assertEquals(anchor, holder(3).itemView.top)
    }

    @Test
    fun switchingToTheLyricsPutsTheCurrentLineAtTheAnchorAtOnce() {
        // The lyrics and the position arrive before the page is first laid out, as when the
        // lyrics page is swiped to mid-song.
        controller.get().supportFragmentManager.beginTransaction().remove(fragment).commitNow()
        showSong()
        fragment = PlayerLyricsFragment()
        controller.get().supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment).commitNow()
        fragment.onFrame(13_500, 60_000)

        idle()

        val anchor = (110 * root.resources.displayMetrics.density).toInt()
        assertEquals(anchor, holder(3).itemView.top)
    }

    @Test
    fun afterTheUserScrollsItWaitsBeforeFollowingAgain() {
        showSong()
        frame(500)
        val before = rows()

        // Not far enough to reach the end, where a relayout would pull the list back.
        drag(list, 40f)
        val dragged = rows()
        assertTrue("moved by the user", dragged != before)
        fragment.onFrame(13_500, 60_000)
        idle(1_000)
        assertEquals("not followed yet", dragged, rows())

        idle(PlayerLyricsFragment.RESUME_FOLLOW_MS + 1_500)
        assertEquals(list.paddingTop, holder(3).itemView.top)
    }

    /** Each row on screen, with where its top is. */
    private fun rows() = (0 until list.childCount).map { list.getChildAdapterPosition(list.getChildAt(it)) to list.getChildAt(it).top }

    private fun drag(view: View, dy: Float) {
        val t = SystemClock.uptimeMillis()
        view.dispatchTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 100f, 600f, 0))
        for (step in 1..10) {
            view.dispatchTouchEvent(MotionEvent.obtain(t, t + step * 16, MotionEvent.ACTION_MOVE, 100f, 600f - dy * step / 10, 0))
        }
        view.dispatchTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 100f, 600f - dy, 0))
        idle()
    }

    @Test
    fun linesSayWhereTheyJumpTo() {
        showSong()

        assertEquals("Jump to line: First line", holder(0).itemView.contentDescription)
        assertEquals("Instrumental break", holder(2).itemView.contentDescription)
        assertTrue(holder(0).itemView.isClickable)
    }

    @Test
    fun unsyncedLyricsAreShownPlainly() {
        show(false, line(0, "Just words"), line(0, "More words"))

        assertFalse(adapter.synced)
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_not_synced_label).visibility)
        assertEquals(Color.WHITE, line(0).text.currentTextColor)
        assertEquals(1f, holder(1).itemView.scaleX, 0f)
        assertFalse(holder(0).itemView.isClickable)

        frame(5000)
        assertEquals(-1, adapter.current)
        assertEquals(-1f, line(0).text.sungChars, 0f)
    }

    @Test
    fun plainTextLyricsAreShownUnsynced() {
        liveData<String>("lyricsLiveData").value = "One\n\nTwo"
        idle()

        assertEquals(listOf("One", "Two"), adapter.items.map { it.text })
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_not_synced_label).visibility)
    }

    @Test
    fun withoutLyricsItSaysSo() {
        show(true)

        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_empty_label).visibility)
        assertEquals("Lyrics aren't available for this song.", view<TextView>(R.id.lyrics_empty_label).text.toString())
        assertEquals(0, adapter.itemCount)
    }

    @Test
    fun aPodcastDescriptionIsOrdinaryText() {
        liveData<String>("descriptionLiveData").value = "About this episode"
        idle()

        val size = line(0).text.textSize
        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, root.resources.displayMetrics), size, 0.5f)
        assertEquals(View.GONE, view<View>(R.id.lyrics_not_synced_label).visibility)
    }

    @Test
    fun withTheControlsHiddenTheLyricsGrowAndTheHeaderBecomesAPill() {
        showSong()
        frame(4500)

        viewModel.setLyricsImmersive(true)
        idle(1_000)

        assertTrue(adapter.immersive)
        val sp36 = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 36f, root.resources.displayMetrics)
        assertEquals(sp36, line(1).text.textSize, 0.5f)
        assertEquals(1f, list.scaleX, 0f)
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_pill).visibility)
        assertEquals(View.INVISIBLE, view<View>(R.id.lyrics_header).visibility)
        assertEquals(View.INVISIBLE, view<View>(R.id.lyrics_actions_bar).visibility)
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_immersive_hint).visibility)
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_immersive_progress).visibility)

        viewModel.setLyricsImmersive(false)
        idle(1_000)

        assertFalse(adapter.immersive)
        assertEquals(View.VISIBLE, view<View>(R.id.lyrics_header).visibility)
        assertEquals(View.GONE, view<View>(R.id.lyrics_pill).visibility)
    }

    private val anchor get() = (110 * root.resources.displayMetrics.density).toInt()
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, root.resources.displayMetrics)

    @Test
    fun partWayThroughTheTextIsARealSizeBetweenTheTwo() {
        showSong()
        frame(4500)

        fragment.applyGrowth(0.5f)
        idle()

        assertEquals(sp(33.5f), line(1).text.textSize, 0.5f)
        assertEquals(sp(22f), line(1).itemView.findViewById<android.widget.TextView>(R.id.lyrics_line_secondary).textSize, 0.5f)
        assertEquals(1f, list.scaleX, 0f)
        assertEquals(1f, list.scaleY, 0f)
    }

    @Test
    fun everyFrameKeepsTheCurrentLineAtTheAnchor() {
        showSong()
        frame(4500)

        for (g in listOf(0.1f, 0.35f, 0.6f, 0.85f, 1f, 0.5f, 0f)) {
            fragment.applyGrowth(g)
            idle()
            assertEquals("at $g", anchor, holder(1).itemView.top)
        }
    }

    @Test
    fun theGapsGrowWithTheText() {
        showSong()
        frame(4500)
        val density = root.resources.displayMetrics.density

        fragment.applyGrowth(0.5f)
        idle()

        assertEquals((32 * density).toInt(), holder(0).itemView.paddingBottom)
    }

    @Test
    fun goingInAndOutEndsAtTheExactSizes() {
        showSong()
        frame(4500)

        viewModel.setLyricsImmersive(true)
        idle(1_000)
        assertEquals(sp(36f), line(1).text.textSize, 0.5f)
        assertEquals(anchor, holder(1).itemView.top)

        viewModel.setLyricsImmersive(false)
        idle(1_000)
        assertEquals(0f, adapter.growth, 0f)
        assertEquals(sp(31f), line(1).text.textSize, 0.5f)
        assertEquals(anchor, holder(1).itemView.top)
    }

    @Test
    fun theLyricsButtonsGoAtOnceRatherThanSlideDownWithThePage() {
        showSong()

        viewModel.setLyricsImmersive(true)
        idle()

        assertEquals(View.INVISIBLE, view<View>(R.id.lyrics_actions_bar).visibility)
        assertEquals(0f, view<View>(R.id.lyrics_actions_bar).alpha, 0f)
    }

    @Test
    fun theThinProgressLineFollowsPlayback() {
        viewModel.setLyricsImmersive(true)
        idle(1_000)

        fragment.onFrame(15_000, 60_000)

        assertEquals(250, view<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.lyrics_immersive_progress).progress)
    }

    @Test
    fun newLyricsStartFresh() {
        showSong()
        frame(4500)

        show(true, line(0, "Another song"))

        assertEquals(listOf("Another song"), adapter.items.map { it.text })
        assertNull(list.findViewHolderForAdapterPosition(1))
    }
}
