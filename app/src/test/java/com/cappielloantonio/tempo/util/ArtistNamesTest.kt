package com.cappielloantonio.tempo.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {
    @Test
    fun theFirstOfSeveralArtistsIsTaken() {
        for (names in listOf("A, B", "A/B", "A; B", "A & B", "A \u2022 B", "A feat. B", "A feat B", "A ft. B", "A ft B")) {
            assertEquals(names, "A", ArtistNames.first(names))
        }
    }

    @Test
    fun oneArtistIsLeftAlone() {
        assertEquals("Daft Punk", ArtistNames.first(" Daft Punk "))
        assertEquals("Little Feat", ArtistNames.first("Little Feat"))
    }
}
