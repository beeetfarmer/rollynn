package com.cappielloantonio.tempo.util

import android.os.Build
import com.atilika.kuromoji.ipadic.Tokenizer

object LyricsRomanizer {

    private val kuromojiTokenizer: Tokenizer? by lazy {
        try {
            Tokenizer()
        } catch (_: Exception) {
            null
        }
    }

    private val KANA_ROMAJI_MAP: Map<String, String> = mapOf(
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ヂャ" to "ja", "ヂュ" to "ju", "ヂョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "o", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ズ" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
        "ー" to ""
    )

    private val HANGUL_CHO = mapOf(
        0x1100 to "g", 0x1101 to "kk", 0x1102 to "n", 0x1103 to "d",
        0x1104 to "tt", 0x1105 to "r", 0x1106 to "m", 0x1107 to "b",
        0x1108 to "pp", 0x1109 to "s", 0x110A to "ss", 0x110B to "",
        0x110C to "j", 0x110D to "jj", 0x110E to "ch", 0x110F to "k",
        0x1110 to "t", 0x1111 to "p", 0x1112 to "h"
    )

    private val HANGUL_JUNG = mapOf(
        0x1161 to "a", 0x1162 to "ae", 0x1163 to "ya", 0x1164 to "yae",
        0x1165 to "eo", 0x1166 to "e", 0x1167 to "yeo", 0x1168 to "ye",
        0x1169 to "o", 0x116A to "wa", 0x116B to "wae", 0x116C to "oe",
        0x116D to "yo", 0x116E to "u", 0x116F to "wo", 0x1170 to "we",
        0x1171 to "wi", 0x1172 to "yu", 0x1173 to "eu", 0x1174 to "eui",
        0x1175 to "i"
    )

    private val HANGUL_JONG = mapOf(
        0x11A8 to "k", 0x11A9 to "kk", 0x11AB to "n", 0x11AE to "t",
        0x11AF to "l", 0x11B7 to "m", 0x11B8 to "p", 0x11BA to "t",
        0x11BB to "t", 0x11BC to "ng", 0x11BD to "t", 0x11BE to "k",
        0x11BF to "k", 0x11C0 to "t", 0x11C1 to "p", 0x11C2 to "t"
    )

    private val DEVANAGARI_MAP: Map<String, String> = mapOf(
        "अ" to "a", "आ" to "aa", "इ" to "i", "ई" to "ee", "उ" to "u", "ऊ" to "oo",
        "ऋ" to "ri", "ए" to "e", "ऐ" to "ai", "ओ" to "o", "औ" to "au",
        "क" to "k", "ख" to "kh", "ग" to "g", "घ" to "gh", "ङ" to "ng",
        "च" to "ch", "छ" to "chh", "ज" to "j", "झ" to "jh", "ञ" to "ny",
        "ट" to "t", "ठ" to "th", "ड" to "d", "ढ" to "dh", "ण" to "n",
        "त" to "t", "थ" to "th", "द" to "d", "ध" to "dh", "न" to "n",
        "प" to "p", "फ" to "ph", "ब" to "b", "भ" to "bh", "म" to "m",
        "य" to "y", "र" to "r", "ल" to "l", "व" to "v",
        "श" to "sh", "ष" to "sh", "स" to "s", "ह" to "h",
        "क्ष" to "ksh", "त्र" to "tr", "ज्ञ" to "gy", "श्र" to "shr",
        "ा" to "aa", "ि" to "i", "ी" to "ee", "ु" to "u", "ू" to "oo",
        "ृ" to "ri", "े" to "e", "ै" to "ai", "ो" to "o", "ौ" to "au",
        "ं" to "n", "ः" to "h", "ँ" to "n", "़" to "", "्" to "",
        "क़" to "q", "ख़" to "kh", "ग़" to "g", "ज़" to "z",
        "ड़" to "r", "ढ़" to "rh", "फ़" to "f", "य़" to "y"
    )

    private val CYRILLIC_MAP: Map<String, String> = mapOf(
        "А" to "A", "Б" to "B", "В" to "V", "Г" to "G", "Ґ" to "G", "Д" to "D",
        "Е" to "E", "Ё" to "Yo", "Є" to "Ye", "Ж" to "Zh", "З" to "Z",
        "И" to "I", "І" to "I", "Ї" to "Yi", "Й" to "Y", "К" to "K",
        "Л" to "L", "М" to "M", "Н" to "N", "О" to "O", "П" to "P",
        "Р" to "R", "С" to "S", "Т" to "T", "У" to "U", "Ф" to "F",
        "Х" to "Kh", "Ц" to "Ts", "Ч" to "Ch", "Ш" to "Sh", "Щ" to "Shch",
        "Ъ" to "", "Ы" to "Y", "Ь" to "", "Э" to "E", "Ю" to "Yu", "Я" to "Ya",
        "а" to "a", "б" to "b", "в" to "v", "г" to "g", "ґ" to "g", "д" to "d",
        "е" to "e", "ё" to "yo", "є" to "ye", "ж" to "zh", "з" to "z",
        "и" to "i", "і" to "i", "ї" to "yi", "й" to "y", "к" to "k",
        "л" to "l", "м" to "m", "н" to "n", "о" to "o", "п" to "p",
        "р" to "r", "с" to "s", "т" to "t", "у" to "u", "ф" to "f",
        "х" to "kh", "ц" to "ts", "ч" to "ch", "ш" to "sh", "щ" to "shch",
        "ъ" to "", "ы" to "y", "ь" to "", "э" to "e", "ю" to "yu", "я" to "ya",
        "Ў" to "W", "ў" to "w", "Ђ" to "Dj", "ђ" to "dj",
        "Ћ" to "C", "ћ" to "c", "Џ" to "Dzh", "џ" to "dzh",
        "Љ" to "Lj", "љ" to "lj", "Њ" to "Nj", "њ" to "nj",
        "Ј" to "J", "ј" to "j", "Ң" to "Ng", "ң" to "ng",
        "Ө" to "O", "ө" to "o", "Ү" to "U", "ү" to "u",
        "Ғ" to "Gh", "ғ" to "gh", "Қ" to "Q", "қ" to "q",
        "Һ" to "H", "һ" to "h", "Ѕ" to "Dz", "ѕ" to "dz",
        "Ѓ" to "Gj", "ѓ" to "gj", "Ќ" to "Kj", "ќ" to "kj"
    )

