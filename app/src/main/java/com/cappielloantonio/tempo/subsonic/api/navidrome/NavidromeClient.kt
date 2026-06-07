package com.cappielloantonio.tempo.subsonic.api.navidrome

import com.cappielloantonio.tempo.subsonic.models.ArtistID3
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.util.Preferences
import com.cappielloantonio.tempo.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class NavidromeClient {

    companion object {
        @Volatile
        private var instance: NavidromeClient? = null

        @JvmStatic
        fun getInstance(): NavidromeClient {
            return instance ?: synchronized(this) {
                instance ?: NavidromeClient().also { instance = it }
            }
        }

        @JvmStatic
        fun refresh() {
            instance = null
        }

        @JvmStatic
        fun clearCredentials() {
            synchronized(this) {
                instance?.jwtToken = null
                instance = null
            }
        }
    }

    private val service: NavidromeService
    @Volatile
    private var jwtToken: String? = null

    init {
        val baseUrl = getBaseUrl()

        val gson = GsonBuilder().setLenient().create()

        val client = OkHttpClient.Builder()
            .callTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().setLevel(
                if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                else HttpLoggingInterceptor.Level.NONE
            ))
            .build()

        service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .client(client)
            .build()
            .create(NavidromeService::class.java)
    }

    private fun getBaseUrl(): String {
        val server = Preferences.getServer() ?: ""
        return if (server.endsWith("/")) server else "$server/"
    }

    @Synchronized
    private fun authenticate(forceRefresh: Boolean = false): String? {
        if (!forceRefresh && !jwtToken.isNullOrBlank()) {
            return jwtToken
        }

        val username = Preferences.getUser() ?: return null
        val password = Preferences.getPassword() ?: return null

        try {
            val response = service.login(NavidromeCredentials(username, password)).execute()
            if (response.isSuccessful && response.body()?.token != null) {
                jwtToken = "Bearer ${response.body()!!.token}"
                return jwtToken
            }
        } catch (_: Exception) {
        }

        jwtToken = null
        return null
    }

    fun getRecentlyPlayedSongs(count: Int): List<Child> {
        val songs = executeWithAuth { token ->
            service.getSongs(
                auth = token,
                sort = "play_date",
                order = "DESC",
                start = 0,
                end = count,
                recentlyPlayed = true
            )
        } ?: return emptyList()

        return songs.map { it.toChild() }
    }

    fun getRecentlyPlayedArtists(count: Int): List<ArtistID3> {
        val artists = executeWithAuth { token ->
            service.getArtists(
                auth = token,
                sort = "play_date",
                order = "DESC",
                start = 0,
                end = count,
                recentlyPlayed = true
            )
        } ?: return emptyList()

        return artists.map { it.toArtistID3() }
    }

    fun getTopPlayedArtists(count: Int): List<ArtistID3> {
        val artists = executeWithAuth { token ->
            service.getArtistsSorted(
                auth = token,
                sort = "play_count",
                order = "DESC",
                start = 0,
                end = count
            )
        } ?: return emptyList()

        return artists.map { it.toArtistID3() }
    }

    fun getTopPlayedSongs(count: Int): List<Child> {
        val songs = executeWithAuth { token ->
            service.getSongsSorted(
                auth = token,
                sort = "play_count",
                order = "DESC",
                start = 0,
                end = count
            )
        } ?: return emptyList()

        return songs.map { it.toChild() }
    }

    private fun <T> executeWithAuth(request: (String) -> Call<T>): T? {
        val firstToken = authenticate() ?: return null
        val firstResponse = try {
            request(firstToken).execute()
        } catch (_: Exception) {
            return null
        }

        if (firstResponse.isSuccessful) {
            return firstResponse.body()
        }

        if (firstResponse.code() != 401 && firstResponse.code() != 403) {
            return null
        }

        val refreshedToken = authenticate(forceRefresh = true) ?: return null
        val retryResponse = try {
            request(refreshedToken).execute()
        } catch (_: Exception) {
            return null
        }

        if (retryResponse.isSuccessful) {
            return retryResponse.body()
        }

        return null
    }

    private fun NavidromeArtist.toArtistID3(): ArtistID3 {
        return ArtistID3(
            id = id,
            name = name,
            coverArtId = coverArtId ?: "ar-$id",
            albumCount = albumCount ?: 0,
            starred = if (starred == true) java.util.Date() else null
        )
    }

    private fun NavidromeSong.toChild(): Child {
        return Child(
            id = id,
            title = title,
            album = album,
            artist = artist,
            albumId = albumId,
            artistId = artistId,
            duration = duration?.toInt(),
            bitrate = bitRate,
            size = size,
            suffix = suffix,
            contentType = contentType,
            path = path,
            genre = genre,
            year = year,
            track = trackNumber,
            discNumber = discNumber,
            playCount = playCount,
            coverArtId = coverArtId ?: id,
            userRating = rating,
            starred = if (starred == true) java.util.Date() else null
        )
    }
}
