package com.cappielloantonio.tempo.listenbrainz

import android.util.Log
import com.cappielloantonio.tempo.util.ArtistNames
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * ListenBrainz has no per-track count and asks apps not to page through a listening history, so
 * counts come from the user's all-time top recordings, fetched in one request and kept for an hour.
 * Used from the main thread only.
 */
class ListenBrainzCounts(
    private val api: ListenBrainzApi,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var cached: Cached? = null
    private val waiting = mutableMapOf<String, MutableList<(Index) -> Unit>>()

    fun find(user: String, artist: String, track: String, onFound: (Long) -> Unit) {
        withIndex(user) { index -> index.count(artist, track)?.let(onFound) }
    }

    private fun withIndex(user: String, action: (Index) -> Unit) {
        cached?.takeIf { it.user == user && clock() - it.at in 0 until KEEP_MS }?.let { return action(it.index) }

        // The player can ask several times while the first request is still out.
        waiting[user]?.let {
            it.add(action)
            return
        }
        waiting[user] = mutableListOf(action)

        api.topRecordings(user).enqueue(object : Callback<ListenBrainzRecordingsResponse> {
            override fun onResponse(call: Call<ListenBrainzRecordingsResponse>, response: Response<ListenBrainzRecordingsResponse>) {
                val actions = waiting.remove(user).orEmpty()
                // 204 means no statistics yet and 404 no such user: both are answers worth keeping.
                if (!response.isSuccessful && response.code() != 404) {
                    Log.w(TAG, "ListenBrainz answered HTTP ${response.code()}")
                    return
                }
                val index = Index(response.body()?.payload?.recordings.orEmpty())
                cached = Cached(user, clock(), index)
                actions.forEach { it(index) }
            }

            override fun onFailure(call: Call<ListenBrainzRecordingsResponse>, t: Throwable) {
                waiting.remove(user)
                Log.w(TAG, "ListenBrainz request failed", t)
            }
        })
    }

    private class Cached(val user: String, val at: Long, val index: Index)

    private class Index(recordings: List<ListenBrainzRecording>) {
        private val byArtist = mutableMapOf<Pair<String, String>, Long>()
        private val byFirstArtist = mutableMapOf<Pair<String, String>, Long>()

        init {
            for (recording in recordings) {
                val artist = recording.artistName ?: continue
                val track = normalise(recording.trackName ?: continue)
                // One song can be several recordings, so their listens add up.
                byArtist.merge(normalise(artist) to track, recording.listenCount, Long::plus)
                byFirstArtist.merge(normalise(ArtistNames.first(artist)) to track, recording.listenCount, Long::plus)
            }
        }

        // ponytail: matched by name, so "Simon" can pick up a same-titled "Simon & Garfunkel" track.
        // Match on recording MBIDs if the player ever carries them.
        fun count(artist: String, track: String): Long? {
            val title = normalise(track)
            return byArtist[normalise(artist) to title]
                ?: byFirstArtist[normalise(ArtistNames.first(artist)) to title]
        }
    }

    private companion object {
        const val TAG = "ListenBrainzCounts"
        val KEEP_MS = TimeUnit.HOURS.toMillis(1)

        val SINGLE_QUOTES = Regex("[\u2018\u2019]")
        val DOUBLE_QUOTES = Regex("[\u201C\u201D]")
        val DASHES = Regex("[\u2010-\u2015]")
        val SPACES = Regex("\\s+")

        /** MusicBrainz uses typographic punctuation where file tags mostly don't. */
        fun normalise(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace(SINGLE_QUOTES, "'")
            .replace(DOUBLE_QUOTES, "\"")
            .replace(DASHES, "-")
            .replace(SPACES, " ")
            .trim()
            .lowercase(Locale.ROOT)
    }
}
