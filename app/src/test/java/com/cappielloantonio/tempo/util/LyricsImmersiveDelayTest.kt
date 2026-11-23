package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.App
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsImmersiveDelayTest {
    private val prefs get() = App.getInstance().preferences

    @Test
    fun itWaitsFourSecondsByDefault() {
        assertEquals(4, Preferences.getLyricsImmersiveDelaySeconds())
    }

    @Test
    fun itKeepsTheChosenSeconds() {
        Preferences.setLyricsImmersiveDelaySeconds(7)

        assertEquals(7, Preferences.getLyricsImmersiveDelaySeconds())
    }

    @Test
    fun zeroTurnsItOff() {
        Preferences.setLyricsImmersiveDelaySeconds(0)

        assertEquals(0, Preferences.getLyricsImmersiveDelaySeconds())
    }

    @Test
    fun itStaysWithinZeroToTen() {
        Preferences.setLyricsImmersiveDelaySeconds(25)
        assertEquals(10, Preferences.getLyricsImmersiveDelaySeconds())

        Preferences.setLyricsImmersiveDelaySeconds(-3)
        assertEquals(0, Preferences.getLyricsImmersiveDelaySeconds())
    }

    @Test
    fun aValueStoredOutOfRangeIsReadWithinIt() {
        // The settings slider writes the key directly.
        prefs.edit().putInt("lyrics_immersive_delay", 99).commit()

        assertEquals(10, Preferences.getLyricsImmersiveDelaySeconds())
    }
}
