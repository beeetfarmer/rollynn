package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.subsonic.models.Line
import kotlin.math.max
import kotlin.math.min

/** A timed word, as the characters [from] until [to] of its line's text. */
data class TimedWord(val startMs: Long, val endMs: Long, val from: Int, val to: Int)

/** One row of the lyrics view: a sung line, or an instrumental break between lines. */
data class LyricItem(
    val startMs: Long,
    val durationMs: Long,
    val text: String,
    /** Smaller text under the line: background vocals, romanization, translation. */
    val secondary: String? = null,
    val words: List<TimedWord> = emptyList(),
    val isInstrumental: Boolean = false,
)

/** Turns lyric lines into what the lyrics view shows, and where playback is in them. */
object LyricsTimeline {
    /** A pause at least this long between lines shows as an instrumental break. */
    const val INSTRUMENTAL_GAP_MS = 4_000L

    /** How long the last line lasts when nothing says when it ends. */
    const val LAST_LINE_MS = 5_000L

    fun interface Secondary {
        /** The smaller text for the line at [index] in the source list, if any. */
        fun of(index: Int, text: String): String?
    }

    /** Synced lines, with breaks for long pauses. [trackDurationMs] ≤ 0 means unknown. */
    @JvmStatic
    fun build(lines: List<Line>, trackDurationMs: Long, secondary: Secondary): List<LyricItem> {
        val out = ArrayList<LyricItem>()
        for (i in lines.indices) {
            val line = lines[i]
            val start = line.start?.toLong() ?: continue
            val next = nextStart(lines, i)
            val (text, words) = textAndWords(line)

            // A timed empty line marks a pause.
            if (text.isEmpty()) {
                val end = next ?: (start + LAST_LINE_MS)
                if (end - start >= INSTRUMENTAL_GAP_MS) out += instrumental(start, end)
                continue
            }

            if (out.isEmpty() && start >= INSTRUMENTAL_GAP_MS) out += instrumental(0, start)

            val sungEnd = words.lastOrNull()?.endMs
            val secondaryText = secondary.of(i, text)
            if (sungEnd != null && next != null && next - sungEnd >= INSTRUMENTAL_GAP_MS) {
                out += LyricItem(start, max(1, sungEnd - start), text, secondaryText, words)
                out += instrumental(sungEnd, next)
            } else {
                val end = next ?: sungEnd ?: lastLineEnd(start, trackDurationMs)
                out += LyricItem(start, max(1, end - start), text, secondaryText, words)
            }
        }
        return out
    }

    /** Unsynced lines, without timing; empty lines are left out. */
    @JvmStatic
    fun plain(lines: List<String>, secondary: Secondary): List<LyricItem> =
        lines.mapIndexedNotNull { i, raw ->
            val text = raw.trim()
            if (text.isEmpty()) null else LyricItem(0, 0, text, secondary.of(i, text))
        }

    /** The item being sung at [positionMs]: the last one that has started, or the first. */
    @JvmStatic
    fun indexAt(items: List<LyricItem>, positionMs: Long): Int {
        var lo = 0
        var hi = items.size - 1
        var found = 0
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (items[mid].startMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return found
    }

    /** How far through [item] playback is, from 0 to 1. */
    @JvmStatic
    fun progress(item: LyricItem, positionMs: Long): Float =
        ((positionMs - item.startMs).toFloat() / max(1, item.durationMs)).coerceIn(0f, 1f)

    /**
     * How many characters of [item]'s text have been sung, fractions included. Word timings are
     * followed when there are any, so the fill keeps to the singing; otherwise it moves evenly.
     */
    @JvmStatic
    fun sungChars(item: LyricItem, positionMs: Long): Float {
        if (item.words.isEmpty()) return progress(item, positionMs) * item.text.length
        var chars = 0f
        for (word in item.words) {
            when {
                positionMs >= word.endMs -> chars = word.to.toFloat()
                positionMs > word.startMs -> {
                    val f = (positionMs - word.startMs).toFloat() / max(1, word.endMs - word.startMs)
                    return word.from + (word.to - word.from) * f
                }
                else -> return chars
            }
        }
        return chars
    }

    private fun nextStart(lines: List<Line>, i: Int): Long? {
        for (j in i + 1 until lines.size) lines[j].start?.let { return it.toLong() }
        return null
    }

    private fun lastLineEnd(start: Long, trackDurationMs: Long): Long =
        if (trackDurationMs > start) min(trackDurationMs, start + LAST_LINE_MS) else start + LAST_LINE_MS

    private fun instrumental(start: Long, end: Long) = LyricItem(start, end - start, "", isInstrumental = true)

    /** The line's text and where each timed word sits in it, joined as the TTML parser does. */
    private fun textAndWords(line: Line): Pair<String, List<TimedWord>> {
        val source = line.words
        if (source.isNullOrEmpty()) return line.value.trim() to emptyList()

        val sb = StringBuilder()
        val words = ArrayList<TimedWord>(source.size)
        for (i in source.indices) {
            val text = source[i].text
            val from = sb.length
            sb.append(text)
            words += TimedWord(source[i].start.toLong(), source[i].end.toLong(), from, sb.length)
            if (i < source.lastIndex && !text.endsWith(' ') && !text.endsWith('-') && !source[i + 1].text.startsWith(' ')) {
                sb.append(' ')
            }
        }
        val lead = sb.length - sb.trimStart().length
        val text = sb.toString().trim()
        return text to words.map {
            TimedWord(it.startMs, it.endMs, (it.from - lead).coerceIn(0, text.length), (it.to - lead).coerceIn(0, text.length))
        }
    }
}
