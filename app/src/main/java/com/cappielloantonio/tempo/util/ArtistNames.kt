package com.cappielloantonio.tempo.util

object ArtistNames {
    private val SEPARATOR = Regex("""\s*[,/;&\u2022]\s*|\s+feat\.?\s+|\s+ft\.?\s+""")

    fun first(artist: String): String = artist.split(SEPARATOR)[0].trim()
}
