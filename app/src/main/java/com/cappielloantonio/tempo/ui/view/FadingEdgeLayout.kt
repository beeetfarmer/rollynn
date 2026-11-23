package com.cappielloantonio.tempo.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.util.AttributeSet
import android.widget.FrameLayout

/** Fades its content out towards the top and bottom edges, to transparent, over any background. */
class FadingEdgeLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {

    var fadeTop: Int = 0
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    var fadeBottom: Int = 0
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private var shader: Shader? = null

    init {
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = null
    }

    override fun dispatchDraw(canvas: Canvas) {
        val h = height.toFloat()
        if (h <= 0f || (fadeTop <= 0 && fadeBottom <= 0)) {
            super.dispatchDraw(canvas)
            return
        }
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), h, null)
        super.dispatchDraw(canvas)
        maskPaint.shader = shader ?: mask(h).also { shader = it }
        canvas.drawRect(0f, 0f, width.toFloat(), h, maskPaint)
        canvas.restoreToCount(layer)
    }

    private fun mask(h: Float): Shader {
        val top = (fadeTop / h).coerceIn(0f, 0.5f)
        val bottom = (1f - fadeBottom / h).coerceIn(top, 1f)
        return LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, top, bottom, 1f),
            Shader.TileMode.CLAMP,
        )
    }
}
