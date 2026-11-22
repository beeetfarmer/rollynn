package com.cappielloantonio.tempo.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.cappielloantonio.tempo.lastfm.LastFm
import com.cappielloantonio.tempo.lastfm.LastFmAccess
import com.cappielloantonio.tempo.lastfm.api.TrackClient
import com.cappielloantonio.tempo.lastfm.models.LastFmTrackResponse
import com.cappielloantonio.tempo.listenbrainz.ListenBrainz
import com.cappielloantonio.tempo.listenbrainz.ListenBrainzCounts
import com.cappielloantonio.tempo.util.ArtistNames
import com.cappielloantonio.tempo.util.Constants
import com.cappielloantonio.tempo.util.Preferences
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class ScrobbleCountRepository @JvmOverloads constructor(
    private val lastFm: () -> TrackClient = { LastFm.trackClient },
    private val listenBrainz: () -> ListenBrainzCounts = { ListenBrainz.counts },
) {
    /** A new LiveData for each track, so a late answer can't land on the track that followed it. */
    fun getCount(artist: String?, track: String?): LiveData<Long?> {
        val result = MutableLiveData<Long?>(null)
        if (artist.isNullOrBlank() || track.isNullOrBlank()) return result

        if (Preferences.getScrobbleSource() == Constants.SCROBBLE_SOURCE_LISTENBRAINZ) {
            fetchListenBrainz(artist, track, result)
        } else {
            fetchLastFm(artist, track, result)
        }
        return result
    }

    private fun fetchLastFm(artist: String, track: String, result: MutableLiveData<Long?>) {
        val user = Preferences.getLastFmUser()
        val key = Preferences.getLastFmApiKey()
        if (user.isNullOrEmpty() || key.isNullOrEmpty() || !LastFmAccess.mayRequest()) {
            fetchListenBrainz(artist, track, result)
            return
        }

        val firstArtist = ArtistNames.first(artist)
        if (firstArtist.isEmpty()) return

        lastFm().getTrackInfo(firstArtist, track, user, key).enqueue(object : Callback<LastFmTrackResponse> {
            override fun onResponse(call: Call<LastFmTrackResponse>, response: Response<LastFmTrackResponse>) {
                LastFmAccess.record(key, response)
                if (LastFmAccess.isRejected()) {
                    fetchListenBrainz(artist, track, result)
                } else {
                    response.body()?.track?.userPlayCount?.toLongOrNull()?.let { result.value = it }
                }
            }

            override fun onFailure(call: Call<LastFmTrackResponse>, t: Throwable) {
                Log.e(TAG, "Last.fm API error", t)
                // Only when this was the daily retry of a refused key; a blip doesn't switch source.
                if (LastFmAccess.isRejected()) fetchListenBrainz(artist, track, result)
            }
        })
    }

    private fun fetchListenBrainz(artist: String, track: String, result: MutableLiveData<Long?>) {
        val user = Preferences.getListenBrainzUser() ?: return
        listenBrainz().find(user, artist, track) { result.value = it }
    }

    companion object {
        private const val TAG = "ScrobbleCountRepository"

        /** Whether [source] is Last.fm but ListenBrainz is standing in for it. */
        @JvmStatic
        fun isFallingBack(source: String): Boolean {
            if (source != Constants.SCROBBLE_SOURCE_LASTFM || Preferences.getListenBrainzUser() == null) return false
            return Preferences.getLastFmUser().isNullOrEmpty() ||
                Preferences.getLastFmApiKey().isNullOrEmpty() ||
                LastFmAccess.isRejected()
        }
    }
}
