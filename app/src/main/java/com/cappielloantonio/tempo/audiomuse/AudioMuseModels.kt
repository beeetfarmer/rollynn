package com.cappielloantonio.tempo.audiomuse

import com.google.gson.annotations.SerializedName

data class AudioMuseChatRequest(
    @SerializedName("userInput") val userInput: String,
)

data class AudioMuseChatResponse(
    @SerializedName("response") val response: AudioMuseChatResult?,
    @SerializedName("error") val error: String?,
)

data class AudioMuseChatResult(
    @SerializedName("message") val message: String?,
    @SerializedName("query_results") val queryResults: List<AudioMuseTrack>?,
)

data class AudioMuseTrack(
    @SerializedName("item_id") val itemId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("artist") val artist: String?,
)
