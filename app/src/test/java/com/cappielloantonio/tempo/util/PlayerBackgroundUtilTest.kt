package com.cappielloantonio.tempo.util

import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.widget.RatingBar
import com.cappielloantonio.tempo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlayerBackgroundUtilTest {

    private fun ratingBar() = RatingBar(
        ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.AppTheme),
        null,
        android.R.attr.ratingBarStyleIndicator,
    )

    private fun RatingBar.emptyStars() =
        (progressDrawable as LayerDrawable).findDrawableByLayerId(android.R.id.background)

    @Test
    fun filledAndEmptyStarsTakeTheColour() {
        val bar = ratingBar()

        PlayerBackgroundUtil.tintRatingBar(bar, Color.WHITE)

        assertEquals(Color.WHITE, bar.progressTintList!!.defaultColor)
        assertEquals(Color.WHITE, bar.progressBackgroundTintList!!.defaultColor)
    }

    @Test
    fun emptyStarsAreNotLeftAtThePlatformsDisabledAlpha() {
        val bar = ratingBar()
        assertTrue("platform default was ${bar.emptyStars().alpha}", bar.emptyStars().alpha < 0x80)

        PlayerBackgroundUtil.tintRatingBar(bar, Color.WHITE)

        assertEquals(0x80, bar.emptyStars().alpha)
    }

    @Test
    fun tintingAgainForTheNextTrackKeepsTheEmptyStarsVisible() {
        val bar = ratingBar()

        PlayerBackgroundUtil.tintRatingBar(bar, Color.WHITE)
        PlayerBackgroundUtil.tintRatingBar(bar, Color.BLACK)

        assertEquals(Color.BLACK, bar.progressBackgroundTintList!!.defaultColor)
        assertEquals(0x80, bar.emptyStars().alpha)
    }
}
