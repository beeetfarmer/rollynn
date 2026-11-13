package com.cappielloantonio.tempo.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockLayoutTest {
    // A 400px-wide screen; a 60px-tall dock that is 250px wide expanded, 60 minimized.
    private fun layout(
        navCompact: Boolean,
        playerCompact: Boolean,
        playerVisible: Boolean = true,
        screen: Int = 400,
        navWidth: Int = if (navCompact) 60 else 250,
        minPlayer: Int = 150,
    ) = DockLayout.compute(
        screenWidth = screen,
        navWidth = navWidth,
        rowHeight = 60,
        navCompact = navCompact,
        playerCompact = playerCompact,
        playerVisible = playerVisible,
        stackedMargin = 28,
        rowMargin = 12,
        gap = 8,
        minPlayerWidth = minPlayer,
    )

    @Test
    fun bothExpandedStacksAsBefore() {
        val r = layout(navCompact = false, playerCompact = false)
        assertFalse(r.sameRow)
        assertEquals(75, r.navX)          // (400 - 250) / 2
        assertEquals(344, r.playerWidth)  // 400 - 2 * 28
        assertEquals(28, r.playerX)
    }

    @Test
    fun minimizedNavPutsFullPlayerBesideIt() {
        val r = layout(navCompact = true, playerCompact = false)
        assertTrue(r.sameRow)
        assertEquals(12, r.navX)
        assertEquals(12 + 60 + 8, r.playerX)
        assertEquals(400 - 24 - 60 - 8, r.playerWidth)  // fills the rest of the row
        assertEquals(400 - 12, r.playerX + r.playerWidth)
    }

    @Test
    fun minimizedPlayerSitsBesideExpandedNav() {
        val r = layout(navCompact = false, playerCompact = true)
        assertTrue(r.sameRow)
        assertEquals(60, r.playerWidth)
        // Group of 250 + 8 + 60 = 318 is centred.
        assertEquals(41, r.navX)
        assertEquals(41 + 250 + 8, r.playerX)
    }

    @Test
    fun bothMinimizedShareOneCentredRow() {
        val r = layout(navCompact = true, playerCompact = true)
        assertTrue(r.sameRow)
        assertEquals(60, r.playerWidth)
        assertEquals(136, r.navX)  // (400 - 128) / 2
        assertEquals(204, r.playerX)
    }

    @Test
    fun wideNavWithMinimizedPlayerStacksWhenTooNarrow() {
        // 330 + 8 + 60 = 398 > 400 - 24
        val r = layout(navCompact = false, playerCompact = true, navWidth = 330)
        assertFalse(r.sameRow)
        assertEquals(35, r.navX)
        assertEquals(60, r.playerWidth)
        assertEquals(170, r.playerX)  // bubble centred above the dock
    }

    @Test
    fun minimizedNavStacksWhenPlayerWouldBeTooNarrow() {
        val r = layout(navCompact = true, playerCompact = false, screen = 250)
        // 250 - 24 - 60 - 8 = 158 >= 150 fits; at 240 it would be 148.
        assertTrue(r.sameRow)
        val narrow = layout(navCompact = true, playerCompact = false, screen = 240)
        assertFalse(narrow.sameRow)
        assertEquals(240 - 56, narrow.playerWidth)
    }

    @Test
    fun noPlayerKeepsNavCentred() {
        for (navCompact in listOf(true, false)) for (playerCompact in listOf(true, false)) {
            val r = layout(navCompact, playerCompact, playerVisible = false)
            assertFalse(r.sameRow)
            assertEquals((400 - (if (navCompact) 60 else 250)) / 2, r.navX)
        }
    }

    @Test
    fun sameRowNeverOverlaps() {
        for (navCompact in listOf(true, false)) for (playerCompact in listOf(true, false)) {
            val r = layout(navCompact, playerCompact)
            if (!r.sameRow) continue
            val navWidth = if (navCompact) 60 else 250
            assertTrue(r.navX >= 12)
            assertTrue(r.playerX >= r.navX + navWidth + 8)
            assertTrue(r.playerX + r.playerWidth <= 400 - 12)
        }
    }
}
