package com.cappielloantonio.tempo.service

import androidx.media3.common.C
import androidx.media3.common.FlagSet
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaBrowser
import com.cappielloantonio.tempo.interfaces.MediaIndexCallback
import com.cappielloantonio.tempo.model.Queue
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.AppTestEnv.db
import com.cappielloantonio.tempo.testutil.AppTestEnv.settle
import com.cappielloantonio.tempo.testutil.thenAnswerOk
import com.cappielloantonio.tempo.util.MappingUtil
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel
import com.google.common.util.concurrent.Futures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import java.lang.ref.WeakReference
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class MediaManagerTest {
    private val browser = mock(MediaBrowser::class.java)
    private val future = Futures.immediateFuture(browser)

    private fun song(id: String) = Child(id, title = id)

    private fun songs(vararg ids: String) = ids.map(::song)

    private fun setStatic(field: String, value: Any?) {
        MediaManager::class.java.getDeclaredField(field).apply { isAccessible = true }.set(null, value)
    }

    @Before
    fun setUp() {
        AppTestEnv.install()
        AppTestEnv.setOnline()
        setStatic("lastPlayNextInsertedIndex", -1)
        setStatic("attachedBrowserRef", WeakReference<MediaBrowser>(null))
    }

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun savedQueue(vararg ids: String) {
        db.queueDao().insertAll(ids.mapIndexed { i, id -> Queue(id, trackOrder = i) })
    }

    private fun queueIds(): List<String> = db.queueDao().getAllSimple().map { it.id }

    private fun playing(index: Int, count: Int, nextIndex: Int = index + 1) {
        `when`(browser.currentMediaItemIndex).thenReturn(index)
        `when`(browser.mediaItemCount).thenReturn(count)
        `when`(browser.nextMediaItemIndex).thenReturn(nextIndex)
    }

    private fun addedItemIds(): List<String> {
        @Suppress("UNCHECKED_CAST")
        val items = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MediaItem>>
        verify(browser).addMediaItems(items.capture())
        return items.value.map { it.mediaId }
    }

    private fun playNext(behavior: String) = AppTestEnv.setPreference("play_next_behavior", behavior)

    // enqueue

    @Test
    fun addToQueueGoesAtTheEnd() {
        savedQueue("a", "b")
        playing(0, 2)

        MediaManager.enqueue(future, songs("c", "d"), false)

        assertEquals(listOf("c", "d"), addedItemIds())
        assertEquals(listOf("a", "b", "c", "d"), queueIds())
    }

    @Test
    fun addOneToQueueGoesAtTheEnd() {
        savedQueue("a", "b")
        playing(0, 2)

        MediaManager.enqueue(future, song("c"), false)

        verify(browser).addMediaItem(any(MediaItem::class.java))
        assertEquals(listOf("a", "b", "c"), queueIds())
    }

    @Test
    fun playNextGoesStraightAfterTheCurrentTrack() {
        savedQueue("a", "b", "c")
        playing(0, 3)
        playNext(Preferences.PLAY_NEXT_BEHAVIOR_TOP)

        MediaManager.enqueue(future, songs("x", "y"), true)

        verify(browser).addMediaItems(eq(1), anyList())
        assertEquals(listOf("a", "x", "y", "b", "c"), queueIds())
    }

    @Test
    fun playNextOnTopGoesAheadOfEarlierPlayNexts() {
        savedQueue("a", "b")
        playing(0, 2)
        playNext(Preferences.PLAY_NEXT_BEHAVIOR_TOP)

        MediaManager.enqueue(future, song("x"), true)
        playing(0, 3)
        MediaManager.enqueue(future, song("y"), true)

        verify(browser, times(2)).addMediaItem(eq(1), any(MediaItem::class.java))
        assertEquals(listOf("a", "y", "x", "b"), queueIds())
    }

    @Test
    fun sequentialPlayNextGoesBehindEarlierPlayNexts() {
        savedQueue("a", "b")
        playing(0, 2)
        playNext(Preferences.PLAY_NEXT_BEHAVIOR_SEQUENTIAL)

        MediaManager.enqueue(future, songs("x", "y"), true)
        playing(0, 4)
        MediaManager.enqueue(future, song("z"), true)

        verify(browser).addMediaItems(eq(1), anyList())
        verify(browser).addMediaItem(eq(3), any(MediaItem::class.java))
        assertEquals(listOf("a", "x", "y", "z", "b"), queueIds())
    }

    @Test
    fun sequentialPlayNextStaysInsideAShorterQueue() {
        savedQueue("a", "b")
        setStatic("lastPlayNextInsertedIndex", 9)
        playing(0, 2)
        playNext(Preferences.PLAY_NEXT_BEHAVIOR_SEQUENTIAL)

        MediaManager.enqueue(future, song("x"), true)

        verify(browser).addMediaItem(eq(2), any(MediaItem::class.java))
    }

    @Test
    fun playNextAtTheEndOfTheQueueAppends() {
        savedQueue("a")
        playing(0, 1, C.INDEX_UNSET)

        MediaManager.enqueue(future, songs("x"), true)

        assertEquals(listOf("x"), addedItemIds())
        assertEquals(listOf("a", "x"), queueIds())
    }

    @Test
    fun aTrackChangeRestartsTheSequentialPlayNextPosition() {
        savedQueue("a", "b", "c")
        playing(0, 3)
        playNext(Preferences.PLAY_NEXT_BEHAVIOR_SEQUENTIAL)
        val listener = observePlayback(mock(PlaybackViewModel::class.java))

        MediaManager.enqueue(future, song("x"), true)
        listener.onEvents(browser, events(Player.EVENT_MEDIA_ITEM_TRANSITION))
        playing(2, 4)
        MediaManager.enqueue(future, song("y"), true)

        verify(browser).addMediaItem(eq(3), any(MediaItem::class.java))
    }

    @Test
    fun aTrackAlreadyInTheQueueIsSavedLikeThePlayerHasIt() {
        savedQueue("a", "b")
        playing(0, 2)

        MediaManager.enqueue(future, songs("a"), false)

        assertEquals(listOf("a"), addedItemIds())
        assertEquals(listOf("a", "b", "a"), queueIds())
    }

    @Test
    fun addingAfterARepeatedTrackStillReachesThePlayer() {
        savedQueue("a", "b")
        playing(0, 2)
        MediaManager.enqueue(future, songs("a"), false)
        playing(0, 3)

        MediaManager.enqueue(future, songs("c"), false)

        verify(browser, times(2)).addMediaItems(anyList())
    }

    @Test
    fun aSavedQueueBehindThePlayerStillTakesNewTracks() {
        savedQueue("a")
        playing(0, 5)

        MediaManager.enqueue(future, songs("x"), false)

        assertEquals(listOf("x"), addedItemIds())
        assertEquals(listOf("a", "x"), queueIds())
    }

    @Test
    fun aSavedQueueBehindThePlayerStillTakesANewTrack() {
        savedQueue("a")
        playing(0, 5)

        MediaManager.enqueue(future, song("x"), false)

        verify(browser).addMediaItem(any(MediaItem::class.java))
        assertEquals(listOf("a", "x"), queueIds())
    }

    // remove / removeRange / swap / shuffle

    @Test
    fun removingAnotherTrackUpdatesThePlayerAndTheSavedQueue() {
        playing(0, 3)
        val media = songs("a", "b", "c").toMutableList()

        MediaManager.remove(future, media, 1)

        verify(browser).removeMediaItem(1)
        assertEquals(listOf("a", "c"), queueIds())
    }

    @Test
    fun theCurrentTrackIsNotRemoved() {
        savedQueue("a", "b")
        playing(0, 2)

        MediaManager.remove(future, songs("a", "b").toMutableList(), 0)

        verify(browser, never()).removeMediaItem(anyInt())
        assertEquals(listOf("a", "b"), queueIds())
    }

    @Test
    fun theLastTrackIsNotRemoved() {
        savedQueue("a")
        playing(1, 1)

        MediaManager.remove(future, songs("a").toMutableList(), 0)

        verify(browser, never()).removeMediaItem(anyInt())
        assertEquals(listOf("a"), queueIds())
    }

    @Test
    fun removingARangeUpdatesThePlayerAndTheSavedQueue() {
        val media = songs("a", "b", "c", "d").toMutableList()

        MediaManager.removeRange(future, media, 1, 3)

        verify(browser).removeMediaItems(1, 3)
        assertEquals(listOf("a", "d"), queueIds())
    }

    @Test
    fun removingARangeKeepsTheSameTrackElsewhereInTheQueue() {
        val repeated = song("r")
        val media = mutableListOf(repeated, song("a"), repeated)

        MediaManager.removeRange(future, media, 2, 3)

        verify(browser).removeMediaItems(2, 3)
        assertEquals(listOf("r", "a"), queueIds())
    }

    @Test
    fun swappingMovesTheItemAndSavesTheNewOrder() {
        MediaManager.swap(future, songs("b", "a", "c"), 1, 0)

        verify(browser).moveMediaItem(1, 0)
        assertEquals(listOf("b", "a", "c"), queueIds())
    }

    @Test
    fun shufflingReplacesOnlyTheGivenRange() {
        MediaManager.shuffle(future, songs("a", "c", "b"), 1, 2)

        verify(browser).removeMediaItems(1, 3)
        assertEquals(listOf("c", "b"), addedItemIds())
        assertEquals(listOf("a", "c", "b"), queueIds())
    }

    // Starting playback

    @Test
    fun startingAQueuePlaysFromTheChosenTrackOnceTheTimelineArrives() {
        `when`(browser.mediaItemCount).thenReturn(3)
        MediaManager.justStarted.set(false)

        MediaManager.startQueue(future, songs("a", "b", "c"), 1)
        settle()

        verify(browser).setMediaItems(anyList(), eq(1), eq(0L))
        verify(browser).prepare()
        assertTrue(MediaManager.justStarted.get())

        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(browser).addListener(listener.capture())
        listener.value.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)

        verify(browser).seekTo(1, 0)
        verify(browser).play()
        verify(browser).removeListener(listener.value)
    }

    @Test
    fun anOutOfRangeStartIndexWaitsForALaterTimeline() {
        `when`(browser.mediaItemCount).thenReturn(1)

        MediaManager.startQueue(future, songs("a", "b", "c"), 2)
        settle()

        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(browser).addListener(listener.capture())
        listener.value.onTimelineChanged(Timeline.EMPTY, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)

        verify(browser, never()).play()
        verify(browser, never()).removeListener(listener.value)
    }

    @Test
    fun startingAQueueReplacesTheSavedQueue() {
        savedQueue("old")
        `when`(browser.mediaItemCount).thenReturn(2)

        MediaManager.startQueue(future, songs("a", "b"), 0)

        val deadline = System.currentTimeMillis() + 5000
        while (queueIds() != listOf("a", "b") && System.currentTimeMillis() < deadline) Thread.sleep(5)
        assertEquals(listOf("a", "b"), queueIds())
    }

    @Test
    fun startingOneSongPlaysItAndSavesIt() {
        savedQueue("old")

        MediaManager.startQueue(future, song("a"))

        verify(browser).setMediaItem(any(MediaItem::class.java))
        verify(browser).play()
        assertEquals(listOf("a"), queueIds())
    }

    @Test
    fun playingADownloadClearsTheSavedQueue() {
        savedQueue("old")

        MediaManager.playDownloadedMediaItem(future, MediaItem.Builder().setMediaId("d").build())
        settle()

        verify(browser).play()
        assertTrue(queueIds().isEmpty())
    }

    @Test
    fun aRadioOrPodcastPlaysWithoutTouchingTheSavedQueue() {
        savedQueue("a")

        MediaManager.startRadio(future, InternetRadioStation("r", "Radio", "http://radio/"))
        MediaManager.startPodcast(future, PodcastEpisode(id = "e", coverArtId = "c", streamId = "st"))

        verify(browser, times(2)).setMediaItem(any(MediaItem::class.java))
        verify(browser, times(2)).play()
        assertEquals(listOf("a"), queueIds())
    }

    @Test
    fun aNullBrowserIsIgnored() {
        MediaManager.enqueue(null, songs("a"), false)
        MediaManager.startQueue(null, song("a"))
        MediaManager.reset(null)
        MediaManager.hide(null)
        MediaManager.check(null)

        assertTrue(queueIds().isEmpty())
    }

    // reset / hide / check / init

    @Test
    fun resetStopsAndForgetsTheQueue() {
        savedQueue("a")
        `when`(browser.isPlaying).thenReturn(true)

        MediaManager.reset(future)
        settle()

        verify(browser).pause()
        verify(browser).stop()
        verify(browser).clearMediaItems()
        assertTrue(queueIds().isEmpty())
    }

    @Test
    fun hidePausesOnlyWhenPlaying() {
        MediaManager.hide(future)
        verify(browser, never()).pause()

        `when`(browser.isPlaying).thenReturn(true)
        MediaManager.hide(future)
        verify(browser).pause()
    }

    @Test
    fun checkRestoresTheSavedQueueIntoAnEmptyPlayer() {
        savedQueue("a", "b")
        `when`(browser.mediaItemCount).thenReturn(0)

        MediaManager.check(future)

        verify(browser).setMediaItems(anyList())
        verify(browser).prepare()
    }

    @Test
    fun checkLeavesAPlayerThatHasItems() {
        savedQueue("a")
        `when`(browser.mediaItemCount).thenReturn(1)

        MediaManager.check(future)

        verify(browser, never()).setMediaItems(anyList())
    }

    @Test
    fun checkDoesNothingWithoutASavedQueue() {
        `when`(browser.mediaItemCount).thenReturn(0)

        MediaManager.check(future)

        verify(browser, never()).setMediaItems(anyList())
    }

    @Test
    fun initResumesWhereTheQueueWasLeft() {
        db.queueDao().insertAll(
            listOf(Queue("a", trackOrder = 0), Queue("b", trackOrder = 1, lastPlay = 500, playingChanged = 7000)),
        )

        MediaManager.init(future, songs("a", "b"))

        verify(browser).clearMediaItems()
        verify(browser).seekTo(1, 7000)
        verify(browser).prepare()
    }

    @Test
    fun getCurrentIndexAsksThePlayer() {
        `when`(browser.currentMediaItemIndex).thenReturn(4)
        var index = -1

        MediaManager.getCurrentIndex(future, object : MediaIndexCallback {
            override fun onRecovery(index_: Int) {
                index = index_
            }
        })

        assertEquals(4, index)
    }

    // Playback observer

    private fun observePlayback(viewModel: PlaybackViewModel): Player.Listener {
        MediaManager.registerPlaybackObserver(future, viewModel)
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(browser).addListener(listener.capture())
        return listener.value
    }

    private fun events(vararg events: Int) =
        Player.Events(FlagSet.Builder().addAll(*events).build())

    @Test
    fun theObserverReportsTheCurrentStateStraightAway() {
        `when`(browser.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId("a").build())
        `when`(browser.playbackState).thenReturn(Player.STATE_READY)
        `when`(browser.playWhenReady).thenReturn(true)
        val viewModel = mock(PlaybackViewModel::class.java)

        MediaManager.registerPlaybackObserver(future, viewModel)

        verify(viewModel).update("a", true)
    }

    @Test
    fun bufferingDoesNotCountAsPlaying() {
        `when`(browser.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId("a").build())
        `when`(browser.playbackState).thenReturn(Player.STATE_BUFFERING)
        `when`(browser.playWhenReady).thenReturn(true)
        val viewModel = mock(PlaybackViewModel::class.java)

        MediaManager.registerPlaybackObserver(future, viewModel)

        verify(viewModel).update("a", false)
    }

    @Test
    fun theObserverFollowsPlayerEvents() {
        val viewModel = mock(PlaybackViewModel::class.java)
        val listener = observePlayback(viewModel)
        `when`(browser.currentMediaItem).thenReturn(MediaItem.Builder().setMediaId("b").build())

        listener.onEvents(browser, events(Player.EVENT_MEDIA_ITEM_TRANSITION))

        verify(viewModel).update("b", false)
    }

    @Test
    fun aSecondRegistrationDoesNotAddASecondListener() {
        val viewModel = mock(PlaybackViewModel::class.java)

        MediaManager.registerPlaybackObserver(future, viewModel)
        MediaManager.registerPlaybackObserver(future, viewModel)

        verify(browser, times(1)).addListener(any())
        verify(viewModel, times(2)).update(null, false)
    }

    @Test
    fun aReleasedBrowserCanBeObservedAgain() {
        val viewModel = mock(PlaybackViewModel::class.java)

        MediaManager.registerPlaybackObserver(future, viewModel)
        MediaManager.onBrowserReleased(browser)
        MediaManager.registerPlaybackObserver(future, viewModel)

        verify(browser, times(2)).addListener(any())
    }

    // Scrobbling and events

    private fun mediaItem(id: String) = MappingUtil.mapMediaItem(song(id))

    @Test
    fun scrobblingSendsTheSong() {
        val annotation = AppTestEnv.subsonic.mediaAnnotationClient
        `when`(annotation.scrobble(anyString(), anyBoolean(), any())).thenAnswerOk()

        MediaManager.scrobble(mediaItem("a"), true, 100L)
        settle()

        verify(annotation).scrobble("a", true, 100L)
    }

    @Test
    fun scrobblingCanBeTurnedOff() {
        AppTestEnv.setPreference("scrobbling", false)
        val annotation = AppTestEnv.subsonic.mediaAnnotationClient

        MediaManager.scrobble(mediaItem("a"), true)

        verify(annotation, never()).scrobble(anyString(), anyBoolean(), any())
    }

    @Test
    fun scrobblingNothingDoesNothing() {
        MediaManager.scrobble(null, true)

        verify(AppTestEnv.subsonic.mediaAnnotationClient, never()).scrobble(anyString(), anyBoolean(), any())
    }

    @Test
    fun playCountIncrementsAddUpUntilReset() {
        val before = MediaManager.getScrobbleVersion()

        MediaManager.postScrobbleEvent("counted")
        MediaManager.postScrobbleEvent("counted")

        assertEquals(2, MediaManager.getPlayCountIncrement("counted"))
        assertEquals(before + 2, MediaManager.getScrobbleVersion())

        MediaManager.resetPlayCountIncrement("counted")
        assertEquals(0, MediaManager.getPlayCountIncrement("counted"))
    }

    @Test
    fun aNullSongHasNoPlayCount() {
        assertEquals(0, MediaManager.getPlayCountIncrement(null))
        MediaManager.resetPlayCountIncrement(null)
    }

    @Test
    fun favoriteAndRatingEventsCarryTheirValues() {
        val starred = Date(1000)

        MediaManager.postFavoriteEvent("a", starred)
        MediaManager.postRatingEvent("a", 4)
        settle()

        assertEquals(listOf("a", starred), MediaManager.getFavoriteEvent().value!!.toList())
        assertEquals(listOf<Any>("a", 4), MediaManager.getRatingEvent().value!!.toList())
    }

    // continuousPlay

    @Test
    fun continuousPlayQueuesASimilarMix() {
        `when`(AppTestEnv.subsonic.browsingClient.getSimilarSongs("a", 25))
            .thenAnswerOk("""{"similarSongs":{"song":[{"id":"x"}]}}""")
        savedQueue("a")
        playing(0, 1, C.INDEX_UNSET)

        MediaManager.continuousPlay(mediaItem("a"), future)
        settle()

        assertEquals(listOf("x"), addedItemIds())
    }

    @Test
    fun continuousPlayCanBeTurnedOff() {
        AppTestEnv.setPreference("continuous_play", false)

        MediaManager.continuousPlay(mediaItem("a"), future)

        verify(AppTestEnv.subsonic.browsingClient, never()).getSimilarSongs(anyString(), anyInt())
    }

    @Test
    fun continuousPlayWaitsBetweenMixes() {
        Preferences.setLastInstantMix()

        MediaManager.continuousPlay(mediaItem("a"), future)

        verify(AppTestEnv.subsonic.browsingClient, never()).getSimilarSongs(anyString(), anyInt())
    }

    @Test
    fun continuousPlayIgnoresNothing() {
        MediaManager.continuousPlay(null, future)

        assertFalse(db.queueDao().count() > 0)
    }
}
