package com.cappielloantonio.tempo.ui.adapter

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.ui.view.KaraokeTextView
import com.cappielloantonio.tempo.util.LyricItem
import kotlin.math.abs
import kotlin.math.min

/**
 * The lyrics list. The current line is bright and full size; the others are dimmed, slightly
 * smaller, and blurred more the further away they are. Unsynced lyrics are all shown plainly.
 */
class LyricsAdapter(private val onLineClick: (LyricItem) -> Unit) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var items: List<LyricItem> = emptyList()
        private set
    var synced = false
        private set

    /** The item being sung, or −1. */
    var current = -1
        private set

    /** How far the text has grown towards its immersive size: 0 normal, 1 immersive. */
    var growth = 0f
        private set

    val immersive get() = growth >= 1f

    /** Blur lines two or more away from the current one. */
    var blurDistant = true

    /** Freezes the pulsing dots, for users who turned animations off. */
    var reduceMotion = false

    /** Ordinary-sized text, for a podcast description shown in place of lyrics. */
    var compact = false

    fun submit(items: List<LyricItem>, synced: Boolean) {
        this.items = items
        this.synced = synced
        current = -1
        notifyDataSetChanged()
    }

    fun setCurrent(index: Int) {
        if (index == current) return
        current = index
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    fun setImmersive(immersive: Boolean) = setGrowth(if (immersive) 1f else 0f)

    /** Sizes the text part way between normal and immersive, a frame at a time while it grows. */
    fun setGrowth(growth: Float) {
        val g = growth.coerceIn(0f, 1f)
        if (g == this.growth) return
        this.growth = g
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SIZE)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int) = if (items[position].isInstrumental) TYPE_BREAK else TYPE_LINE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_BREAK) {
            BreakHolder(inflater.inflate(R.layout.item_lyrics_break, parent, false))
        } else {
            LineHolder(inflater.inflate(R.layout.item_lyrics_line, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LineHolder -> holder.bind(items[position])
            is BreakHolder -> holder.bind(items[position])
        }
        applySize(holder)
        applyState(holder, position, animate = false)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) return onBindViewHolder(holder, position)
        if (PAYLOAD_SIZE in payloads) applySize(holder)
        if (PAYLOAD_STATE in payloads) applyState(holder, position, animate = true)
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is BreakHolder) holder.setPulsing(false)
        (holder as? Holder)?.cancelAnimations()
    }

    private fun applySize(holder: RecyclerView.ViewHolder) {
        val gap = lerp(30f, 34f, growth)
        holder.itemView.setPadding(0, 0, 0, (gap * holder.itemView.resources.displayMetrics.density).toInt())
        if (holder is LineHolder) holder.applySize(growth, compact)
    }

    private fun applyState(holder: RecyclerView.ViewHolder, position: Int, animate: Boolean) {
        val h = holder as? Holder ?: return
        if (!synced) {
            h.setDistance(0, animate = false, blur = 0f)
            if (holder is LineHolder) holder.showPlain()
            return
        }
        val distance = if (current < 0) Int.MAX_VALUE else abs(position - current)
        val blur = if (blurDistant && distance >= 2) min(distance - 1, 3) * BLUR_STEP_DP else 0f
        h.setDistance(distance, animate, blur)
        when (holder) {
            is LineHolder -> holder.showSynced(distance == 0, animate)
            is BreakHolder -> holder.setCurrent(distance == 0, reduceMotion)
        }
    }

    /** Scale and blur by distance from the current line, shared by both kinds of row. */
    abstract class Holder(view: View) : RecyclerView.ViewHolder(view) {
        private var scaleAnimator: ValueAnimator? = null
        private var blurAnimator: ValueAnimator? = null
        private var blurPx = 0f

        init {
            // Scale from the start edge, as the text is aligned there.
            view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                v.pivotX = if (v.layoutDirection == View.LAYOUT_DIRECTION_RTL) v.width.toFloat() else 0f
                v.pivotY = v.height / 2f
            }
        }

        fun setDistance(distance: Int, animate: Boolean, blur: Float) {
            val scale = if (distance == 0) 1f else DISTANT_SCALE
            scaleAnimator?.cancel()
            if (animate && itemView.scaleX != scale) {
                scaleAnimator = ObjectAnimator.ofPropertyValuesHolder(
                    itemView,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, scale),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, scale),
                ).setDuration(STATE_MS).also { it.start() }
            } else {
                itemView.scaleX = scale
                itemView.scaleY = scale
            }
            setBlur(blur * itemView.resources.displayMetrics.density, animate)
        }

        private fun setBlur(target: Float, animate: Boolean) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            blurAnimator?.cancel()
            if (!animate || target == blurPx) return applyBlur(target)
            blurAnimator = ValueAnimator.ofFloat(blurPx, target).setDuration(STATE_MS).apply {
                addUpdateListener { applyBlur(it.animatedValue as Float) }
                start()
            }
        }

        private fun applyBlur(px: Float) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
            blurPx = px
            itemView.setRenderEffect(if (px < 0.5f) null else RenderEffect.createBlurEffect(px, px, Shader.TileMode.DECAL))
        }

        open fun cancelAnimations() {
            scaleAnimator?.cancel()
            blurAnimator?.cancel()
        }
    }

    inner class LineHolder(view: View) : Holder(view) {
        val text: KaraokeTextView = view.findViewById(R.id.lyrics_line_text)
        private val secondary: TextView = view.findViewById(R.id.lyrics_line_secondary)
        private var item: LyricItem? = null

        fun bind(item: LyricItem) {
            this.item = item
            text.text = item.text
            secondary.text = item.secondary
            secondary.visibility = if (item.secondary.isNullOrEmpty()) View.GONE else View.VISIBLE
            itemView.contentDescription = itemView.context.getString(R.string.lyrics_jump_to_line, item.text)
            itemView.setOnClickListener { this.item?.let(onLineClick) }
        }

        fun applySize(growth: Float, compact: Boolean) {
            text.typeface = ResourcesCompat.getFont(itemView.context, if (compact) R.font.inter_regular else R.font.inter_extra_bold)
            if (compact) {
                setSize(text, 17f, 26f)
                text.letterSpacing = 0f
                return
            }
            setSize(text, lerp(31f, 36f, growth), lerp(36f, 41f, growth))
            text.letterSpacing = lerp(-0.025f, -0.03f, growth)
            setSize(secondary, lerp(21f, 23f, growth), lerp(26f, 28f, growth))
        }

        fun showPlain() {
            text.clearSung(animate = false)
            text.setTextColor(Color.WHITE)
            secondary.setTextColor(SECONDARY_CURRENT)
            itemView.isClickable = false
            itemView.contentDescription = null
        }

        fun showSynced(current: Boolean, animate: Boolean) {
            text.setTextColor(UNSUNG)
            if (current) {
                if (text.sungChars < 0) text.setSungChars(0f)
            } else {
                text.clearSung(animate)
            }
            secondary.setTextColor(if (current) SECONDARY_CURRENT else SECONDARY_OTHER)
            itemView.isClickable = true
        }

        override fun cancelAnimations() {
            super.cancelAnimations()
            text.clearSung(animate = false)
        }
    }

    inner class BreakHolder(view: View) : Holder(view) {
        private val dots = listOf<View>(
            view.findViewById(R.id.lyrics_dot_1),
            view.findViewById(R.id.lyrics_dot_2),
            view.findViewById(R.id.lyrics_dot_3),
        )
        private val pulses = arrayOfNulls<ObjectAnimator>(3)
        private var isCurrent = false
        private var lit = -1
        private var item: LyricItem? = null

        fun bind(item: LyricItem) {
            this.item = item
            itemView.setOnClickListener { this.item?.let(onLineClick) }
        }

        fun setCurrent(current: Boolean, reduceMotion: Boolean) {
            isCurrent = current
            setPulsing(current && !reduceMotion)
            if (!current) {
                lit = -1
                dots.forEach { it.animate().alpha(DOT_IDLE).setDuration(DOT_MS).start() }
            } else if (lit < 0) {
                setProgress(0f)
            }
        }

        /** Lights a dot as the break passes 0 %, 34 % and 67 %. */
        fun setProgress(progress: Float) {
            if (!isCurrent) return
            val count = DOT_STEPS.count { progress > it }
            if (count == lit) return
            lit = count
            dots.forEachIndexed { i, dot ->
                dot.animate().alpha(if (i < count) 1f else DOT_WAITING).setDuration(DOT_MS).start()
            }
        }

        fun setPulsing(pulsing: Boolean) {
            dots.forEachIndexed { i, dot ->
                if (!pulsing) {
                    pulses[i]?.cancel()
                    pulses[i] = null
                    dot.scaleX = 1f
                    dot.scaleY = 1f
                } else if (pulses[i] == null) {
                    pulses[i] = ObjectAnimator.ofPropertyValuesHolder(
                        dot,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.3f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.3f),
                    ).apply {
                        duration = PULSE_MS / 2
                        startDelay = i * 150L
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        start()
                    }
                }
            }
        }

        override fun cancelAnimations() {
            super.cancelAnimations()
            setPulsing(false)
        }
    }

    companion object {
        const val PAYLOAD_STATE = "state"
        const val PAYLOAD_SIZE = "size"
        private const val TYPE_LINE = 0
        private const val TYPE_BREAK = 1

        const val DISTANT_SCALE = 0.965f
        const val BLUR_STEP_DP = 0.9f
        private const val STATE_MS = 500L

        /** White at 42 %, 80 % and 30 %. */
        const val UNSUNG = 0x6BFFFFFF
        const val SECONDARY_CURRENT = 0xCCFFFFFF.toInt()
        const val SECONDARY_OTHER = 0x4DFFFFFF

        private const val DOT_IDLE = 0.32f
        private const val DOT_WAITING = 0.45f
        private const val DOT_MS = 300L
        private const val PULSE_MS = 1_100L
        private val DOT_STEPS = floatArrayOf(-0.0001f, 0.34f, 0.67f)

        private fun dp(view: View, value: Int) = (value * view.resources.displayMetrics.density).toInt()

        private fun setSize(view: TextView, sp: Float, lineSp: Float) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            TextViewCompat.setLineHeight(view, TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, lineSp, view.resources.displayMetrics).toInt())
        }
    }
}

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
