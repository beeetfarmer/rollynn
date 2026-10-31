package com.cappielloantonio.tempo.util

import android.net.Uri
import androidx.media3.common.HeartRating
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.cappielloantonio.tempo.model.Download
import com.cappielloantonio.tempo.subsonic.models.Child
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.cappielloantonio.tempo.testutil.AppTestEnv.db
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class MappingUtilTest {
    @Before
    fun setUp() = AppTestEnv.install()

    @After
    fun tearDown() = AppTestEnv.uninstall()

    private fun uri(item: MediaItem) = item.requestMetadata.mediaUri.toString()

    private fun extras(item: MediaItem) = item.mediaMetadata.extras!!

    @Test
    fun mapsASong() {
        val item = MappingUtil.mapMediaItem(
            Child(
                "s1", parentId = "dir", title = "Title", album = "Album", artist = "Artist", track = 3,
                year = 1999, genre = "Rock", coverArtId = "cover", size = 10, contentType = "audio/flac",
                suffix = "flac", duration = 200, bitrate = 900, samplingRate = 44100, bitDepth = 16,
                discNumber = 2, albumId = "al1", artistId = "ar1", playCount = 7, userRating = 4,
            ),
        )

        assertEquals("s1", item.mediaId)
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals("Album", item.mediaMetadata.albumTitle)
        assertEquals("Artist", item.mediaMetadata.artist)
        assertEquals(3, item.mediaMetadata.trackNumber)
        assertEquals(2, item.mediaMetadata.discNumber)
        assertEquals(1999, item.mediaMetadata.releaseYear)
        assertEquals(true, item.mediaMetadata.isPlayable)
        assertEquals(false, item.mediaMetadata.isBrowsable)

        val extras = extras(item)
        assertEquals("s1", extras.getString("id"))
        assertEquals("dir", extras.getString("parentId"))
        assertEquals("Rock", extras.getString("genre"))
        assertEquals("cover", extras.getString("coverArtId"))
        assertEquals(10L, extras.getLong("size"))
        assertEquals("flac", extras.getString("suffix"))
        assertEquals(200, extras.getInt("duration"))
        assertEquals(900, extras.getInt("bitrate"))
        assertEquals(44100, extras.getInt("samplingRate"))
        assertEquals(16, extras.getInt("bitDepth"))
        assertEquals(7L, extras.getLong("playCount"))
        assertEquals(4, extras.getInt("userRating"))
        assertEquals("al1", extras.getString("albumId"))
        assertEquals("ar1", extras.getString("artistId"))
        assertEquals(Constants.MEDIA_TYPE_MUSIC, extras.getString("type"))
    }

    @Test
    fun missingNumbersBecomeZero() {
        val item = MappingUtil.mapMediaItem(Child("s1"))
        val extras = extras(item)

        assertEquals(0, item.mediaMetadata.trackNumber)
        for (key in listOf("track", "year", "duration", "bitrate", "samplingRate", "bitDepth", "userRating", "discNumber")) {
            assertEquals(key, 0, extras.getInt(key, -1))
        }
        for (key in listOf("size", "playCount", "created", "starred", "bookmarkPosition")) {
            assertEquals(key, 0L, extras.getLong(key, -1))
        }
    }

    @Test
    fun aStarredSongGetsAHeart() {
        val starred = MappingUtil.mapMediaItem(Child("s1", starred = Date(5000)))
        val unstarred = MappingUtil.mapMediaItem(Child("s2"))

        assertTrue((starred.mediaMetadata.userRating as HeartRating).isHeart)
        assertEquals(5000L, extras(starred).getLong("starred"))
        assertFalse((unstarred.mediaMetadata.userRating as HeartRating).isHeart)
    }

    @Test
    fun artworkUsesTheContentProviderOnlyWithACover() {
        assertEquals("cover", MappingUtil.mapMediaItem(Child("s1", coverArtId = "cover")).mediaMetadata.artworkUri!!.lastPathSegment)
        assertNull(MappingUtil.mapMediaItem(Child("s2")).mediaMetadata.artworkUri)
    }

    @Test
    fun assetLinksAreBuiltOnlyForKnownValues() {
        val full = extras(MappingUtil.mapMediaItem(Child("s1", albumId = "a", artistId = "b", genre = "Rock", year = 1999)))
        for (key in listOf("assetLinkSong", "assetLinkAlbum", "assetLinkArtist", "assetLinkGenre", "assetLinkYear")) {
            assertNotNull(key, full.getString(key))
        }

        val sparse = extras(MappingUtil.mapMediaItem(Child("s2", year = 0)))
        assertNotNull(sparse.getString("assetLinkSong"))
        for (key in listOf("assetLinkAlbum", "assetLinkArtist", "assetLinkGenre", "assetLinkYear")) {
            assertNull(key, sparse.getString(key))
        }
    }

    @Test
    fun aSongStreamsWhenItIsNotDownloaded() {
        val item = MappingUtil.mapMediaItem(Child("s1"))

        assertTrue(uri(item), uri(item).startsWith("http://server/stream"))
        assertTrue(uri(item), uri(item).endsWith("&id=s1"))
        assertEquals(uri(item), extras(item).getString("uri"))
    }

    @Test
    fun aDownloadedSongPlaysTheLocalFile() {
        db.downloadDao().insert(Download("s1", downloadUri = "file:///music/s1.flac"))

        assertEquals("file:///music/s1.flac", uri(MappingUtil.mapMediaItem(Child("s1"))))
    }

    @Test
    fun aDownloadRecordWithoutAFileStillStreams() {
        db.downloadDao().insert(Download("s1", downloadUri = ""))

        assertTrue(uri(MappingUtil.mapMediaItem(Child("s1"))).startsWith("http://server/stream"))
    }

    @Test
    fun aListKeepsItsOrder() {
        val ids = MappingUtil.mapMediaItems(listOf(Child("b"), Child("a"), Child("c"))).map { it.mediaId }

        assertEquals(listOf("b", "a", "c"), ids)
    }

    @Test
    fun remappingALocalItemKeepsItsFile() {
        val old = MediaItem.Builder()
            .setMediaId("s1")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse("file:///music/s1.flac")).build(),
            )
            .build()

        assertEquals("file:///music/s1.flac", uri(MappingUtil.mapMediaItem(old)))
    }

    @Test
    fun remappingAStreamReplacesTheOldBitrateAndFormat() {
        val old = MediaItem.Builder()
            .setMediaId("s1")
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(Uri.parse("http://server/stream?u=me&maxBitRate=128&format=mp3&id=s1"))
                    .build(),
            )
            .build()

        val remapped = uri(MappingUtil.mapMediaItem(old))

        assertFalse(remapped, remapped.contains("maxBitRate=128"))
        assertFalse(remapped, remapped.contains("format=mp3"))
        assertTrue(remapped, remapped.contains("id=s1"))
    }

    @Test
    fun remappingAnItemWithNoUriStaysEmpty() {
        assertNull(MappingUtil.mapMediaItem(MediaItem.Builder().setMediaId("s1").build()).requestMetadata.mediaUri)
    }

    @Test
    fun aDownloadRequestUsesTheDownloadUrl() {
        val item = MappingUtil.mapDownload(Child("s1", title = "Title", samplingRate = 48000))

        assertTrue(uri(item), uri(item).startsWith("http://server/download"))
        assertEquals("Title", item.mediaMetadata.title)
        assertEquals(48000, extras(item).getInt("samplingRate"))
        assertEquals(uri(item), item.localConfiguration!!.uri.toString())
    }

    @Test
    fun aDownloadRequestCanAskForTheTranscodedFile() {
        AppTestEnv.setPreference("audio_transcode_download", true)

        val item = MappingUtil.mapDownload(Child("s1"))

        assertTrue(uri(item), uri(item).startsWith("http://server/stream"))
    }

    @Test
    fun aDownloadListKeepsItsOrder() {
        assertEquals(listOf("b", "a"), MappingUtil.mapDownloads(listOf(Child("b"), Child("a"))).map { it.mediaId })
    }

    @Test
    fun mapsARadioStation() {
        val item = MappingUtil.mapInternetRadioStation(
            InternetRadioStation("r1", "Station", "http://radio/stream"),
        )

        assertEquals("r1", item.mediaId)
        assertEquals("Station", item.mediaMetadata.title)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
        assertEquals("http://radio/stream", uri(item))
        assertEquals(Constants.MEDIA_TYPE_RADIO, extras(item).getString("type"))
    }

    @Test
    fun mapsAPodcastEpisode() {
        val item = MappingUtil.mapMediaItem(
            PodcastEpisode(id = "e1", title = "Episode", artist = "Host", coverArtId = "cover", streamId = "st1", description = "About"),
        )

        assertEquals("e1", item.mediaId)
        assertEquals("Episode", item.mediaMetadata.title)
        assertEquals("cover", item.mediaMetadata.artworkUri!!.lastPathSegment)
        assertEquals("About", extras(item).getString("description"))
        assertEquals(Constants.MEDIA_TYPE_PODCAST, extras(item).getString("type"))
        assertTrue(uri(item), uri(item).endsWith("&id=st1"))
    }

    @Ignore("Known bug: a missing cover becomes an albumArt/null URI")
    @Test
    fun aPodcastEpisodeWithoutCoverArtHasNoArtwork() {
        val item = MappingUtil.mapMediaItem(PodcastEpisode(id = "e1", streamId = "st1"))

        assertNull(item.mediaMetadata.artworkUri)
    }
}
