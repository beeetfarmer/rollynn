package com.cappielloantonio.tempo.ui.span

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import java.util.concurrent.atomic.AtomicLong

class WipeSpan(
    private val highlightColor: Int,
    private val shadowColor: Int,
    private val wordStart: Int,
    private val wordEnd: Int,
    private val timeSource: AtomicLong,
    val seekTarget: Int
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.bottom = metrics.bottom
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val timestamp = timeSource.get().toInt()
        val width = paint.measureText(text, start, end)

        val progress = when {
            timestamp >= wordEnd -> 1f
            timestamp <= wordStart -> 0f
            else -> {
                val duration = wordEnd - wordStart
                if (duration > 0) (timestamp - wordStart).toFloat() / duration else 1f
            }
        }

        if (progress >= 1f) {
            paint.color = highlightColor
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            return
        }

        if (progress <= 0f) {
            paint.color = shadowColor
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            return
        }

        val splitX = x + width * progress

        canvas.save()
        canvas.clipRect(x, top.toFloat(), splitX, bottom.toFloat())
        paint.color = highlightColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
        canvas.restore()

        canvas.save()
        canvas.clipRect(splitX, top.toFloat(), x + width, bottom.toFloat())
        paint.color = shadowColor
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
        canvas.restore()
    }
}
