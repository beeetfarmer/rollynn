package com.cappielloantonio.tempo.lastfm

import com.cappielloantonio.tempo.lastfm.models.LastFmTrack
import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse
import com.cappielloantonio.tempo.util.Preferences
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LastFmAccessTest {
    private val now = 1_800_000_000_000L
    private val day = TimeUnit.DAYS.toMillis(1)

    @Before
    fun setUp() = Preferences.setLastFmApiKey("key")

    private fun failure(status: Int, body: String): Response<LastFmTrackResponse> =
        Response.error(status, body.toResponseBody())

    private fun error(code: Int) = """{"message":"message","error":$code}"""

    private fun record(response: Response<LastFmTrackResponse>) = LastFmAccess.record("key", response, now)

    private fun found() = Response.success(LastFmTrackResponse(track = LastFmTrack(userPlayCount = "3")))

    @Test
    fun lastFmIsOnUntilAKeyIsRefused() {
        assertFalse(LastFmAccess.isRejected())
        assertTrue(LastFmAccess.mayRequest(now))
    }

    @Test
    fun theAnswerLastFmGivesForAnInvalidKeyTurnsItOff() {
        record(failure(403, """{"message":"Invalid API key - You must be granted a valid key by last.fm","error":10}"""))

        assertTrue(LastFmAccess.isRejected())
    }

    @Test
    fun everyKeyErrorTurnsItOffWhateverTheStatus() {
        for (code in listOf(4, 10, 26)) {
            for (status in listOf(400, 403, 500)) {
                Preferences.setLastFmRejectedAt(0)
                record(failure(status, error(code)))
                assertTrue("$status/$code", LastFmAccess.isRejected())
            }
        }
    }

    @Test
    fun aKeyErrorSentWithHttp200TurnsItOff() {
        record(Response.success(LastFmTrackResponse(error = 26)))

        assertTrue(LastFmAccess.isRejected())
    }

    @Test
    fun anUnknownCodeOnAForbiddenAnswerTurnsItOff() {
        for (status in listOf(401, 403)) {
            Preferences.setLastFmRejectedAt(0)
            record(failure(status, error(30)))
            assertTrue("$status", LastFmAccess.isRejected())
        }
    }

    @Test
    fun paymentRequiredTurnsItOff() {
        record(failure(402, ""))

        assertTrue(LastFmAccess.isRejected())
    }

    @Test
    fun errorsThatAreNotAboutTheKeyLeaveItOn() {
        for (code in listOf(6, 8, 11, 16, 17, 29)) {
            for (status in listOf(400, 403, 500)) {
                record(failure(status, error(code)))
                assertFalse("$status/$code", LastFmAccess.isRejected())
            }
        }
    }

    @Test
    fun aBlockThatIsNotFromLastFmLeavesItOn() {
        for (body in listOf("", "<html>Forbidden</html>", "{}", """{"error":"blocked"}""")) {
            record(failure(403, body))
            assertFalse(body, LastFmAccess.isRejected())
        }
    }

    @Test
    fun anOutageLeavesItOn() {
        record(failure(503, "unavailable"))

        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun aRefusedKeyWaitsADayBeforeTheNextTry() {
        record(failure(403, error(10)))

        assertFalse(LastFmAccess.mayRequest(now))
        assertFalse(LastFmAccess.mayRequest(now + day - 1))
        assertTrue(LastFmAccess.mayRequest(now + day))
    }

    @Test
    fun aClockSetBackDoesNotKeepItOff() {
        record(failure(403, error(10)))

        assertTrue(LastFmAccess.mayRequest(now - 1))
    }

    @Test
    fun aSecondRefusalStartsTheDayAgain() {
        record(failure(403, error(10)))

        LastFmAccess.record("key", failure(403, error(10)), now + day)

        assertEquals(now + day, Preferences.getLastFmRejectedAt())
    }

    @Test
    fun anAcceptedKeyTurnsItBackOn() {
        record(failure(403, error(10)))

        record(found())

        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun anUnknownTrackStillShowsTheKeyWorks() {
        record(failure(403, error(10)))

        record(Response.success(LastFmTrackResponse(error = 6)))

        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun anOutageDoesNotTurnItBackOn() {
        record(failure(403, error(10)))

        record(failure(503, ""))

        assertTrue(LastFmAccess.isRejected())
    }

    @Test
    fun enteringAKeyTurnsItBackOn() {
        record(failure(403, error(10)))

        Preferences.setLastFmApiKey("key")

        assertFalse(LastFmAccess.isRejected())
    }

    @Test
    fun anAnswerForAReplacedKeyIsIgnored() {
        Preferences.setLastFmApiKey("new key")

        record(failure(403, error(10)))

        assertFalse(LastFmAccess.isRejected())
    }
}
