package com.cappielloantonio.tempo.listenbrainz

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ListenBrainzApi {
    /** Public, so a username is enough. 1000 is all ListenBrainz calculates. */
    @GET("1/stats/user/{user}/recordings?range=all_time&count=1000")
    fun topRecordings(@Path("user") user: String): Call<ListenBrainzRecordingsResponse>
}

@Keep
data class ListenBrainzRecordingsResponse(val payload: ListenBrainzPayload? = null)

@Keep
data class ListenBrainzPayload(val recordings: List<ListenBrainzRecording>? = null)

@Keep
data class ListenBrainzRecording(
    @SerializedName("artist_name") val artistName: String? = null,
    @SerializedName("track_name") val trackName: String? = null,
    @SerializedName("listen_count") val listenCount: Long = 0,
)

object ListenBrainz {
    private const val BASE_URL = "https://api.listenbrainz.org/"

    // No request logging: the address holds the username.
    val api: ListenBrainzApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(OkHttpClient())
            .build()
            .create(ListenBrainzApi::class.java)
    }

    val counts: ListenBrainzCounts by lazy { ListenBrainzCounts(api) }
}
