package com.cappielloantonio.tempo.viewmodel

import com.cappielloantonio.tempo.subsonic.models.AlbumID3
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistPageViewModelIsTypeTest {
    private fun album(types: List<String>?, songs: Int? = 0) =
        AlbumID3(songCount = songs, releaseTypes = types)

    @Test
    fun lowercaseReleaseTypesMatch() {
        assertTrue(ArtistPageViewModel.isType(album(listOf("album")), "album"))
        assertTrue(ArtistPageViewModel.isType(album(listOf("single")), "single"))
        assertTrue(ArtistPageViewModel.isType(album(listOf("ep")), "ep"))
    }

    @Test
    fun capitalizedReleaseTypesMatch() {
        assertTrue(ArtistPageViewModel.isType(album(listOf("Album")), "album"))
        assertTrue(ArtistPageViewModel.isType(album(listOf("Single")), "single"))
        assertTrue(ArtistPageViewModel.isType(album(listOf("EP")), "ep"))
        assertTrue(ArtistPageViewModel.isType(album(listOf("Ep")), "ep"))
    }

    @Test
    fun capitalizedTypeDoesNotMatchOtherSections() {
        val a = album(listOf("Album"), songs = 2)
        assertFalse(ArtistPageViewModel.isType(a, "single"))
        assertFalse(ArtistPageViewModel.isType(a, "ep"))
    }

    @Test
    fun anyOfSeveralTypesMatches() {
        val a = album(listOf("Compilation", "Album"))
        assertTrue(ArtistPageViewModel.isType(a, "album"))
        assertFalse(ArtistPageViewModel.isType(a, "ep"))
    }

    @Test
    fun releaseTypesWinOverSongCount() {
        assertTrue(ArtistPageViewModel.isType(album(listOf("Single"), songs = 12), "single"))
        assertFalse(ArtistPageViewModel.isType(album(listOf("Single"), songs = 12), "album"))
    }

    @Test
    fun emptyOrNullTypesFallBackToSongCount() {
        for (types in listOf(null, emptyList<String>())) {
            assertTrue(ArtistPageViewModel.isType(album(types, 1), "single"))
            assertTrue(ArtistPageViewModel.isType(album(types, 2), "single"))
            assertTrue(ArtistPageViewModel.isType(album(types, 3), "ep"))
            assertTrue(ArtistPageViewModel.isType(album(types, 7), "ep"))
            assertTrue(ArtistPageViewModel.isType(album(types, 8), "album"))
            assertFalse(ArtistPageViewModel.isType(album(types, 0), "single"))
            assertFalse(ArtistPageViewModel.isType(album(types, null), "album"))
        }
    }

    @Test
    fun unrelatedTypeMatchesNothing() {
        val a = album(listOf("Live"))
        assertFalse(ArtistPageViewModel.isType(a, "album"))
        assertFalse(ArtistPageViewModel.isType(a, "single"))
        assertFalse(ArtistPageViewModel.isType(a, "ep"))
    }
}
