package com.cappielloantonio.tempo.koito

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Resolves an album/artist's play count and last-played from Koito. Everything is best-effort:
 * on a missing match, missing config, or any error the LiveData stays null so callers fall back
 * to the Subsonic server's own stats. Matching prefers the MusicBrainz ID, then the name.
 */
class KoitoRepository {

    fun getAlbumStats(mbid: String?, title: String?, artistName: String?): LiveData<KoitoStats?> {
        val result = MutableLiveData<KoitoStats?>(null)
        val api = KoitoClient.getApi()
        if (api == null || title.isNullOrBlank()) return result

        api.search(title).enqueue(object : Callback<KoitoSearchResponse> {
            override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                val match = pickMatch(response.body()?.albums, mbid, title, artistName)
                if (match == null) {
                    result.postValue(null)
                    return
                }
                api.album(match.id).enqueue(object : Callback<KoitoEntity> {
                    override fun onResponse(call: Call<KoitoEntity>, entity: Response<KoitoEntity>) {
                        val count = entity.body()?.listenCount ?: 0
                        fetchLastPlayed(api, match.id, null) { lastPlayed ->
                            result.postValue(KoitoStats(count, lastPlayed))
                        }
                    }

                    override fun onFailure(call: Call<KoitoEntity>, t: Throwable) {
                        result.postValue(null)
                    }
                })
            }

            override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) {
                result.postValue(null)
            }
        })
        return result
    }

    fun getArtistStats(mbid: String?, name: String?): LiveData<KoitoStats?> {
        val result = MutableLiveData<KoitoStats?>(null)
        val api = KoitoClient.getApi()
        if (api == null || name.isNullOrBlank()) return result

        api.search(name).enqueue(object : Callback<KoitoSearchResponse> {
            override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                val match = pickMatch(response.body()?.artists, mbid, name, null)
                if (match == null) {
                    result.postValue(null)
                    return
                }
                api.artist(match.id).enqueue(object : Callback<KoitoEntity> {
                    override fun onResponse(call: Call<KoitoEntity>, entity: Response<KoitoEntity>) {
                        val count = entity.body()?.listenCount ?: 0
                        fetchLastPlayed(api, null, match.id) { lastPlayed ->
                            result.postValue(KoitoStats(count, lastPlayed))
                        }
                    }

                    override fun onFailure(call: Call<KoitoEntity>, t: Throwable) {
                        result.postValue(null)
                    }
                })
            }

            override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) {
                result.postValue(null)
            }
        })
        return result
    }

    /**
     * Play count for a single track (for the player). Resolves the album first when known so
     * same-titled tracks on different releases are told apart, then matches by title and reads
     * the track entity's real count. Null on any miss so the caller keeps the server's count.
     */
    /** Rewind summary for a calendar period (month 1-12, or 0 for the whole year). Null on error. */
    fun getSummary(year: Int, month: Int): LiveData<KoitoSummary?> {
        val result = MutableLiveData<KoitoSummary?>(null)
        val api = KoitoClient.getApi()
        if (api == null) return result
        api.summary(year, month).enqueue(object : Callback<KoitoSummary> {
            override fun onResponse(call: Call<KoitoSummary>, response: Response<KoitoSummary>) {
                result.postValue(if (response.isSuccessful) response.body() else null)
            }

            override fun onFailure(call: Call<KoitoSummary>, t: Throwable) {
                result.postValue(null)
            }
        })
        return result
    }

    fun getTrackCount(artist: String?, title: String?, albumTitle: String?): LiveData<Long?> {
        val result = MutableLiveData<Long?>(null)
        val api = KoitoClient.getApi()
        if (api == null || title.isNullOrBlank()) return result

        if (!albumTitle.isNullOrBlank()) {
            api.search(albumTitle).enqueue(object : Callback<KoitoSearchResponse> {
                override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                    val album = pickMatch(response.body()?.albums, null, albumTitle, artist)
                    searchAndCountTrack(api, title, album?.id, artist, result)
                }

                override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) {
                    searchAndCountTrack(api, title, null, artist, result)
                }
            })
        } else {
            searchAndCountTrack(api, title, null, artist, result)
        }
        return result
    }

    private fun searchAndCountTrack(api: KoitoApi, title: String, koitoAlbumId: Int?, artist: String?, result: MutableLiveData<Long?>) {
        api.search(title).enqueue(object : Callback<KoitoSearchResponse> {
            override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                val tracks = response.body()?.tracks
                val track = if (koitoAlbumId != null) pickTrack(tracks, koitoAlbumId, title) else pickTrackByArtist(tracks, title, artist)
                if (track == null) {
                    result.postValue(null)
                    return
                }
                api.track(track.id).enqueue(object : Callback<KoitoEntity> {
                    override fun onResponse(call: Call<KoitoEntity>, entity: Response<KoitoEntity>) {
                        result.postValue(entity.body()?.listenCount?.takeIf { it > 0 })
                    }

                    override fun onFailure(call: Call<KoitoEntity>, t: Throwable) {
                        result.postValue(null)
                    }
                })
            }

            override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) {
                result.postValue(null)
            }
        })
    }

    private fun pickTrackByArtist(tracks: List<KoitoEntity>?, title: String, artist: String?): KoitoEntity? {
        if (tracks.isNullOrEmpty()) return null
        val target = title.trim()
        return tracks.firstOrNull {
            it.label().equals(target, ignoreCase = true) &&
                (artist.isNullOrBlank() || it.artists?.any { a -> a.name.equals(artist, ignoreCase = true) } != false)
        }
    }

    /**
     * Resolves per-track play counts for an album's songs, keyed by the Subsonic song id.
     * Songs carry no MBID, so each is matched to a Koito track by title within the resolved
     * album (disambiguating same-titled tracks on other releases). Best-effort: unmatched
     * songs are simply absent from the map, and callers keep the server's count for those.
     */
    fun getAlbumTrackCounts(
        albumMbid: String?,
        albumTitle: String?,
        artistName: String?,
        songs: Map<String, String>,
    ): LiveData<Map<String, Long>> {
        val result = MutableLiveData<Map<String, Long>>()
        val api = KoitoClient.getApi()
        if (api == null || albumTitle.isNullOrBlank() || songs.isEmpty()) return result

        api.search(albumTitle).enqueue(object : Callback<KoitoSearchResponse> {
            override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                val album = pickMatch(response.body()?.albums, albumMbid, albumTitle, artistName)
                if (album == null) {
                    result.postValue(emptyMap())
                    return
                }
                resolveTrackCounts(api, album.id, songs, result)
            }

            override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) {
                result.postValue(emptyMap())
            }
        })
        return result
    }

    private fun resolveTrackCounts(api: KoitoApi, koitoAlbumId: Int, songs: Map<String, String>, result: MutableLiveData<Map<String, Long>>) {
        val counts = java.util.concurrent.ConcurrentHashMap<String, Long>()
        val remaining = java.util.concurrent.atomic.AtomicInteger(songs.size)
        val finish = { if (remaining.decrementAndGet() <= 0) result.postValue(HashMap(counts)) }

        for ((songId, title) in songs) {
            if (title.isBlank()) { finish(); continue }
            api.search(title).enqueue(object : Callback<KoitoSearchResponse> {
                override fun onResponse(call: Call<KoitoSearchResponse>, response: Response<KoitoSearchResponse>) {
                    val track = pickTrack(response.body()?.tracks, koitoAlbumId, title)
                    if (track == null) { finish(); return }
                    api.track(track.id).enqueue(object : Callback<KoitoEntity> {
                        override fun onResponse(call: Call<KoitoEntity>, entity: Response<KoitoEntity>) {
                            entity.body()?.listenCount?.let { if (it > 0) counts[songId] = it }
                            finish()
                        }

                        override fun onFailure(call: Call<KoitoEntity>, t: Throwable) { finish() }
                    })
                }

                override fun onFailure(call: Call<KoitoSearchResponse>, t: Throwable) { finish() }
            })
        }
    }

    private fun pickTrack(tracks: List<KoitoEntity>?, koitoAlbumId: Int, title: String): KoitoEntity? {
        if (tracks.isNullOrEmpty()) return null
        val target = title.trim()
        return tracks.firstOrNull { it.albumId == koitoAlbumId && it.label().equals(target, ignoreCase = true) }
            ?: tracks.firstOrNull { it.label().equals(target, ignoreCase = true) }
    }

    private fun pickMatch(items: List<KoitoEntity>?, mbid: String?, label: String?, artistName: String?): KoitoEntity? {
        if (items.isNullOrEmpty()) return null
        if (!mbid.isNullOrBlank()) {
            items.firstOrNull { it.musicBrainzId.equals(mbid, ignoreCase = true) }?.let { return it }
        }
        val target = label?.trim()
        return items.firstOrNull {
            it.label().equals(target, ignoreCase = true) &&
                (artistName.isNullOrBlank() || it.artists?.any { a -> a.name.equals(artistName, ignoreCase = true) } != false)
        }
    }

    private fun fetchLastPlayed(api: KoitoApi, albumId: Int?, artistId: Int?, callback: (Date?) -> Unit) {
        api.listens(albumId, artistId, 1).enqueue(object : Callback<KoitoListensResponse> {
            override fun onResponse(call: Call<KoitoListensResponse>, response: Response<KoitoListensResponse>) {
                callback(parseTime(response.body()?.items?.firstOrNull()?.time))
            }

            override fun onFailure(call: Call<KoitoListensResponse>, t: Throwable) {
                callback(null)
            }
        })
    }

    /** Koito returns RFC3339 UTC timestamps; the seconds prefix is enough for a "last played" date. */
    private fun parseTime(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            formatter.parse(if (raw.length >= 19) raw.substring(0, 19) else raw)
        } catch (e: Exception) {
            null
        }
    }
}
