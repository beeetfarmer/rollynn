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
    val image: KoitoImage? = null,
) {
    fun label(): String? = title ?: name
    fun artistNames(): String? = artists?.mapNotNull { it.name }?.joinToString(", ")?.ifBlank { null }
}

@Keep
data class KoitoSimpleArtist(val id: Int = 0, val name: String? = null)

@Keep
data class KoitoImage(val xs: String? = null, val small: String? = null, val medium: String? = null, val large: String? = null)

/** Response of GET /apis/web/v1/summary?year=&month= — the data behind Koito's Rewind page. */
@Keep
data class KoitoSummary(
    @SerializedName("top_artists") val topArtists: List<KoitoRankedItem>? = null,
    @SerializedName("top_albums") val topAlbums: List<KoitoRankedItem>? = null,
    @SerializedName("top_tracks") val topTracks: List<KoitoRankedItem>? = null,
    @SerializedName("minutes_listened") val minutesListened: Long = 0,
    val plays: Long = 0,
    @SerializedName("unique_tracks") val uniqueTracks: Long = 0,
    @SerializedName("unique_albums") val uniqueAlbums: Long = 0,
    @SerializedName("unique_artists") val uniqueArtists: Long = 0,
    @SerializedName("new_tracks") val newTracks: Long = 0,
    @SerializedName("new_albums") val newAlbums: Long = 0,
    @SerializedName("new_artists") val newArtists: Long = 0,
)

@Keep
data class KoitoRankedItem(val item: KoitoEntity? = null, val rank: Int = 0)

/** Response of GET /apis/web/v1/listens (newest first). */
@Keep
data class KoitoListensResponse(val items: List<KoitoListen>? = null)

@Keep
data class KoitoListen(val time: String? = null)

/** Play count and last-played resolved from Koito for one album/artist. */
@Keep
data class KoitoStats(val playCount: Long, val lastPlayed: Date?)
