package com.cappielloantonio.tempo.subsonic.api.navidrome

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface NavidromeService {
    @POST("auth/login")
    fun login(@Body credentials: NavidromeCredentials): Call<NavidromeLoginResponse>

    @GET("api/song")
    fun getSongs(
        @Header("X-ND-Authorization") auth: String,
        @Query("_sort") sort: String,
        @Query("_order") order: String,
        @Query("_start") start: Int,
        @Query("_end") end: Int,
        @Query("recently_played") recentlyPlayed: Boolean,
        @Query("library_id") libraryId: String?
    ): Call<List<NavidromeSong>>

    @GET("api/song")
    fun getSongsSorted(
        @Header("X-ND-Authorization") auth: String,
        @Query("_sort") sort: String,
        @Query("_order") order: String,
        @Query("_start") start: Int,
        @Query("_end") end: Int,
        @Query("library_id") libraryId: String?
    ): Call<List<NavidromeSong>>

    @GET("api/artist")
    fun getArtists(
        @Header("X-ND-Authorization") auth: String,
        @Query("_sort") sort: String,
        @Query("_order") order: String,
        @Query("_start") start: Int,
        @Query("_end") end: Int,
        @Query("recently_played") recentlyPlayed: Boolean,
        @Query("library_id") libraryId: String?
    ): Call<List<NavidromeArtist>>

    @GET("api/artist")
    fun getArtistsSorted(
        @Header("X-ND-Authorization") auth: String,
        @Query("_sort") sort: String,
        @Query("_order") order: String,
        @Query("_start") start: Int,
        @Query("_end") end: Int,
        @Query("library_id") libraryId: String?
    ): Call<List<NavidromeArtist>>
}
