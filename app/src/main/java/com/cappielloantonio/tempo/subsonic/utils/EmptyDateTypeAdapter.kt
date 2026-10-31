package com.cappielloantonio.tempo.subsonic.utils

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// This adapter handles Date objects, returning null if the JSON string is empty or unparsable.
class EmptyDateTypeAdapter : JsonDeserializer<Date> {

    // Define the date formats expected from the Subsonic server.
    private val dateFormats: List<SimpleDateFormat> = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
    )

    // SSS would read every fractional digit as milliseconds, and Go servers send up to nine.
    private val fraction = Regex("\\.(\\d+)")

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date? {
        val jsonString = fraction.replace(json.asString.trim()) { "." + it.groupValues[1].padEnd(3, '0').take(3) }

        if (jsonString.isEmpty()) {
            return null
        }

        for (format in dateFormats) {
            try {
                return format.parse(jsonString)
            } catch (e: ParseException) {
                // Ignore and try the next format
            }
        }
        
        return null 
    }
}