    @JvmStatic
    fun isJapanese(text: String): Boolean {
        return text.any { it in '぀'..'ゟ' || it in '゠'..'ヿ' || it in '一'..'鿿' }
    }

    @JvmStatic
    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false
        val cjk = text.count { it in '一'..'鿿' }
        val kana = text.count { it in '぀'..'ゟ' || it in '゠'..'ヿ' }
        return cjk > 0 && kana.toDouble() / text.length < 0.1
    }

    @JvmStatic
    fun isKorean(text: String): Boolean = text.any { it in '가'..'힣' }

    @JvmStatic
    fun isHindi(text: String): Boolean = text.any { it in 'ऀ'..'ॿ' }

    @JvmStatic
    fun isCyrillic(text: String): Boolean {
        val count = text.count { it in 'Ѐ'..'ӿ' }
        return count >= 2
    }

    @JvmStatic
    fun needsRomanization(text: String): Boolean {
        return isJapanese(text) || isChinese(text) || isKorean(text) || isHindi(text) || isCyrillic(text)
    }

    @JvmStatic
    fun romanize(text: String): String? {
        if (text.isBlank()) return null
        return when {
            isJapanese(text) && !isChinese(text) -> romanizeJapanese(text)
            isChinese(text) -> romanizeChinese(text)
            isKorean(text) -> romanizeKorean(text)
            isHindi(text) -> romanizeHindi(text)
            isCyrillic(text) -> romanizeCyrillic(text)
            else -> null
        }
    }

    @JvmStatic
    fun romanizeJapanese(text: String): String {
        val tokenizer = kuromojiTokenizer ?: return text
        val tokens = tokenizer.tokenize(text)
        return tokens.joinToString(" ") { token ->
            val reading = if (token.reading.isNullOrEmpty() || token.reading == "*") {
                token.surface
            } else {
                token.reading
            }
            katakanaToRomaji(reading)
        }
    }

    private fun katakanaToRomaji(katakana: String): String {
        if (katakana.isEmpty()) return ""
        val sb = StringBuilder(katakana.length)
        var i = 0
        while (i < katakana.length) {
            if (i + 1 < katakana.length) {
                val two = katakana.substring(i, i + 2)
                val mapped = KANA_ROMAJI_MAP[two]
                if (mapped != null) {
                    sb.append(mapped)
                    i += 2
                    continue
                }
            }
            if (katakana[i] == 'ッ') {
                if (i + 1 < katakana.length) {
                    val next = KANA_ROMAJI_MAP[katakana[i + 1].toString()]
                    if (next != null && next.isNotEmpty()) sb.append(next[0])
                }
                i++
                continue
            }
            val one = katakana[i].toString()
            sb.append(KANA_ROMAJI_MAP[one] ?: one)
            i++
        }
        return sb.toString().lowercase()
    }

    @JvmStatic
    fun romanizeKorean(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            if (ch in '가'..'힣') {
                val idx = ch.code - 0xAC00
                val cho = idx / (21 * 28)
                val jung = (idx % (21 * 28)) / 28
                val jong = idx % 28
                sb.append(HANGUL_CHO[0x1100 + cho] ?: "")
                sb.append(HANGUL_JUNG[0x1161 + jung] ?: "")
                if (jong != 0) sb.append(HANGUL_JONG[0x11A7 + jong] ?: "")
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    @JvmStatic
    fun romanizeChinese(text: String): String {
        if (text.isEmpty()) return ""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val transliterator = android.icu.text.Transliterator.getInstance("Han-Latin/Names; Latin-ASCII")
                return transliterator.transliterate(text)
            } catch (_: Exception) { }
        }
        return text
    }

    @JvmStatic
    fun romanizeHindi(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (i + 1 < text.length) {
                val two = text.substring(i, i + 2)
                val mapped = DEVANAGARI_MAP[two]
                if (mapped != null) {
                    sb.append(mapped)
                    i += 2
                    continue
                }
            }
            sb.append(DEVANAGARI_MAP[text[i].toString()] ?: text[i])
            i++
        }
        return sb.toString()
    }

    @JvmStatic
    fun romanizeCyrillic(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            sb.append(CYRILLIC_MAP[ch.toString()] ?: ch)
        }
        return sb.toString()
    }
}
