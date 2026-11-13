package com.cappielloantonio.tempo.util

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Keeps the Simplified Chinese strings complete and safe to format. */
class ChineseTranslationTest {
    private val res = File("src/main/res")

    /** name -> text (for plurals, the "other" item) of every translatable entry. */
    private fun strings(dir: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(res, "$dir/strings.xml"))
        val out = mutableMapOf<String, String>()
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            if (e.getAttribute("translatable") == "false") continue
            val text = when (e.tagName) {
                "string" -> e.textContent
                "plurals" -> {
                    val items = e.getElementsByTagName("item")
                    (0 until items.length).map { items.item(it) as Element }
                        .firstOrNull { it.getAttribute("quantity") == "other" }?.textContent ?: ""
                }
                else -> continue
            }
            out[e.getAttribute("name")] = text
        }
        return out
    }

    private val placeholder = Regex("""%(\d+\$)?[sdf]""")
    private fun placeholders(s: String) = placeholder.findAll(s).map { it.value }.sorted().toList()

    @Test
    fun everyEnglishStringHasAChineseTranslation() {
        val missing = strings("values").keys - strings("values-zh").keys
        assertTrue("Missing from values-zh/strings.xml: $missing", missing.isEmpty())
    }

    @Test
    fun noChineseStringIsLeftOverFromARemovedEnglishOne() {
        val stale = strings("values-zh").keys - strings("values").keys
        assertTrue("Not in values/strings.xml: $stale", stale.isEmpty())
    }

    @Test
    fun chinesePlaceholdersMatchEnglish() {
        val en = strings("values")
        val mismatched = strings("values-zh").filter { (name, zh) ->
            en[name] != null && placeholders(en.getValue(name)) != placeholders(zh)
        }.keys
        assertTrue("Placeholders differ from English in: $mismatched", mismatched.isEmpty())
    }

    @Test
    fun layoutsAndSettingsHaveNoHardcodedText() {
        // Brand names are the same in every language.
        val allowed = setOf("Last.fm")
        val attr = Regex("""(?:android|app):(?:title|summary|text|hint|contentDescription|dialogTitle)="([^"]*)"""")
        val hardcoded = res.listFiles()!!
            .filter { it.isDirectory && (it.name.startsWith("layout") || it.name == "menu" || it.name == "xml") }
            .flatMap { it.listFiles()!!.toList() }
            .flatMap { file ->
                attr.findAll(file.readText()).map { it.groupValues[1] }
                    .filter { !it.startsWith("@") && !it.startsWith("?") && Regex("[A-Za-z]{2}").containsMatchIn(it) }
                    .filter { it !in allowed }
                    .map { "${file.parentFile.name}/${file.name}: $it" }
            }
        assertTrue("Use string resources instead of: $hardcoded", hardcoded.isEmpty())
    }

    @Test
    fun placeholderExtractionSeesPositionalAndPercentEscapes() {
        assertTrue(placeholders("Mark when %1\$d%% is reached") == listOf("%1\$d"))
        assertTrue(placeholders("你的 %2\$d 年 %1\$s 回顾") == listOf("%1\$s", "%2\$d"))
    }
}
