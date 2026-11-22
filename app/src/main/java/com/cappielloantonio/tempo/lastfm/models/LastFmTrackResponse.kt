package com.cappielloantonio.tempo.lastfm.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class LastFmTrackResponse(
    @SerializedName("track")
    val track: LastFmTrack? = null,
    @SerializedName("error")
    val error: Int? = null
)

@Keep
data class LastFmTrack(
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("userplaycount")
    val userPlayCount: String? = null
)
