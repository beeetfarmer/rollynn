package com.cappielloantonio.tempo.ui.view

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.graphics.ColorUtils
import com.cappielloantonio.tempo.util.LyricsPalette

/**
 * The lyrics background: a dark base colour with four soft colour blobs drifting slowly over
 * it, and a dark scrim on top for the text. Colours come from [setColors].
 */
class BlobBackgroundView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    View(context, attrs) {

    /** A blob's size and start on the 390 × 844 dp design frame, and how it drifts. */
    private class Blob(
        val size: Float,
        val x: Float,
        val y: Float,
        val fromRight: Boolean,
        val fromBottom: Boolean,
        val dx: Float,
        val dy: Float,
        val scaleFrom: Float,
        val scaleTo: Float,
        val periodMs: Long,
    ) {
        var fraction = 0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val matrix = Matrix()
        var animator: ValueAnimator? = null
    }

    private val blobs = listOf(
        Blob(340f, -120f, -80f, false, false, 90f, 140f, 1f, 1.25f, 16_000),
        Blob(300f, -110f, 160f, true, false, -110f, 90f, 1.1f, 0.9f, 21_000),
        Blob(360f, -60f, 470f, false, false, 70f, -160f, 0.9f, 1.2f, 18_000),
        Blob(260f, -70f, -70f, true, true, -80f, -90f, 1f, 1.15f, 24_000),
    )

    private val density = resources.displayMetrics.density
    private var colors = LyricsPalette.DEFAULT.clone()
    private var colorAnimator: ValueAnimator? = null
    private val scrimPaint = Paint()

    /** How dark the scrim over the blobs is, from 0 to 1. */
    var scrim: Float = 0.28f
        set(value) {
            field = value
            invalidate()
        }

    init {
        updateShaders()
    }

    /** Base then four blob colours, as from [LyricsPalette]; crossfades when [animate]. */
    fun setColors(next: IntArray, animate: Boolean) {
        require(next.size == 5)
        colorAnimator?.cancel()
        if (!animate || !isShown) {
            colors = next.clone()
            updateShaders()
            invalidate()
            return
        }
        val from = colors.clone()
        val evaluator = ArgbEvaluator()
        colorAnimator = ValueAnimator.ofFloat(0f, 1f).setDuration(CROSSFADE_MS).apply {
            addUpdateListener { a ->
                val f = a.animatedFraction
                colors = IntArray(5) { evaluator.evaluate(f, from[it], next[it]) as Int }
                updateShaders()
                invalidate()
            }
            start()
        }
    }

    private fun updateShaders() {
        blobs.forEachIndexed { i, blob ->
            val radius = blob.size / 2 * density
            val soft = SOFTNESS_DP * density
            val outer = radius + soft * 1.5f
            val color = colors[i + 1]
            blob.paint.shader = RadialGradient(
                0f, 0f, outer,
                intArrayOf(color, color, ColorUtils.setAlphaComponent(color, 0x80), Color.TRANSPARENT),
                floatArrayOf(0f, ((radius - soft) / outer).coerceAtLeast(0f), radius / outer, 1f),
                Shader.TileMode.CLAMP,
            )
            blob.paint.alpha = (255 * BLOB_OPACITY).toInt()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(colors[0])
        for (blob in blobs) {
            val f = blob.fraction
            val radius = blob.size / 2 * density
            // x and y are CSS offsets: from the right or bottom edge to the blob's far side.
            val left = if (blob.fromRight) width - (blob.x + blob.size) * density else blob.x * density
            val top = if (blob.fromBottom) height - (blob.y + blob.size) * density else blob.y * density
            val cx = left + radius + blob.dx * density * f
            val cy = top + radius + blob.dy * density * f
            val scale = blob.scaleFrom + (blob.scaleTo - blob.scaleFrom) * f
            blob.matrix.setScale(scale, scale)
            blob.matrix.postTranslate(cx, cy)
            blob.paint.shader.setLocalMatrix(blob.matrix)
            canvas.drawPaint(blob.paint)
        }
        scrimPaint.color = ColorUtils.setAlphaComponent(Color.BLACK, (255 * scrim).toInt())
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) startDrift() else stopDrift()
    }

    override fun onDetachedFromWindow() {
        stopDrift()
        colorAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun startDrift() {
        if (reduceMotion(context)) return
        for (blob in blobs) {
            val running = blob.animator
            if (running != null) {
                running.resume()
                continue
            }
            blob.animator = ValueAnimator.ofFloat(0f, 1f).setDuration(blob.periodMs).apply {
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    blob.fraction = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private fun stopDrift() {
        for (blob in blobs) blob.animator?.pause()
    }

    companion object {
        private const val CROSSFADE_MS = 600L
        private const val SOFTNESS_DP = 72f
        private const val BLOB_OPACITY = 0.85f

        /** Whether the user turned animations off in the system settings. */
        @JvmStatic
        fun reduceMotion(context: Context): Boolean =
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
