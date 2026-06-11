package com.cappielloantonio.tempo.util

import com.cappielloantonio.tempo.subsonic.models.Line
import com.cappielloantonio.tempo.subsonic.models.LyricsList
import com.cappielloantonio.tempo.subsonic.models.StructuredLyrics
import com.cappielloantonio.tempo.subsonic.models.Word
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

object TTMLParser {

    fun parse(ttml: String): LyricsList? {
        val lines = parseTTML(ttml)
        if (lines.isEmpty()) return null

        val structured = StructuredLyrics()
        structured.synced = true
        structured.offset = 0
        structured.line = lines

        val lyricsList = LyricsList()
        lyricsList.structuredLyrics = listOf(structured)
        return lyricsList
    }

    private fun parseTTML(ttml: String): List<Line> {
        val lines = mutableListOf<Line>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }

            val doc = factory.newDocumentBuilder().parse(ttml.byteInputStream())
            val body = findChild(doc.documentElement, "body") ?: return emptyList()
            walkElement(body, lines)
        } catch (_: Exception) {
            return emptyList()
        }
        return lines
    }

    private fun walkElement(element: Element, lines: MutableList<Line>) {
        val name = element.localName ?: element.nodeName.substringAfterLast(':')
        if (name == "p") {
            parseP(element, lines)
            return
        }
        var child = element.firstChild
        while (child != null) {
            if (child is Element) walkElement(child, lines)
            child = child.nextSibling
        }
    }

    private fun parseP(p: Element, lines: MutableList<Line>) {
        var begin = p.getAttribute("begin")
        if (begin.isEmpty()) begin = findFirstSpanBegin(p) ?: return
        val startMs = (parseTime(begin) * 1000).toInt()

        val words = mutableListOf<Word>()
        var child = p.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    val role = getRole(child)
                    if (role != "x-translation" && role != "x-roman" && role != "x-bg") {
                        parseSpanWords(child, words)
                    }
                }
            }
            child = child.nextSibling
        }

        val lineText = if (words.isNotEmpty()) {
            buildWordText(words)
        } else {
            getDirectText(p).trim()
        }

        if (lineText.isNotEmpty()) {
            val line = Line()
            line.start = startMs
            line.value = lineText
            if (words.isNotEmpty()) line.words = words
            lines.add(line)
        }
    }

    private fun parseSpanWords(span: Element, words: MutableList<Word>) {
        val childSpans = mutableListOf<Element>()
        var child = span.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") childSpans.add(child)
            }
            child = child.nextSibling
        }

        if (childSpans.isNotEmpty()) {
            for (cs in childSpans) {
                val role = getRole(cs)
                if (role != "x-translation" && role != "x-roman") {
                    addWordFromSpan(cs, words)
                }
            }
        } else {
            addWordFromSpan(span, words)
        }
    }

    private fun addWordFromSpan(span: Element, words: MutableList<Word>) {
        val beginStr = timingAttr(span, "begin")
        val endStr = timingAttr(span, "end")
        val text = span.textContent ?: ""
        if (beginStr.isEmpty() || endStr.isEmpty() || text.isBlank()) return

        val word = Word()
        word.start = (parseTime(beginStr) * 1000).toInt()
        word.end = (parseTime(endStr) * 1000).toInt()
        word.text = text
        words.add(word)
    }

    private fun buildWordText(words: List<Word>): String {
        val sb = StringBuilder()
        for (i in words.indices) {
            val text = words[i].text
            sb.append(text)
            if (i < words.lastIndex && !text.endsWith(' ') && !text.endsWith('-')) {
                val nextText = words[i + 1].text
                if (!nextText.startsWith(' ')) {
                    sb.append(' ')
                }
            }
        }
        return sb.toString().trim()
    }

    private fun findChild(parent: Element, localName: String): Element? {
        var child = parent.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == localName) return child
            }
            child = child.nextSibling
        }
        return null
    }

    private fun findFirstSpanBegin(p: Element): String? {
        var child = p.firstChild
        while (child != null) {
            if (child is Element) {
                val name = child.localName ?: child.nodeName.substringAfterLast(':')
                if (name == "span") {
                    val begin = timingAttr(child, "begin")
                    if (begin.isNotEmpty()) return begin
                }
            }
            child = child.nextSibling
        }
        return null
    }

    private fun timingAttr(el: Element, localName: String): String {
        val direct = el.getAttribute(localName)
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS("http://www.w3.org/ns/ttml#parameter", localName)
    }

    private fun getRole(el: Element): String {
        val ttm = el.getAttribute("ttm:role")
        if (ttm.isNotEmpty()) return ttm
        val direct = el.getAttribute("role")
        if (direct.isNotEmpty()) return direct
        return el.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role")
    }

    private fun getDirectText(el: Element): String {
        val sb = StringBuilder()
        var child = el.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                sb.append(child.textContent)
            } else if (child is Element) {
                val role = getRole(child)
                if (role != "x-translation" && role != "x-roman") {
                    sb.append(child.textContent)
                }
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun parseTime(time: String): Double {
        val t = time.trim()
        val c1 = t.indexOf(':')
        if (c1 != -1) {
            val c2 = t.lastIndexOf(':')
            return if (c1 == c2) {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 60.0 +
                        (t.substring(c1 + 1).toDoubleOrNull() ?: 0.0)
            } else {
                (t.substring(0, c1).toIntOrNull() ?: 0) * 3600.0 +
                        (t.substring(c1 + 1, c2).toIntOrNull() ?: 0) * 60.0 +
                        (t.substring(c2 + 1).toDoubleOrNull() ?: 0.0)
            }
        }
        if (t.endsWith("ms")) return (t.substring(0, t.length - 2).toDoubleOrNull() ?: 0.0) / 1000.0
        return t.toDoubleOrNull() ?: 0.0
    }
}
