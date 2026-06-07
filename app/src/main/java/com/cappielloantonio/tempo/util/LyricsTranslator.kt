package com.cappielloantonio.tempo.util

import android.os.Handler
import android.os.Looper
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LyricsTranslator {

    interface Callback {
        fun onSuccess(translatedLines: List<String>)
        fun onError(message: String)
    }

    private val client = OkHttpClient.Builder()
        .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun translate(lines: List<String>, callback: Callback) {
        val provider = Preferences.getTranslationProvider()
        val apiKey = Preferences.getTranslationApiKey()
        val targetLanguage = Preferences.getTranslationTargetLanguage()

        if (apiKey.isNullOrBlank()) {
            callback.onError("API key not configured. Set it in Settings > Playback > Lyrics Translation.")
            return
        }

        if (targetLanguage.isNullOrBlank()) {
            callback.onError("Target language not configured.")
            return
        }

        Thread {
            try {
                val result = when (provider) {
                    "deepl" -> translateWithDeepL(lines, targetLanguage, apiKey)
                    else -> translateWithOpenAI(lines, targetLanguage, apiKey, provider)
                }
                mainHandler.post { callback.onSuccess(result) }
            } catch (e: Exception) {
                val msg = e.message ?: "Translation failed"
                mainHandler.post { callback.onError(msg) }
            }
        }.start()
    }

    private fun translateWithDeepL(
        lines: List<String>,
        targetLanguage: String,
        apiKey: String
    ): List<String> {
        val deeplLang = when (targetLanguage.lowercase()) {
            "zh" -> "ZH"
            "en" -> "EN-US"
            "pt" -> "PT-PT"
            else -> targetLanguage.uppercase().take(2)
        }

        val baseUrl = if (apiKey.endsWith(":fx"))
            "https://api-free.deepl.com/v2/translate"
        else
            "https://api.deepl.com/v2/translate"

        val textArray = JSONArray()
        lines.forEach { textArray.put(it) }

        val body = JSONObject().apply {
            put("text", textArray)
            put("target_lang", deeplLang)
            put("preserve_formatting", true)
        }

        val request = Request.Builder()
            .url(baseUrl)
            .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.trim()}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseBody ?: "").optString("message")
            } catch (_: Exception) { null }
            throw Exception(errorMsg ?: "DeepL error: HTTP ${response.code}")
        }

        val json = JSONObject(responseBody ?: throw Exception("Empty response"))
        val translations = json.optJSONArray("translations")
            ?: throw Exception("No translations in response")

        val result = mutableListOf<String>()
        for (i in 0 until translations.length()) {
            result.add(translations.getJSONObject(i).optString("text", ""))
        }

        while (result.size < lines.size) result.add("")
        return result.take(lines.size)
    }

    private fun translateWithOpenAI(
        lines: List<String>,
        targetLanguage: String,
        apiKey: String,
        provider: String
    ): List<String> {
        val model = Preferences.getTranslationModel()
        val customUrl = Preferences.getTranslationApiUrl()

        val apiUrl = when (provider) {
            "mistral" -> "https://api.mistral.ai/v1/chat/completions"
            "openrouter" -> customUrl?.takeIf { it.isNotBlank() }
                ?: "https://openrouter.ai/api/v1/chat/completions"
            else -> customUrl?.takeIf { it.isNotBlank() }
                ?: "https://openrouter.ai/api/v1/chat/completions"
        }

        val defaultModel = when (provider) {
            "mistral" -> "mistral-small-latest"
            else -> ""
        }

        val lineCount = lines.size
        val fullText = lines.joinToString("\n")

        val languageNames = mapOf(
            "en" to "English", "es" to "Spanish", "fr" to "French",
            "de" to "German", "it" to "Italian", "pt" to "Portuguese",
            "ru" to "Russian", "ja" to "Japanese", "ko" to "Korean",
            "zh" to "Chinese", "ar" to "Arabic", "hi" to "Hindi",
            "tr" to "Turkish", "nl" to "Dutch", "sv" to "Swedish",
            "pl" to "Polish", "uk" to "Ukrainian", "vi" to "Vietnamese",
            "th" to "Thai", "id" to "Indonesian"
        )
        val langName = languageNames[targetLanguage] ?: targetLanguage

        val systemPrompt = """You are a precise lyrics translation assistant. Your output must ALWAYS be a valid JSON array of strings.

CRITICAL RULES:
1. Output ONLY a JSON array: ["line1", "line2", "line3"]
2. NO explanations, NO questions, NO additional text
3. Each input line maps to exactly one output line
4. Preserve empty lines as empty strings ""
5. Return EXACTLY $lineCount items in the array
6. If uncertain, provide best approximation but maintain line count"""

        val userPrompt = """Translate the following $lineCount lines to $langName.

IMPORTANT:
- Provide natural, accurate translation
- Maintain poetic flow and meaning
- Keep punctuation appropriate for target language
- Preserve line-by-line structure exactly
- For song lyrics, prioritize singability

Input ($lineCount lines):
$fullText

Output MUST be a JSON array with EXACTLY $lineCount strings."""

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        }

        val bodyJson = JSONObject().apply {
            val m = model?.takeIf { it.isNotBlank() } ?: defaultModel
            if (m.isNotBlank()) put("model", m)
            put("messages", messages)
            put("temperature", 0.3)
            put("max_tokens", lineCount * 100)
        }

        val requestBuilder = Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toString().toRequestBody(JSON_TYPE))

        if (apiKey.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer ${apiKey.trim()}")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            val errorMsg = try {
                JSONObject(responseBody ?: "").optJSONObject("error")?.optString("message")
            } catch (_: Exception) { null }
            throw Exception(errorMsg ?: "API error: HTTP ${response.code}")
        }

        val json = JSONObject(responseBody ?: throw Exception("Empty response"))
        val choices = json.optJSONArray("choices")
            ?: throw Exception("No choices in response")

        if (choices.length() == 0) throw Exception("Empty choices array")

        val content = choices.getJSONObject(0)
            .optJSONObject("message")
            ?.optString("content")
            ?.trim()
            ?: throw Exception("No content in response")

        return parseTranslationResponse(content, lineCount)
    }

    private fun parseTranslationResponse(content: String, expectedLines: Int): List<String> {
        var lines: List<String>? = null

        try {
            val arr = JSONArray(content)
            lines = (0 until arr.length()).map { arr.optString(it) }
        } catch (_: Exception) {
            val cleaned = content.replace("```json", "").replace("```", "").trim()
            try {
                val arr = JSONArray(cleaned)
                lines = (0 until arr.length()).map { arr.optString(it) }
            } catch (_: Exception) {
                val startIdx = cleaned.indexOf('[')
                val endIdx = cleaned.lastIndexOf(']')
                if (startIdx != -1 && endIdx > startIdx) {
                    try {
                        val arr = JSONArray(cleaned.substring(startIdx, endIdx + 1))
                        lines = (0 until arr.length()).map { arr.optString(it) }
                    } catch (_: Exception) {
                        lines = cleaned.lines()
                            .filter { it.trim().isNotEmpty() }
                            .map { it.trim().removeSurrounding("\"").removeSurrounding("'") }
                    }
                }
            }
        }

        if (lines == null) throw Exception("Could not parse translation response")

        val result = lines.toMutableList()
        while (result.size < expectedLines) result.add("")
        return result.take(expectedLines)
    }
}
