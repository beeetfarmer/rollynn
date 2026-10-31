package com.cappielloantonio.tempo.ui.adapter

import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.media3.session.MediaBrowser
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.google.common.util.concurrent.Futures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PlayerSongQueueAdapterTest {

    private val adapter = PlayerSongQueueAdapter(null)

    @Before
    fun setUp() {
        AppTestEnv.install()
        // A chosen folder keeps the row from starting the download service.
        AppTestEnv.setPreference("download_directory_uri", "content://downloads/tree")
        adapter.setMediaBrowserListenableFuture(Futures.immediateFuture(mock(MediaBrowser::class.java)))
        adapter.setItems(listOf(Child("a"), Child("b")))
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun row(position: Int): PlayerSongQueueAdapter.ViewHolder {
        val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme)
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.bindViewHolder(holder, position)
        return holder
    }

    private fun PlayerSongQueueAdapter.ViewHolder.icon() =
        shadowOf(item.playPauseIcon.drawable).createdFromResId

    @Test
    fun theTrackPlayingShowsAPlayIconOverItsCover() {
        adapter.setPlaybackState("a", true)

        val row = row(0)

        assertEquals(View.VISIBLE, row.item.playPauseIcon.visibility)
        assertEquals(View.VISIBLE, row.item.coverArtOverlay.visibility)
        assertEquals(R.drawable.ic_play, row.icon())
    }

    @Test
    fun aPausedTrackShowsAPauseIcon() {
        adapter.setPlaybackState("a", false)

        assertEquals(R.drawable.ic_pause, row(0).icon())
    }

    @Test
    fun otherTracksShowNoMarker() {
        adapter.setPlaybackState("a", true)

        val row = row(1)

        assertEquals(View.INVISIBLE, row.item.playPauseIcon.visibility)
        assertEquals(View.INVISIBLE, row.item.coverArtOverlay.visibility)
    }

    @Test
    fun nothingIsMarkedWhenNothingIsPlaying() {
        adapter.setPlaybackState(null, false)

        assertEquals(View.INVISIBLE, row(0).item.playPauseIcon.visibility)
    }
}
