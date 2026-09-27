package com.cappielloantonio.tempo.audiomuse

import android.util.Log
import com.cappielloantonio.tempo.BuildConfig
import com.cappielloantonio.tempo.util.Preferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Talks to a self-hosted AudioMuse-AI server. When the server has authentication
 * enabled, external clients send its global API_TOKEN as `Authorization: Bearer <token>`.
 */
object AudioMuseClient {
    private const val TAG = "AudioMuseClient"

    private var retrofit: Retrofit? = null
    private var builtForUrl: String? = null

    @JvmStatic
    fun getBaseUrl(): String? {
        val raw = Preferences.getAudioMuseServerUrl()?.trim()
        if (raw.isNullOrEmpty()) return null
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "http://$raw"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    @JvmStatic
    fun isConfigured(): Boolean = getBaseUrl() != null

    @JvmStatic
    @Synchronized
    fun getApi(): AudioMuseApi? {
        val baseUrl = getBaseUrl() ?: return null
        if (retrofit == null || builtForUrl != baseUrl) {
            retrofit = try {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(buildClient())
                    .build()
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Unusable AudioMuse-AI server URL: $baseUrl", exception)
                return null
            }
            builtForUrl = baseUrl
        }
        return retrofit?.create(AudioMuseApi::class.java)
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        // The server plans the playlist with an LLM before querying the library, which
        // can take minutes on a local model, so allow a long read.
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .addInterceptor(tokenInterceptor)
            .addInterceptor(logging)
            .build()
    }

    private val tokenInterceptor = Interceptor { chain ->
        val token = Preferences.getAudioMuseApiToken()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder().header("Authorization", "Bearer ${token.trim()}").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
}
