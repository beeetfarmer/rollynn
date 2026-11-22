package com.cappielloantonio.tempo.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cappielloantonio.tempo.lastfm.LastFm
import com.cappielloantonio.tempo.lastfm.LastFmAccess
import com.cappielloantonio.tempo.lastfm.api.TrackClient
import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse
import com.cappielloantonio.tempo.util.Preferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ScrobbleCountRepository @JvmOverloads constructor(
    private val lastFm: TrackClient = LastFm.trackClient,
) {
    /** A new LiveData for each track, so a late answer can't land on the track that followed it. */
    fun getCount(artist: String?, track: String?): LiveData<Long?> {
        val result = MutableLiveData<Long?>(null)
        if (artist.isNullOrBlank() || track.isNullOrBlank()) return result

        fetchLastFm(artist, track, result)
        return result
    }

    private fun fetchLastFm(artist: String, track: String, result: MutableLiveData<Long?>) {
        val user = Preferences.getLastFmUser()
        val key = Preferences.getLastFmApiKey()
        if (user.isNullOrEmpty() || key.isNullOrEmpty() || !LastFmAccess.mayRequest()) return

        val firstArtist = artist.split(ARTIST_SEPARATOR)[0].trim()
        if (firstArtist.isEmpty()) return

        lastFm.getTrackInfo(firstArtist, track, user, key).enqueue(object : Callback<LastFmTrackResponse> {
            override fun onResponse(call: Call<LastFmTrackResponse>, response: Response<LastFmTrackResponse>) {
                LastFmAccess.record(key, response)
                response.body()?.track?.userPlayCount?.toLongOrNull()?.let { result.value = it }
            }

            override fun onFailure(call: Call<LastFmTrackResponse>, t: Throwable) {
                Log.e(TAG, "Last.fm API error", t)
            }
        })
    }

    private companion object {
        const val TAG = "ScrobbleCountRepository"
        val ARTIST_SEPARATOR = Regex("""\s*[,/;&\u2022]\s*|\s+feat\.?\s+|\s+ft\.?\s+""")
    }
}
