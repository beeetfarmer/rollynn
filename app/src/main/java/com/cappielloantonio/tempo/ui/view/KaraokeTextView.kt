package com.cappielloantonio.tempo.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.animation.doOnEnd
import kotlin.math.floor
import kotlin.math.min

/**
 * A lyric line that fills with [sungColor] from the start as it is sung, over its text colour.
 * The fill runs through wrapped rows in reading order: the first row fills, then the next.
 */
class KaraokeTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    AppCompatTextView(context, attrs) {

    var sungColor: Int = Color.WHITE

    /** Characters sung so far, with fractions, or −1 for none. */
    var sungChars: Float = -1f
        private set

    private var fillAlpha = 1f
    private var fadeOut: ValueAnimator? = null

    fun setSungChars(chars: Float) {
        fadeOut?.cancel()
        fadeOut = null
        if (chars == sungChars && fillAlpha == 1f) return
        sungChars = chars
        fillAlpha = 1f
        invalidate()
    }

    /** Takes the fill away, fading it out when [animate], as when the line stops being current. */
    fun clearSung(animate: Boolean) {
        if (sungChars < 0 || fadeOut != null) return
        if (!animate) {
            sungChars = -1f
            invalidate()
            return
        }
        fadeOut = ValueAnimator.ofFloat(1f, 0f).setDuration(FADE_MS).apply {
            addUpdateListener {
                fillAlpha = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                if (fadeOut !== this) return@doOnEnd
                fadeOut = null
                sungChars = -1f
                fillAlpha = 1f
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layout = layout ?: return
        if (sungChars <= 0f) return

        // Draw the text again in the sung colour, clipped to the sung part of each row.
        val paint = paint
        val color = paint.color
        paint.color = sungColor
        paint.alpha = (Color.alpha(sungColor) * fillAlpha).toInt()
        canvas.save()
        canvas.translate(totalPaddingLeft.toFloat(), totalPaddingTop.toFloat())
        for (row in 0 until layout.lineCount) {
            val rowStart = layout.getLineStart(row)
            if (sungChars <= rowStart) break
            val rtl = layout.getParagraphDirection(row) == Layout.DIR_RIGHT_TO_LEFT
            val left = layout.getLineLeft(row)
            val right = layout.getLineRight(row)
            val x = sungEdge(layout, row, rtl)
            canvas.save()
            if (rtl) {
                canvas.clipRect(x, layout.getLineTop(row).toFloat(), right, layout.getLineBottom(row).toFloat())
            } else {
                canvas.clipRect(left, layout.getLineTop(row).toFloat(), x, layout.getLineBottom(row).toFloat())
            }
            layout.draw(canvas)
            canvas.restore()
        }
        canvas.restore()
        paint.color = color
    }

    /** Where the sung part of [row] ends, in layout coordinates. */
    private fun sungEdge(layout: Layout, row: Int, rtl: Boolean): Float {
        val rowEnd = layout.getLineEnd(row)
        val rowFar = if (rtl) layout.getLineLeft(row) else layout.getLineRight(row)
        if (sungChars >= rowEnd) return rowFar
        val offset = floor(sungChars).toInt()
        val from = layout.getPrimaryHorizontal(offset)
        val to = if (offset + 1 >= rowEnd) rowFar else layout.getPrimaryHorizontal(min(offset + 1, rowEnd))
        return from + (to - from) * (sungChars - offset)
    }

    private companion object {
        const val FADE_MS = 400L
    }
}
