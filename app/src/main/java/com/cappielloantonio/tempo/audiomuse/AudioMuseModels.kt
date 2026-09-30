package com.cappielloantonio.tempo.audiomuse

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

// Kept: Gson fills these reflectively, and R8 otherwise drops them from the generic
// signatures Retrofit reads, turning list items into LinkedTreeMaps in release builds.
@Keep
data class AudioMuseChatRequest(
    @SerializedName("userInput") val userInput: String,
)

@Keep
data class AudioMuseChatResponse(
    @SerializedName("response") val response: AudioMuseChatResult?,
    @SerializedName("error") val error: String?,
)

@Keep
data class AudioMuseChatResult(
    @SerializedName("message") val message: String?,
    @SerializedName("query_results") val queryResults: List<AudioMuseTrack>?,
)

@Keep
data class AudioMuseTrack(
    @SerializedName("item_id") val itemId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
)

@Keep
data class AudioMuseSimilarArtist(
    @SerializedName("artist") val artist: String?,
    @SerializedName("artist_id") val artistId: String?,
)
