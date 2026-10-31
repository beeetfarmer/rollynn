package com.cappielloantonio.tempo.ui.fragment

import android.content.ComponentName
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.model.Queue
import com.cappielloantonio.tempo.service.MediaService
import com.cappielloantonio.tempo.testutil.AppTestEnv
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

// Sized like a phone so the list gets a real layout, with room for 12 rows.
@Config(qualifiers = "w360dp-h800dp")
@RunWith(RobolectricTestRunner::class)
class PlayerQueueScrollTest {

    private lateinit var controller: ActivityController<AppCompatActivity>
    private val fragment = PlayerQueueFragment()

    @Before
    fun setUp() {
        AppTestEnv.install()
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, MediaService::class.java))

        controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        controller.get().setTheme(R.style.AppTheme)
        controller.setup()
        controller.get().supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
        idle()
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
        AppTestEnv.uninstall()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun list() = controller.get().findViewById<RecyclerView>(R.id.player_queue_recycler_view)

    /** Room delivers on a background thread, so this waits for the rows to reach the list. */
    private fun loadQueue(size: Int) {
        AppTestEnv.db.queueDao().insertAll((0 until size).map { Queue("track$it", it) })
        val deadline = System.currentTimeMillis() + 5_000
        while (list().adapter!!.itemCount < size && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
            idle()
        }
        assertEquals(size, list().adapter!!.itemCount)
        idle()
    }

    private fun topTrack(): Int {
        idle()
        return (list().layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
    }

    @Test
    fun aLoadedQueueScrollsThePlayingTrackToTheTop() {
        loadQueue(60)

        fragment.scrollToTrack(30)

        assertEquals(30, topTrack())
    }

    @Test
    fun aQueueThatLoadsAfterOpeningStillScrollsToThePlayingTrack() {
        fragment.scrollToTrack(30)

        loadQueue(60)

        assertEquals(30, topTrack())
    }

    @Test
    fun aTrackNearTheEndOfTheQueueStillReachesTheTop() {
        loadQueue(20)

        fragment.scrollToTrack(16)

        assertEquals(16, topTrack())
    }

    @Test
    fun theLastTrackReachesTheTop() {
        loadQueue(20)

        fragment.scrollToTrack(19)

        assertEquals(19, topTrack())
    }

    @Test
    fun aShortQueueThatStartsOnItsLastTrackStillReachesTheTop() {
        fragment.scrollToTrack(2)

        loadQueue(3)

        assertEquals(2, topTrack())
    }

    @Test
    fun theFirstTrackStaysAtTheTop() {
        loadQueue(60)

        fragment.scrollToTrack(0)

        assertEquals(0, topTrack())
    }

    @Test
    fun aLaterQueueUpdateLeavesTheScrollPositionAlone() {
        fragment.scrollToTrack(30)
        loadQueue(60)
        assertEquals(30, topTrack())
        (list().layoutManager as LinearLayoutManager).scrollToPositionWithOffset(5, 0)
        assertEquals(5, topTrack())

        loadQueue(61)

        assertEquals(5, topTrack())
    }
}
