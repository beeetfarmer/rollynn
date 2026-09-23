package com.cappielloantonio.tempo.koito

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
 * Talks to a self-hosted Koito listening-stats server. Reads are authenticated with the
 * user's API key via `Authorization: Token <key>` (the same key used for scrobbling), which
 * Koito accepts on its web read API and simply ignores on public instances.
 */
object KoitoClient {
    private const val TAG = "KoitoClient"

    private var retrofit: Retrofit? = null
    private var builtForUrl: String? = null

    @JvmStatic
    fun getBaseUrl(): String? {
        val raw = Preferences.getKoitoServerUrl()?.trim()
        if (raw.isNullOrEmpty()) return null
        val withScheme = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "http://$raw"
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }

    @JvmStatic
    fun isConfigured(): Boolean = getBaseUrl() != null

    @JvmStatic
    @Synchronized
    fun getApi(): KoitoApi? {
        val baseUrl = getBaseUrl() ?: return null
        if (retrofit == null || builtForUrl != baseUrl) {
            retrofit = try {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(buildClient())
                    .build()
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Unusable Koito server URL: $baseUrl", exception)
                return null
            }
            builtForUrl = baseUrl
        }
        return retrofit?.create(KoitoApi::class.java)
    }

    /** Drops the cached client so the next call picks up new settings. */
    @JvmStatic
    @Synchronized
    fun reset() {
        retrofit = null
        builtForUrl = null
    }

    private fun buildClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(tokenInterceptor)
            .addInterceptor(logging)
            .build()
    }

    private val tokenInterceptor = Interceptor { chain ->
        val key = Preferences.getKoitoApiKey()
        val request = if (!key.isNullOrBlank()) {
            chain.request().newBuilder().header("Authorization", "Token $key").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }
}
