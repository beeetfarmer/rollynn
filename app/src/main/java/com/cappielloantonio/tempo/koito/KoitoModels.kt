package com.cappielloantonio.tempo.koito

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import java.util.Date

/** Response of GET /apis/web/v1/search?q=... */
@Keep
data class KoitoSearchResponse(
    val artists: List<KoitoEntity>? = null,
    val albums: List<KoitoEntity>? = null,
    val tracks: List<KoitoEntity>? = null,
)

/** A Koito artist/album/track. Albums/tracks use `title`, artists use `name`. */
@Keep
data class KoitoEntity(
    val id: Int = 0,
    @SerializedName("musicbrainz_id") val musicBrainzId: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerializedName("listen_count") val listenCount: Long = 0,
    @SerializedName("album_id") val albumId: Int? = null,
    val artists: List<KoitoSimpleArtist>? = null,
) {
    fun label(): String? = title ?: name
}

@Keep
data class KoitoSimpleArtist(val id: Int = 0, val name: String? = null)

/** Response of GET /apis/web/v1/listens (newest first). */
@Keep
data class KoitoListensResponse(val items: List<KoitoListen>? = null)

@Keep
data class KoitoListen(val time: String? = null)

/** Play count and last-played resolved from Koito for one album/artist. */
@Keep
data class KoitoStats(val playCount: Long, val lastPlayed: Date?)
