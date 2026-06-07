package com.cappielloantonio.tempo.repository

import android.text.TextUtils
import androidx.lifecycle.MutableLiveData
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.util.TTMLParser
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BetterLyricsRepository {

    private val client = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    fun getLyrics(media: Child): MutableLiveData<LyricsList> {
        val result = MutableLiveData<LyricsList>(null)

        if (TextUtils.isEmpty(media.artist) || TextUtils.isEmpty(media.title)) {
            return result
        }

        executor.execute {
            val urlBuilder = API_BASE_URL.toHttpUrl().newBuilder()
                .addQueryParameter("s", media.title)
                .addQueryParameter("a", media.artist)

            media.duration?.let { dur ->
                if (dur > 0) urlBuilder.addQueryParameter("d", dur.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Accept", "application/json")
                .header("User-Agent", "Rollynn")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful || response.body == null) return@execute

                val json = gson.fromJson(response.body!!.string(), JsonObject::class.java)
                    ?: return@execute
                val ttml = json.get("ttml")?.takeIf { !it.isJsonNull }?.asString
                if (ttml.isNullOrBlank()) return@execute

                TTMLParser.parse(ttml)?.let { result.postValue(it) }
            } catch (_: Exception) {
            }
        }

        return result
    }

    companion object {
        private const val API_BASE_URL = "https://lyrics-api.boidu.dev/getLyrics"
    }
}
