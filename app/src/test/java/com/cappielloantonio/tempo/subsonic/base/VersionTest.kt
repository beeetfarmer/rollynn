package com.cappielloantonio.tempo.subsonic.base

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun comparesEachPartAsANumberNotAsText() {
        assertTrue(Version.of("1.9.0").isLowerThan(Version.of("1.10.0")))
        assertTrue(Version.of("2.0").compareTo(Version.of("1.99.99")) > 0)
    }

    @Test
    fun treatsMissingPartsAsZero() {
        assertEquals(0, Version.of("1.2").compareTo(Version.of("1.2.0")))
        assertTrue(Version.of("1.2").isLowerThan(Version.of("1.2.1")))
        assertFalse(Version.of("1.2.1").isLowerThan(Version.of("1.2")))
    }

    @Test
    fun equalVersionsAreNotLower() {
        assertFalse(Version.of("1.16.1").isLowerThan(Version.of("1.16.1")))
    }

    @Test
    fun anyVersionIsGreaterThanNull() {
        assertEquals(1, Version.of("0").compareTo(null))
    }

    @Test
    fun rejectsMalformedVersions() {
        for (bad in listOf(null, "", "1.", ".1", "1..2", "a.b", "1.2-beta", " 1.2")) {
            assertThrows(bad.toString(), IllegalArgumentException::class.java) { Version.of(bad) }
        }
    }

    @Test
    fun keepsTheOriginalString() {
        assertEquals("1.16.1", Version.of("1.16.1").toString())
        assertEquals("1.16.1", Version.of("1.16.1").versionString)
    }
}
