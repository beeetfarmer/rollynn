package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.subsonic.models.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TTMLParserTest {
    private fun ttml(body: String) = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <head/><body>$body</body>
        </tt>
    """.trimIndent()

    private fun lines(body: String): List<Line> =
        TTMLParser.parse(ttml(body))!!.structuredLyrics!!.single().line!!

    private fun word(text: String, begin: String = "1", end: String = "2", role: String? = null) =
        """<span begin="$begin" end="$end"${role?.let { " ttm:role=\"$it\"" } ?: ""}>$text</span>"""

    @Test
    fun marksLyricsAsSyncedWithNoOffset() {
        val lyrics = TTMLParser.parse(ttml("""<p begin="1">Hello</p>"""))!!.structuredLyrics!!.single()
        assertEquals(true, lyrics.synced)
        assertEquals(0, lyrics.offset)
    }

    @Test
    fun parsesEveryTimeFormat() {
        val starts = lines(
            """
            <p begin="12.5">seconds</p>
            <p begin="1500ms">milliseconds</p>
            <p begin="1:02.500">minutes</p>
            <p begin="1:00:01.5">hours</p>
            """
        ).map { it.start }
        assertEquals(listOf(12500, 1500, 62500, 3601500), starts)
    }

    @Test
    fun aMalformedTimeStartsAtZero() {
        assertEquals(0, lines("""<p begin="soon">Hello</p>""").single().start)
    }

    @Test
    fun keepsPlainLinesWithoutWordTiming() {
        val line = lines("""<p begin="1">  Hello world  </p>""").single()
        assertEquals("Hello world", line.value)
        assertNull(line.words)
    }

    @Test
    fun findsLinesInsideDivs() {
        val values = lines("""<div><p begin="1">one</p></div><div><div><p begin="2">two</p></div></div>""")
            .map { it.value }
        assertEquals(listOf("one", "two"), values)
    }

    @Test
    fun skipsLinesWithNoText() {
        val values = lines("""<p begin="1">  </p><p begin="2">kept</p>""").map { it.value }
        assertEquals(listOf("kept"), values)
    }

    @Test
    fun skipsLinesWithNoTimingAnywhere() {
        val values = lines("""<p>untimed</p><p begin="2">kept</p>""").map { it.value }
        assertEquals(listOf("kept"), values)
    }

    @Test
    fun takesWordTimings() {
        val line = lines("""<p begin="1">${word("Hello", "1", "1.5")}${word("world", "1.5", "2.25")}</p>""").single()
        val words = line.words!!
        assertEquals(listOf("Hello", "world"), words.map { it.text })
        assertEquals(listOf(1000, 1500), words.map { it.start })
        assertEquals(listOf(1500, 2250), words.map { it.end })
    }

    @Test
    fun usesTheFirstTimedSpanWhenTheLineHasNoBegin() {
        val line = lines("""<p><span>skip</span>${word("Hello", "3", "4")}</p>""").single()
        assertEquals(3000, line.start)
    }

    @Test
    fun joinsWordsWithSingleSpaces() {
        assertEquals("Hello world", lines("""<p begin="1">${word("Hello")}${word("world")}</p>""").single().value)
        assertEquals("Hello world", lines("""<p begin="1">${word("Hello ")}${word("world")}</p>""").single().value)
        assertEquals("Hello world", lines("""<p begin="1">${word("Hello")}${word(" world")}</p>""").single().value)
    }

    @Test
    fun doesNotSplitAHyphenatedWord() {
        assertEquals("rock-steady", lines("""<p begin="1">${word("rock-")}${word("steady")}</p>""").single().value)
    }

    @Test
    fun readsWordsNestedInAGroupingSpan() {
        val line = lines("""<p begin="1"><span>${word("Hello")}${word("world")}</span></p>""").single()
        assertEquals(listOf("Hello", "world"), line.words!!.map { it.text })
    }

    @Test
    fun leavesOutTranslationRomanisationAndBackgroundVocals() {
        val line = lines(
            """
            <p begin="1">
              ${word("Hola")}
              ${word("Hello", role = "x-translation")}
              ${word("Ola", role = "x-roman")}
              ${word("ooh", role = "x-bg")}
            </p>
            """
        ).single()
        assertEquals("Hola", line.value)
    }

    @Test
    fun leavesTranslationsOutOfPlainLines() {
        val line = lines("""<p begin="1">Hola<span ttm:role="x-translation">Hello</span></p>""").single()
        assertEquals("Hola", line.value)
    }

    @Test
    fun returnsNullWhenThereIsNothingToShow() {
        assertNull(TTMLParser.parse("not xml"))
        assertNull(TTMLParser.parse(""))
        assertNull(TTMLParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><head/></tt>"""))
        assertNull(TTMLParser.parse(ttml("")))
    }

    @Test
    fun doesNotReadExternalEntities() {
        val secret = File.createTempFile("ttml", ".txt").apply {
            writeText("secret")
            deleteOnExit()
        }
        val xml = """
            <!DOCTYPE tt [<!ENTITY leak SYSTEM "${secret.toURI()}">]>
            <tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1">a&leak;b</p></body></tt>
        """.trimIndent()

        val text = TTMLParser.parse(xml)?.structuredLyrics?.single()?.line?.single()?.value.orEmpty()
        assertFalse(text, text.contains("secret"))
    }
}
