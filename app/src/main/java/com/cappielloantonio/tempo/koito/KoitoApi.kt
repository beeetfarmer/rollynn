package com.cappielloantonio.tempo.koito

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface KoitoApi {
    @GET("apis/web/v1/search")
    fun search(@Query("q") query: String): Call<KoitoSearchResponse>

    // Search results carry listen_count=0; the real count is only on the entity endpoints.
    @GET("apis/web/v1/album/{id}")
    fun album(@Path("id") id: Int): Call<KoitoEntity>

    @GET("apis/web/v1/artist/{id}")
    fun artist(@Path("id") id: Int): Call<KoitoEntity>

    @GET("apis/web/v1/track/{id}")
    fun track(@Path("id") id: Int): Call<KoitoEntity>

    /** Listens are returned newest-first, so limit=1 yields the last played. */
    @GET("apis/web/v1/listens")
    fun listens(
        @Query("album_id") albumId: Int?,
        @Query("artist_id") artistId: Int?,
        @Query("limit") limit: Int,
    ): Call<KoitoListensResponse>
}
