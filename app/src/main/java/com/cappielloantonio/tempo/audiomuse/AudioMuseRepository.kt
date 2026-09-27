package com.cappielloantonio.tempo.audiomuse

import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.subsonic.base.ApiResponse
import com.cappielloantonio.tempo.subsonic.models.ArtistID3
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.concurrent.TimeUnit

object AudioMuseRepository {
    // Short, so an unreachable server falls back quickly instead of hanging a page.
    private const val QUICK_TIMEOUT_SECONDS = 5L

    fun interface AvailabilityCallback {
        fun onResult(available: Boolean)
    }

    fun interface ArtistsCallback {
        /** Null when AudioMuse-AI could not be asked (unreachable, error); callers should fall back. */
        fun onResult(artists: List<ArtistID3>?)
    }

    /** True when the server answers with success, i.e. it is up and accepts our token. */
    @JvmStatic
    fun checkAvailable(callback: AvailabilityCallback) {
        val api = AudioMuseClient.getApi() ?: return callback.onResult(false)
        api.configDefaults().quick().enqueue(object : Callback<okhttp3.ResponseBody> {
            override fun onResponse(call: Call<okhttp3.ResponseBody>, response: Response<okhttp3.ResponseBody>) {
                response.body()?.close()
                callback.onResult(response.isSuccessful)
            }

            override fun onFailure(call: Call<okhttp3.ResponseBody>, t: Throwable) = callback.onResult(false)
        })
    }

    /**
     * Sonically similar artists for a music-server artist id. AudioMuse-AI returns the
     * server's artist ids but leaves some null (often its closest matches), so those are
     * looked up on the server by exact name; any still unresolved are dropped, as they
     * could not be opened. Order follows AudioMuse-AI's ranking.
     */
    @JvmStatic
    fun similarArtists(artistId: String, count: Int, callback: ArtistsCallback) {
        val api = AudioMuseClient.getApi() ?: return callback.onResult(null)
        api.similarArtists(artistId, count).quick().enqueue(object : Callback<List<AudioMuseSimilarArtist>> {
            override fun onResponse(call: Call<List<AudioMuseSimilarArtist>>, response: Response<List<AudioMuseSimilarArtist>>) {
                val similar = response.body()
                if (!response.isSuccessful || similar == null) return callback.onResult(null)
                resolve(similar.filter { it.artistId != artistId && !it.artist.isNullOrBlank() }, callback)
            }

            override fun onFailure(call: Call<List<AudioMuseSimilarArtist>>, t: Throwable) = callback.onResult(null)
        })
    }

    private fun resolve(similar: List<AudioMuseSimilarArtist>, callback: ArtistsCallback) {
        val slots = arrayOfNulls<ArtistID3>(similar.size)
        var pending = similar.size
        if (pending == 0) return callback.onResult(emptyList())

        fun done() {
            if (--pending == 0) callback.onResult(slots.filterNotNull().distinctBy { it.id })
        }

        similar.forEachIndexed { index, match ->
            val id = match.artistId
            if (id != null) {
                slots[index] = ArtistID3(id = id, name = match.artist, coverArtId = id)
                done()
                return@forEachIndexed
            }
            App.getSubsonicClientInstance(false).searchingClient
                .search3(match.artist, 0, 0, 5)
                .enqueue(object : Callback<ApiResponse> {
                    override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                        val found = response.body()?.subsonicResponse?.searchResult3?.artists
                            ?.firstOrNull { it.name.equals(match.artist, ignoreCase = true) }
                        if (found?.id != null) {
                            slots[index] = ArtistID3(id = found.id, name = found.name,
                                coverArtId = found.coverArtId ?: found.id)
                        }
                        done()
                    }

                    override fun onFailure(call: Call<ApiResponse>, t: Throwable) = done()
                })
        }
    }

    private fun <T> Call<T>.quick(): Call<T> = apply { timeout().timeout(QUICK_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
}
