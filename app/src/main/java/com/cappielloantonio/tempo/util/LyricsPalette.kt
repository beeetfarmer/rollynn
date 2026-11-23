package com.cappielloantonio.tempo.util

import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/** Colours for the lyrics background, from the album art: a dark base and four blobs. */
object LyricsPalette {
    const val DEFAULT_BASE = 0xFF1A1A1A.toInt()

    /** Used with no artwork: the base with four soft greys. */
    @JvmField
    val DEFAULT = intArrayOf(DEFAULT_BASE, 0xFF4A4A4A.toInt(), 0xFF3A3A3A.toInt(), 0xFF5A5A5A.toInt(), 0xFF2E2E2E.toInt())

    /** Base, then the four blob colours. Swatches the art lacks are filled from those it has. */
    @JvmStatic
    fun from(palette: Palette): IntArray {
        val any = listOfNotNull(
            palette.vibrantSwatch, palette.darkVibrantSwatch, palette.lightVibrantSwatch,
            palette.mutedSwatch, palette.lightMutedSwatch, palette.dominantSwatch,
        ).firstOrNull()?.rgb ?: return DEFAULT.clone()

        // Kept dark, as white lyrics sit on it.
        val base = palette.darkMutedSwatch?.rgb?.let { darkened(it, 0.2f) }
            ?: palette.darkVibrantSwatch?.rgb?.let { darkened(it, 0.15f) }
            ?: DEFAULT_BASE
        return intArrayOf(
            base,
            palette.vibrantSwatch?.rgb ?: any,
            palette.darkVibrantSwatch?.rgb ?: any,
            palette.lightVibrantSwatch?.rgb ?: any,
            palette.mutedSwatch?.rgb ?: any,
        )
    }

    /** [color] with its lightness at most [maxLightness], so it stays dark. */
    @JvmStatic
    fun darkened(color: Int, maxLightness: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[2] = hsl[2].coerceAtMost(maxLightness)
        return ColorUtils.HSLToColor(hsl) or Color.BLACK
    }
}
