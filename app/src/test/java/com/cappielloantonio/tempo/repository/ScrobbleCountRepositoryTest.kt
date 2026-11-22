package com.cappielloantonio.tempo.repository

import com.cappielloantonio.tempo.lastfm.LastFmAccess
import com.cappielloantonio.tempo.lastfm.api.TrackClient
import com.cappielloantonio.tempo.lastfm.models.LastFmTrack
import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse
import com.cappielloantonio.tempo.util.Preferences
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import retrofit2.mock.Calls
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ScrobbleCountRepositoryTest {
    private val lastFm = mock(TrackClient::class.java)
    private val repository = ScrobbleCountRepository(lastFm)

    @Before
    fun setUp() {
        Preferences.setLastFmUser("listener")
        Preferences.setLastFmApiKey("key")
    }

    private fun lastFmRequest() = `when`(lastFm.getTrackInfo(anyString(), anyString(), anyString(), anyString()))

    private fun lastFmCounts(count: String?) {
        lastFmRequest().thenAnswer { Calls.response(LastFmTrackResponse(track = LastFmTrack(userPlayCount = count))) }
    }

    private fun lastFmRefuses() {
        lastFmRequest().thenAnswer {
            Calls.response(Response.error<LastFmTrackResponse>(403, """{"error":10}""".toResponseBody()))
        }
    }

    private fun lastFmIsDown() {
        lastFmRequest().thenAnswer { Calls.failure<LastFmTrackResponse>(IOException("down")) }
    }

    private fun refusedDaysAgo(days: Long) =
        Preferences.setLastFmRejectedAt(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days) - 1000)

    private fun count(artist: String? = "Artist", track: String? = "Track") = repository.getCount(artist, track).value

    @Test
    fun theLastFmCountIsShown() {
        lastFmCounts("12")

        assertEquals(12L, count())
    }

    @Test
    fun lastFmIsAskedAboutTheFirstArtist() {
        lastFmCounts("1")

        for (artist in listOf("First, Second", "First & Second", "First \u2022 Second", "First feat. Second", "First ft Second")) {
            count(artist)
        }

        verify(lastFm, org.mockito.Mockito.times(5)).getTrackInfo("First", "Track", "listener", "key")
    }

    @Test
    fun aMissingOrUnreadableCountShowsNothing() {
        for (value in listOf(null, "", "many")) {
            lastFmCounts(value)
            assertNull(value, count())
        }
    }

    @Test
    fun nothingIsAskedWithoutAnArtistOrTrack() {
        count(artist = null)
        count(artist = " ")
        count(track = null)
        count(track = "")

        verifyNoInteractions(lastFm)
    }

    @Test
    fun lastFmIsNotAskedWithoutAnAccount() {
        Preferences.setLastFmUser("")
        assertNull(count())

        Preferences.setLastFmUser("listener")
        Preferences.setLastFmApiKey("")
        assertNull(count())

        verifyNoInteractions(lastFm)
    }

    @Test
    fun aRefusalShowsNothingAndTurnsLastFmOff() {
        lastFmRefuses()

        assertNull(count())
        assertTrue(LastFmAccess.isRejected())
    }

    @Test
    fun whileOffLastFmIsNotAsked() {
        refusedDaysAgo(0)

        assertNull(count())
        verifyNoInteractions(lastFm)
    }

    @Test
    fun aDayLaterLastFmIsTriedAndComesBack() {
        refusedDaysAgo(1)
        lastFmCounts("7")

        assertEquals(7L, count())
        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun aNetworkFailureDoesNotTurnLastFmOff() {
        lastFmIsDown()

        assertNull(count())
        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun eachTrackGetsItsOwnResult() {
        lastFmCounts("1")
        val first = repository.getCount("Artist", "One")
        lastFmCounts("2")

        val second = repository.getCount("Artist", "Two")

        assertEquals(1L, first.value)
        assertEquals(2L, second.value)
    }
}
