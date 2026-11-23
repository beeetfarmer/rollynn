package com.cappielloantonio.tempo.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class LyricsPaletteTest {
    private fun lightness(color: Int) = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }[2]

    /** Stripes of the given colours. */
    private fun art(vararg colors: Int): Palette {
        val bitmap = Bitmap.createBitmap(90, 90, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        val stripe = 90f / colors.size
        colors.forEachIndexed { i, c ->
            paint.color = c
            canvas.drawRect(i * stripe, 0f, (i + 1) * stripe, 90f, paint)
        }
        return Palette.from(bitmap).generate()
    }

    @Test
    fun givesABaseAndFourOpaqueBlobColours() {
        val colors = LyricsPalette.from(art(0xFFFF5A36.toInt(), 0xFFC2185B.toInt(), 0xFF3A1020.toInt(), 0xFF7B1FA2.toInt()))

        assertEquals(5, colors.size)
        colors.forEach { assertEquals(0xFF, Color.alpha(it)) }
    }

    @Test
    fun theBaseStaysDarkForWhiteText() {
        val colors = LyricsPalette.from(art(0xFF1E88E5.toInt(), 0xFF00A896.toInt(), 0xFF26C6DA.toInt(), 0xFF05182A.toInt()))

        assertTrue(lightness(colors[0]) <= 0.2f + 0.01f)
    }

    @Test
    fun theBlobsUseTheArtsColours() {
        val red = 0xFFE53935.toInt()
        val colors = LyricsPalette.from(art(red, red, red))

        for (blob in colors.drop(1)) {
            assertTrue("blob ${Integer.toHexString(blob)} should be reddish", Color.red(blob) > Color.blue(blob) + 60)
        }
    }

    @Test
    fun grayArtFallsBackToTheDefaults() {
        val colors = LyricsPalette.from(art(Color.GRAY, Color.DKGRAY))

        assertEquals(5, colors.size)
        assertEquals(0xFF, Color.alpha(colors[0]))
    }

    @Test
    fun darkenedCapsTheLightness() {
        assertTrue(lightness(LyricsPalette.darkened(Color.YELLOW, 0.2f)) <= 0.201f)
        assertEquals(Color.BLACK, LyricsPalette.darkened(Color.BLACK, 0.2f))
    }

    @Test
    fun theDefaultIsNotSharedBetweenCallers() {
        // Pure white has no usable colour, so the defaults come back.
        val first = LyricsPalette.from(art(Color.WHITE))
        first[0] = Color.RED

        assertArrayEquals(LyricsPalette.DEFAULT, LyricsPalette.from(art(Color.WHITE)))
    }
}
