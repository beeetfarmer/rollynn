package com.cappielloantonio.tempo.lastfm

import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse
import com.cappielloantonio.tempo.util.Preferences
import com.google.gson.Gson
import retrofit2.Response
import java.util.concurrent.TimeUnit

/**
 * From 1 December 2026 Last.fm only accepts API keys whose owner has Pro, and it accepts a key
 * again once they subscribe. So this follows what the API answers rather than the date: a refused
 * key switches Last.fm off, and it gets one more try a day.
 */
object LastFmAccess {
    private val RETRY_AFTER_MS = TimeUnit.DAYS.toMillis(1)

    private const val TRACK_NOT_FOUND = 6

    // Authentication failed, invalid key, suspended key.
    private val KEY_ERRORS = setOf(4, 10, 26)

    // Not found, outages, a private profile and rate limiting say nothing about the key.
    private val OTHER_ERRORS = setOf(TRACK_NOT_FOUND, 8, 11, 16, 17, 29)

    @JvmStatic
    fun isRejected(): Boolean = Preferences.getLastFmRejectedAt() != 0L

    fun mayRequest(now: Long = System.currentTimeMillis()): Boolean {
        val rejectedAt = Preferences.getLastFmRejectedAt()
        // A time in the future means the clock went back, which shouldn't keep Last.fm off.
        return rejectedAt == 0L || now - rejectedAt !in 0 until RETRY_AFTER_MS
    }

    fun record(apiKey: String, response: Response<LastFmTrackResponse>, now: Long = System.currentTimeMillis()) {
        // The key may have been replaced while the request was out.
        if (apiKey != Preferences.getLastFmApiKey()) return

        val body = if (response.isSuccessful) response.body() else parseError(response)
        val error = body?.error
        when {
            isRefusal(response.code(), error) -> Preferences.setLastFmRejectedAt(now)
            body?.track != null || error == TRACK_NOT_FOUND -> Preferences.setLastFmRejectedAt(0)
        }
    }

    private fun isRefusal(status: Int, error: Int?): Boolean {
        if (error in KEY_ERRORS || status == 402) return true
        // Last.fm hasn't said which code a key without Pro gets. A 403 with no code is more likely a proxy.
        return (status == 401 || status == 403) && error != null && error !in OTHER_ERRORS
    }

    private fun parseError(response: Response<LastFmTrackResponse>): LastFmTrackResponse? =
        runCatching { Gson().fromJson(response.errorBody()?.string(), LastFmTrackResponse::class.java) }.getOrNull()
}
