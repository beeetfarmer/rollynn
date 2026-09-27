package com.cappielloantonio.tempo.audiomuse

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AudioMuseApi {
    /**
     * Turns a natural-language request into a track list. The returned item ids are
     * already the music server's own song ids, so they can go straight into a
     * Subsonic createPlaylist call (which saves the playlist under the user's own
     * account, unlike AudioMuse's create_playlist endpoint).
     */
    @POST("chat/api/chatPlaylist")
    fun chatPlaylist(@Body request: AudioMuseChatRequest): Call<AudioMuseChatResponse>

    /** Artists that sound alike, by the music server's artist id; ids in the result are the server's too. */
    /** Cheap, secret-free config read; used to check the server is up and the token accepted. */
    @GET("chat/api/config_defaults")
    fun configDefaults(): Call<okhttp3.ResponseBody>

    @GET("api/similar_artists")
    fun similarArtists(@Query("artist_id") artistId: String, @Query("n") count: Int): Call<List<AudioMuseSimilarArtist>>
}
