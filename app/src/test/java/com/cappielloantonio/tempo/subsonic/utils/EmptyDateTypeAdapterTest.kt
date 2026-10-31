package com.cappielloantonio.tempo.subsonic.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import java.time.Instant
import java.util.Date

class EmptyDateTypeAdapterTest {
    private val adapter = EmptyDateTypeAdapter()

    private fun parse(value: String): Date? =
        adapter.deserialize(JsonPrimitive(value), Date::class.java, mock(JsonDeserializationContext::class.java))

    private fun date(iso: String) = Date.from(Instant.parse(iso))

    @Test
    fun parsesMilliseconds() {
        assertEquals(date("2024-01-02T03:04:05.678Z"), parse("2024-01-02T03:04:05.678Z"))
    }

    @Test
    fun parsesWholeSeconds() {
        assertEquals(date("2024-01-02T03:04:05Z"), parse("2024-01-02T03:04:05Z"))
    }

    @Test
    fun readsADateWithoutZoneAsUtc() {
        assertEquals(date("2024-01-02T03:04:05Z"), parse("2024-01-02T03:04:05"))
    }

    @Test
    fun ignoresSurroundingWhitespace() {
        assertEquals(date("2024-01-02T03:04:05Z"), parse("  2024-01-02T03:04:05Z "))
    }

    @Test
    fun emptyAndBlankBecomeNull() {
        assertNull(parse(""))
        assertNull(parse("   "))
    }

    @Test
    fun unparsableBecomesNull() {
        assertNull(parse("not a date"))
        assertNull(parse("2024-01-02"))
    }

    // Go servers such as Navidrome write up to nine fractional digits.
    @Test
    fun parsesNanosecondPrecisionTimestamps() {
        val parsed = parse("2024-01-02T03:04:05.123456789Z")
        assertEquals(date("2024-01-02T03:04:05.123Z"), parsed)
    }

    @Test
    fun readsAShortFractionAsPartOfASecond() {
        assertEquals(date("2024-01-02T03:04:05.500Z"), parse("2024-01-02T03:04:05.5Z"))
    }

    @Test
    fun appliesAnExplicitUtcOffset() {
        assertEquals(date("2024-01-02T02:04:05Z"), parse("2024-01-02T03:04:05+01:00"))
        assertEquals(date("2024-01-02T08:34:05Z"), parse("2024-01-02T03:04:05-05:30"))
    }

    @Test
    fun appliesAnOffsetAfterAFraction() {
        assertEquals(date("2024-01-02T02:04:05.678Z"), parse("2024-01-02T03:04:05.678123+01:00"))
    }
}
