package com.cappielloantonio.tempo.listenbrainz

import com.google.gson.Gson
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.mock.Calls
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class ListenBrainzCountsTest {
    private val requested = mutableListOf<String>()
    private var answer: () -> Call<ListenBrainzRecordingsResponse> = { ok() }
    private var now = 1_800_000_000_000L

    private val api = object : ListenBrainzApi {
        override fun topRecordings(user: String): Call<ListenBrainzRecordingsResponse> {
            requested.add(user)
            return answer()
        }
    }
    private val counts = ListenBrainzCounts(api) { now }

    private fun ok(vararg recordings: ListenBrainzRecording): Call<ListenBrainzRecordingsResponse> =
        Calls.response(ListenBrainzRecordingsResponse(ListenBrainzPayload(recordings.toList())))

    private fun httpError(status: Int): Call<ListenBrainzRecordingsResponse> =
        Calls.response(Response.error<ListenBrainzRecordingsResponse>(status, "".toResponseBody()))

    private fun has(artist: String?, track: String?, count: Long) {
        answer = { ok(ListenBrainzRecording(artist, track, count)) }
    }

    private fun find(artist: String = "Artist", track: String = "Track", user: String = "listener"): Long? {
        var found: Long? = null
        counts.find(user, artist, track) { found = it }
        return found
    }

    @Test
    fun aTopTrackHasItsCount() {
        has("Artist", "Track", 25)

        assertEquals(25L, find())
    }

    @Test
    fun aTrackOutsideTheTopHasNoCount() {
        has("Artist", "Another", 25)

        assertNull(find())
    }

    @Test
    fun theSameTitleByAnotherArtistDoesNotMatch() {
        has("Somebody", "Track", 25)

        assertNull(find())
    }

    @Test
    fun caseAndSpacingDoNotMatter() {
        has("The  Artist", "A Track", 4)

        assertEquals(4L, find("the artist", " a  track "))
    }

    @Test
    fun typographicPunctuationMatchesPlainTags() {
        has("Jay\u2010Z", "Baba O\u2019Riley \u201CLive\u201D\u2026", 43)

        assertEquals(43L, find("Jay-Z", "Baba O'Riley \"Live\"..."))
    }

    @Test
    fun composedAndDecomposedAccentsMatch() {
        has("Beyonc\u00E9", "Halo", 9)

        assertEquals(9L, find("Beyonce\u0301", "Halo"))
    }

    @Test
    fun recordingsOfTheSameSongAddUp() {
        answer = { ok(ListenBrainzRecording("Artist", "Track", 10), ListenBrainzRecording("Artist", "Track", 5)) }

        assertEquals(15L, find())
    }

    @Test
    fun creditsWrittenDifferentlyMatchOnTheFirstArtist() {
        has("First feat. Second", "Track", 8)

        assertEquals(8L, find("First \u2022 Second"))
        assertEquals(8L, find("First"))
    }

    @Test
    fun anExactArtistBeatsAFirstArtistMatch() {
        answer = { ok(ListenBrainzRecording("First", "Track", 3), ListenBrainzRecording("First & Second", "Track", 8)) }

        assertEquals(3L, find("First"))
        assertEquals(8L, find("First & Second"))
    }

    @Test
    fun entriesWithoutNamesAreSkipped() {
        answer = {
            ok(ListenBrainzRecording(null, "Track", 1), ListenBrainzRecording("Artist", null, 2), ListenBrainzRecording("Artist", "Track", 3))
        }

        assertEquals(3L, find())
    }

    @Test
    fun anHourOfTracksTakesOneRequest() {
        find(track = "One")
        now += TimeUnit.HOURS.toMillis(1) - 1

        find(track = "Two")

        assertEquals(1, requested.size)
    }

    @Test
    fun afterAnHourItIsFetchedAgain() {
        find()
        now += TimeUnit.HOURS.toMillis(1)

        find()

        assertEquals(2, requested.size)
    }

    @Test
    fun anotherUserGetsTheirOwnCounts() {
        has("Artist", "Track", 1)
        assertEquals(1L, find(user = "one"))
        has("Artist", "Track", 2)

        assertEquals(2L, find(user = "two"))
        assertEquals(listOf("one", "two"), requested)
    }

    @Test
    fun askingWhileARequestIsOutDoesNotSendAnother() {
        lateinit var callback: Callback<ListenBrainzRecordingsResponse>
        val call = object : Call<ListenBrainzRecordingsResponse> by ok() {
            override fun enqueue(callback_: Callback<ListenBrainzRecordingsResponse>) {
                callback = callback_
            }
        }
        answer = { call }
        val found = mutableListOf<Long>()
        repeat(3) { counts.find("listener", "Artist", "Track") { found.add(it) } }

        callback.onResponse(
            call,
            Response.success(ListenBrainzRecordingsResponse(ListenBrainzPayload(listOf(ListenBrainzRecording("Artist", "Track", 6))))),
        )

        assertEquals(1, requested.size)
        assertEquals(listOf(6L, 6L, 6L), found)
    }

    @Test
    fun noStatisticsYetIsRemembered() {
        answer = { Calls.response(Response.success<ListenBrainzRecordingsResponse>(204, null)) }

        assertNull(find())
        assertNull(find())
        assertEquals(1, requested.size)
    }

    @Test
    fun anUnknownUserIsRemembered() {
        answer = { httpError(404) }

        assertNull(find())
        assertNull(find())
        assertEquals(1, requested.size)
    }

    @Test
    fun serverErrorsAndRateLimitsAreRetriedNextTime() {
        for (status in listOf(429, 500, 503)) {
            answer = { httpError(status) }
            assertNull(find())
        }
        has("Artist", "Track", 2)

        assertEquals(2L, find())
    }

    @Test
    fun aNetworkFailureIsRetriedNextTime() {
        answer = { Calls.failure(IOException("down")) }
        assertNull(find())
        has("Artist", "Track", 2)

        assertEquals(2L, find())
    }

    @Test
    fun theRequestAsksForTheAllTimeTopThousand() {
        val url = ListenBrainz.api.topRecordings("a user/name").request().url

        assertEquals("api.listenbrainz.org", url.host)
        assertEquals("https", url.scheme)
        assertEquals("/1/stats/user/a%20user%2Fname/recordings", url.encodedPath)
        assertEquals("all_time", url.queryParameter("range"))
        assertEquals("1000", url.queryParameter("count"))
    }

    @Test
    fun theDocumentedResponseIsRead() {
        val body = Gson().fromJson(
            """{"payload":{"count":1,"range":"all_time","recordings":[{"artist_mbids":[],
                "artist_name":"The Who","listen_count":43,"track_name":"Baba O\u2019Riley"}]}}""",
            ListenBrainzRecordingsResponse::class.java,
        )
        answer = { Calls.response(body) }

        assertEquals(43L, find("The Who", "Baba O'Riley"))
    }
}
