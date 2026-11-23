package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.subsonic.models.Line
import com.cappielloantonio.tempo.subsonic.models.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTimelineTest {
    private val noSecondary = LyricsTimeline.Secondary { _, _ -> null }

    private fun line(start: Int?, text: String, words: List<Word>? = null) = Line().apply {
        this.start = start
        value = text
        this.words = words
    }

    private fun word(text: String, start: Int, end: Int) = Word().apply {
        this.text = text
        this.start = start
        this.end = end
    }

    private fun build(vararg lines: Line, duration: Long = -1) = LyricsTimeline.build(lines.toList(), duration, noSecondary)

    private fun item(start: Long, duration: Long, text: String = "x") = LyricItem(start, duration, text)

    @Test
    fun eachLineLastsUntilTheNext() {
        val items = build(line(1000, "one"), line(3000, "two"), line(6500, "three"))

        assertEquals(listOf(1000L, 3000L, 6500L), items.map { it.startMs })
        assertEquals(listOf(2000L, 3500L), items.take(2).map { it.durationMs })
        assertTrue(items.none { it.isInstrumental })
    }

    @Test
    fun theLastLineLastsFiveSecondsOrUntilTheTrackEnds() {
        assertEquals(5000L, build(line(1000, "only")).single().durationMs)
        assertEquals(2000L, build(line(1000, "only"), duration = 3000).single().durationMs)
        assertEquals(5000L, build(line(1000, "only"), duration = 60_000).single().durationMs)
    }

    @Test
    fun aLongIntroGetsABreakBeforeTheFirstLine() {
        val items = build(line(12_000, "late start"))

        assertTrue(items[0].isInstrumental)
        assertEquals(0L, items[0].startMs)
        assertEquals(12_000L, items[0].durationMs)
        assertEquals("late start", items[1].text)
    }

    @Test
    fun aShortIntroGetsNone() {
        assertFalse(build(line(3_999, "soon")).first().isInstrumental)
    }

    @Test
    fun aTimedEmptyLineIsABreakWhenLongEnough() {
        val items = build(line(0, "sing"), line(2000, ""), line(9000, "again"))

        assertEquals(listOf(false, true, false), items.map { it.isInstrumental })
        assertEquals(2000L, items[1].startMs)
        assertEquals(7000L, items[1].durationMs)
    }

    @Test
    fun aShortEmptyLineIsDropped() {
        val items = build(line(0, "sing"), line(2000, " "), line(3000, "again"))

        assertEquals(listOf("sing", "again"), items.map { it.text })
    }

    @Test
    fun aLongSilenceAfterTheLastSungWordIsABreak() {
        val items = build(
            line(1000, "hold on", listOf(word("hold", 1000, 1500), word("on", 1500, 2000))),
            line(8000, "again"),
        )

        assertEquals(listOf(false, true, false), items.map { it.isInstrumental })
        assertEquals(1000L, items[0].durationMs)
        assertEquals(2000L, items[1].startMs)
        assertEquals(6000L, items[1].durationMs)
    }

    @Test
    fun aShortSilenceAfterTheWordsIsNot() {
        val items = build(line(1000, "hi", listOf(word("hi", 1000, 1500))), line(4000, "there"))

        assertEquals(2, items.size)
        assertEquals(3000L, items[0].durationMs)
    }

    @Test
    fun linesWithoutATimeAreSkipped() {
        assertEquals(listOf("a", "b"), build(line(0, "a"), line(null, "lost"), line(2000, "b")).map { it.text })
    }

    @Test
    fun wordsAreJoinedAsTheParserJoinsThem() {
        val item = build(
            line(0, "ignored", listOf(word("Rock", 0, 100), word("n-", 100, 200), word("roll", 200, 300), word(" baby", 300, 400))),
        ).single()

        assertEquals("Rock n-roll baby", item.text)
        assertEquals(listOf(0 to 4, 5 to 7, 7 to 11, 11 to 16), item.words.map { it.from to it.to })
    }

    @Test
    fun theSecondaryTextIsAskedForWithTheSourceIndex() {
        val asked = mutableListOf<Pair<Int, String>>()
        val items = LyricsTimeline.build(
            listOf(line(0, "a"), line(null, "lost"), line(2000, "b")), -1,
        ) { i, text -> asked += i to text; "#$i" }

        assertEquals(listOf(0 to "a", 2 to "b"), asked)
        assertEquals(listOf("#0", "#2"), items.map { it.secondary })
    }

    @Test
    fun plainLyricsLeaveOutEmptyLinesButKeepTheirIndexes() {
        val items = LyricsTimeline.plain(listOf("one", "", "  three  ")) { i, _ -> "t$i" }

        assertEquals(listOf("one", "three"), items.map { it.text })
        assertEquals(listOf("t0", "t2"), items.map { it.secondary })
    }

    @Test
    fun findsTheLineBeingSung() {
        val items = listOf(item(1000, 1000), item(2000, 1000), item(3000, 1000))

        assertEquals(0, LyricsTimeline.indexAt(items, 0))
        assertEquals(0, LyricsTimeline.indexAt(items, 1999))
        assertEquals(1, LyricsTimeline.indexAt(items, 2000))
        assertEquals(2, LyricsTimeline.indexAt(items, 99_000))
        assertEquals(0, LyricsTimeline.indexAt(emptyList(), 5))
    }

    @Test
    fun progressRunsFromZeroToOne() {
        val item = item(1000, 2000)

        assertEquals(0f, LyricsTimeline.progress(item, 500), 0f)
        assertEquals(0.5f, LyricsTimeline.progress(item, 2000), 0.001f)
        assertEquals(1f, LyricsTimeline.progress(item, 9000), 0f)
    }

    @Test
    fun withoutWordsTheFillMovesEvenly() {
        val item = item(0, 1000, "abcdefghij")

        assertEquals(5f, LyricsTimeline.sungChars(item, 500), 0.001f)
        assertEquals(10f, LyricsTimeline.sungChars(item, 1000), 0.001f)
    }

    @Test
    fun withWordsTheFillFollowsTheSinging() {
        // "hold on": "hold" from 0 to 1000, then nothing until "on" from 3000 to 3500.
        val item = build(
            line(0, "hold on", listOf(word("hold", 0, 1000), word("on", 3000, 3500))),
            line(3600, "next"),
        ).first()

        assertEquals(0f, LyricsTimeline.sungChars(item, 0), 0.001f)
        assertEquals(2f, LyricsTimeline.sungChars(item, 500), 0.001f)
        assertEquals("waits between words", 4f, LyricsTimeline.sungChars(item, 2000), 0.001f)
        assertEquals(6f, LyricsTimeline.sungChars(item, 3250), 0.001f)
        assertEquals(7f, LyricsTimeline.sungChars(item, 3500), 0.001f)
    }

    @Test
    fun instrumentalBreaksHaveNoText() {
        val gap = build(line(10_000, "x")).first()

        assertTrue(gap.isInstrumental)
        assertEquals("", gap.text)
        assertNull(gap.secondary)
    }
}
