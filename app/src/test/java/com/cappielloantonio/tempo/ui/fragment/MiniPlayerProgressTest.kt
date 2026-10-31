package com.cappielloantonio.tempo.ui.fragment

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerProgressTest {

    @Test
    fun durationIsInWholeSeconds() {
        assertEquals(185, PlayerBottomSheetFragment.durationSeconds(185_400L))
    }

    @Test
    fun aTrackStillLoadingHasNoLengthYet() {
        // Cast straight to int this constant is about 1.5 billion, which leaves
        // the bar at zero for the whole track.
        assertEquals(0, PlayerBottomSheetFragment.durationSeconds(C.TIME_UNSET))
    }

    @Test
    fun aNegativeDurationIsTreatedAsUnknown() {
        assertEquals(0, PlayerBottomSheetFragment.durationSeconds(-5_000L))
    }
}
