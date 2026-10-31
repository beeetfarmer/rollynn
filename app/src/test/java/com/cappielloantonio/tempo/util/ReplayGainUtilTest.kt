package com.cappielloantonio.tempo.util

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.cappielloantonio.tempo.testutil.AppTestEnv
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
class ReplayGainUtilTest {
    private val player = mock(Player::class.java)

    @Before
    fun setUp() {
        `when`(player.previousMediaItemIndex).thenReturn(C.INDEX_UNSET)
    }

    private fun mode(mode: String) = AppTestEnv.setPreference("replay_gain_mode", mode)

    private fun tracks(vararg entries: Metadata.Entry): Tracks {
        val format = Format.Builder().setMetadata(Metadata(*entries)).build()
        val group = Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))
        return Tracks(ImmutableList.of(group))
    }

    private fun txxx(tag: String, value: String) = TextInformationFrame("TXXX", tag, ImmutableList.of(value))

    private fun vorbis(tag: String, value: String) = VorbisComment(tag, value)

    private fun volumeFor(db: Float) = 10f.pow(db / 20f)

    private fun assertVolume(expected: Float) {
        val volume = ArgumentCaptor.forClass(Float::class.java)
        verify(player).setVolume(volume.capture())
        assertEquals(expected, volume.value, 0.0001f)
    }

    private fun album(title: String?) = MediaItem.Builder()
        .setMediaMetadata(MediaMetadata.Builder().setAlbumTitle(title).build())
        .build()

    private fun previousAndCurrentAlbums(previous: String?, current: String?) {
        `when`(player.previousMediaItemIndex).thenReturn(0)
        `when`(player.getMediaItemAt(0)).thenReturn(album(previous))
        `when`(player.currentMediaItem).thenReturn(album(current))
    }

    @Test
    fun disabledLeavesTheVolumeAlone() {
        mode("disabled")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-6.0 dB")))
        assertVolume(1f)
    }

    @Test
    fun anUnknownModeLeavesTheVolumeAlone() {
        mode("something else")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-6.0 dB")))
        assertVolume(1f)
    }

    @Test
    fun noTagsLeavesTheVolumeAlone() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, Tracks.EMPTY)
        assertVolume(1f)
    }

    @Test
    fun trackModeReadsVorbisComments() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-6.5 dB")))
        assertVolume(volumeFor(-6.5f))
    }

    @Test
    fun trackModeReadsId3Tags() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, tracks(txxx("REPLAYGAIN_TRACK_GAIN", "-3.25 dB")))
        assertVolume(volumeFor(-3.25f))
    }

    @Test
    fun trackModeReadsMp4InternalFrames() {
        mode("track")
        ReplayGainUtil.setReplayGain(
            player,
            tracks(InternalFrame("com.apple.iTunes", "REPLAYGAIN_TRACK_GAIN", "-4.0 dB")),
        )
        assertVolume(volumeFor(-4f))
    }

    @Test
    fun albumModeUsesTheAlbumGainEvenWhenItIsTheSecondTag() {
        mode("album")
        ReplayGainUtil.setReplayGain(
            player,
            tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"), vorbis("REPLAYGAIN_ALBUM_GAIN", "-9.0 dB")),
        )
        assertVolume(volumeFor(-9f))
    }

    @Test
    fun trackModeUsesTheTrackGainEvenWhenItIsTheSecondTag() {
        mode("track")
        ReplayGainUtil.setReplayGain(
            player,
            tracks(vorbis("REPLAYGAIN_ALBUM_GAIN", "-9.0 dB"), vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB")),
        )
        assertVolume(volumeFor(-1f))
    }

    @Test
    fun autoUsesTheAlbumGainWithinAnAlbum() {
        mode("auto")
        previousAndCurrentAlbums("A", "A")
        ReplayGainUtil.setReplayGain(
            player,
            tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"), vorbis("REPLAYGAIN_ALBUM_GAIN", "-9.0 dB")),
        )
        assertVolume(volumeFor(-9f))
    }

    @Test
    fun autoFallsBackToTheTrackGainWithoutAnAlbumGain() {
        mode("auto")
        previousAndCurrentAlbums("A", "A")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB")))
        assertVolume(volumeFor(-1f))
    }

    @Test
    fun autoUsesTheTrackGainAcrossAlbums() {
        mode("auto")
        previousAndCurrentAlbums("A", "B")
        ReplayGainUtil.setReplayGain(
            player,
            tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"), vorbis("REPLAYGAIN_ALBUM_GAIN", "-9.0 dB")),
        )
        assertVolume(volumeFor(-1f))
    }

    @Test
    fun autoUsesTheTrackGainForTheFirstTrack() {
        mode("auto")
        `when`(player.currentMediaItem).thenReturn(album("A"))
        ReplayGainUtil.setReplayGain(
            player,
            tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "-1.0 dB"), vorbis("REPLAYGAIN_ALBUM_GAIN", "-9.0 dB")),
        )
        assertVolume(volumeFor(-1f))
    }

    @Test
    fun anUnparsableGainLeavesTheVolumeAlone() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("REPLAYGAIN_TRACK_GAIN", "loud")))
        assertVolume(1f)
    }

    // R128 gains are stored as Q7.8 fixed point, so -512 means -2 dB.
    @Test
    fun readsNegativeR128TrackGain() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("R128_TRACK_GAIN", "-512")))
        assertVolume(volumeFor(-2f))
    }

    @Test
    fun readsNegativeR128AlbumGain() {
        mode("album")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("R128_ALBUM_GAIN", "-768")))
        assertVolume(volumeFor(-3f))
    }

    @Test
    fun readsPositiveR128TrackGain() {
        mode("track")
        ReplayGainUtil.setReplayGain(player, tracks(vorbis("R128_TRACK_GAIN", "256")))
        assertVolume(volumeFor(1f))
    }
}
