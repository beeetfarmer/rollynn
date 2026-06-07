package com.cappielloantonio.tempo.lastfm

import com.cappielloantonio.tempo.BuildConfig
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LastFmRetrofitClient {
    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(LastFm.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(getOkHttpClient())
        .build()

    private fun getOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .addInterceptor(getHttpLoggingInterceptor())
            .build()
    }

    private fun getHttpLoggingInterceptor(): HttpLoggingInterceptor {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(
            if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        )
        return loggingInterceptor
    }
}
