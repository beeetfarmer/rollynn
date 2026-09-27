package com.cappielloantonio.tempo.audiomuse

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AudioMuseApi {
    /**
     * Turns a natural-language request into a track list. The returned item ids are
     * already the music server's own song ids, so they can go straight into a
     * Subsonic createPlaylist call (which saves the playlist under the user's own
     * account, unlike AudioMuse's create_playlist endpoint).
     */
    @POST("chat/api/chatPlaylist")
    fun chatPlaylist(@Body request: AudioMuseChatRequest): Call<AudioMuseChatResponse>
}
