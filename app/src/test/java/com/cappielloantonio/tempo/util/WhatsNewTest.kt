package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.App
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WhatsNewTest {
    private val notes = mapOf("v2.0.0" to "• New thing", "v1.0.0" to "• Old thing", "v3.0.0" to "• Newer thing")
    private val asked = mutableListOf<String>()

    private fun launch(version: String = "v2.0.0", freshInstall: Boolean = false): String? =
        WhatsNew.notesToShow(version, freshInstall) { asked += it; notes[it] }

    @Test
    fun anUpdateShowsTheNewVersionsNotes() {
        Preferences.setWhatsNewSeenVersion("v1.0.0")

        assertEquals("• New thing", launch())
    }

    @Test
    fun theyShowOnlyOnce() {
        Preferences.setWhatsNewSeenVersion("v1.0.0")
        launch()

        assertNull(launch())
        assertNull(launch())
    }

    @Test
    fun theNextUpdateShowsAgain() {
        Preferences.setWhatsNewSeenVersion("v1.0.0")
        launch()

        assertEquals("• Newer thing", launch(version = "v3.0.0"))
    }

    @Test
    fun aFreshInstallShowsNothingButRemembersTheVersion() {
        assertNull(launch(freshInstall = true))

        assertEquals("v2.0.0", Preferences.getWhatsNewSeenVersion())
        assertTrue("the notes aren't even read", asked.isEmpty())
    }

    @Test
    fun theFirstUpdateAfterAFreshInstallShows() {
        launch(version = "v1.0.0", freshInstall = true)

        assertEquals("• New thing", launch(version = "v2.0.0", freshInstall = false))
    }

    @Test
    fun anUpdateFromAVersionWithoutThisFeatureShows() {
        // Nothing was stored before, but the app was installed earlier than this update.
        assertEquals("• New thing", launch(freshInstall = false))
    }

    @Test
    fun aVersionWithoutNotesShowsNothingAndIsNotAskedAgain() {
        Preferences.setWhatsNewSeenVersion("v1.0.0")

        assertNull(launch(version = "v9.9.9"))
        assertNull(launch(version = "v9.9.9"))
        assertEquals(listOf("v9.9.9"), asked)
    }

    @Test
    fun goingBackToAnOlderVersionShowsItsNotes() {
        Preferences.setWhatsNewSeenVersion("v2.0.0")

        assertEquals("• Old thing", launch(version = "v1.0.0"))
    }

    @Test
    fun aMissingNotesFileReadsAsNothing() {
        assertNull(WhatsNew.read(App.getContext(), "v0.0.0-missing"))
    }

    @Test
    fun theInstalledVersionIsHandledWithoutCrashing() {
        WhatsNew.notesToShow(App.getContext())

        assertEquals(com.cappielloantonio.tempo.BuildConfig.VERSION_NAME, Preferences.getWhatsNewSeenVersion())
    }
}
