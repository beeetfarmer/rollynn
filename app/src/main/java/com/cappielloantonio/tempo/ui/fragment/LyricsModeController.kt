package com.cappielloantonio.tempo.ui.fragment

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.constraintlayout.widget.ConstraintSet
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.cappielloantonio.tempo.ui.view.BlobBackgroundView
import com.cappielloantonio.tempo.ui.view.TouchWatchConstraintLayout

/**
 * The player's lyrics mode: shows the colour-blob background while the lyrics page is open and,
 * after [idleMs] without a touch during playback (never when it is 0), hides the controls so the lyrics take the
 * screen ("immersive"). Any touch then brings the controls back, and is not passed on.
 */
class LyricsModeController(
    private val root: TouchWatchConstraintLayout,
    private val pager: View,
    /** Views that stay when the controls hide: the pager itself, and what sits over it. */
    private val keep: Set<View>,
    private val background: BlobBackgroundView,
    /** Off in landscape, where the lyrics sit beside the controls rather than above them. */
    private val immersiveAllowed: Boolean,
    /** Read each time the wait starts, so a settings change applies at once. */
    private val idleMs: () -> Long,
    private val onImmersiveChanged: (Boolean) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val enter = Runnable { setImmersive(true) }
    private val accessibility = root.context.getSystemService(AccessibilityManager::class.java)
    private val slide = 16 * root.resources.displayMetrics.density

    var lyricsShown = false
        private set
    var immersive = false
        private set
    private var playing = false
    private var active = true
    /** Swallowing the rest of a gesture whose touch brought the controls back. */
    private var swallowing = false
    private var pagerBottom: Pair<Int, Int>? = null

    init {
        root.touchWatcher = TouchWatchConstraintLayout.TouchWatcher(::onTouch)
    }

    fun setLyricsShown(shown: Boolean) {
        if (shown == lyricsShown) return
        lyricsShown = shown
        showBackground(shown)
        if (!shown) setImmersive(false, animate = false)
        restartTimer()
    }

    fun setPlaying(playing: Boolean) {
        this.playing = playing
        // Paused from elsewhere, as from the notification: show what's going on.
        if (!playing) setImmersive(false)
        restartTimer()
    }

    /** Off while the player isn't on screen. */
    fun setActive(active: Boolean) {
        this.active = active
        if (!active) setImmersive(false, animate = false)
        restartTimer()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        root.touchWatcher = null
    }

    private fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swallowing = immersive
                if (immersive) setImmersive(false)
                restartTimer()
                return swallowing
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val swallowed = swallowing
                swallowing = false
                restartTimer()
                return swallowed
            }
        }
        return swallowing
    }

    private fun restartTimer() {
        handler.removeCallbacks(enter)
        // TalkBack users move by touch, so the controls never hide on them.
        val talkBack = accessibility?.isTouchExplorationEnabled == true
        val idle = idleMs()
        if (idle > 0 && immersiveAllowed && lyricsShown && playing && active && !immersive && !talkBack) {
            handler.postDelayed(enter, idle)
        }
    }

    private fun setImmersive(on: Boolean, animate: Boolean = true) {
        if (on == immersive) return
        immersive = on
        background.scrim = if (on) 0.30f else 0.28f

        // Before the fade starts: applying constraints also re-applies each view's alpha.
        stretchPager(on)
        val duration = if (animate) MODE_MS else 0L
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child in keep || child.visibility == View.GONE) continue
            child.animate().cancel()
            child.animate()
                .alpha(if (on) 0f else 1f)
                .translationY(if (on) slide else 0f)
                .setDuration(duration)
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
        }
        onImmersiveChanged(on)
        restartTimer()
    }

    /** Lets the lyrics run to the bottom of the screen while the controls are hidden. */
    private fun stretchPager(stretch: Boolean) {
        val set = ConstraintSet()
        set.clone(root)
        val params = pager.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        if (stretch) {
            pagerBottom = params.bottomToTop to params.bottomToBottom
            set.connect(pager.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM, params.bottomMargin)
        } else {
            val (toTop, toBottom) = pagerBottom ?: return
            set.clear(pager.id, ConstraintSet.BOTTOM)
            if (toTop != ConstraintSet.UNSET) {
                set.connect(pager.id, ConstraintSet.BOTTOM, toTop, ConstraintSet.TOP, params.bottomMargin)
            } else {
                set.connect(pager.id, ConstraintSet.BOTTOM, toBottom, ConstraintSet.BOTTOM, params.bottomMargin)
            }
        }
        set.applyTo(root)
    }

    private fun showBackground(show: Boolean) {
        background.animate().cancel()
        if (show) {
            if (background.visibility != View.VISIBLE) {
                background.alpha = 0f
                background.visibility = View.VISIBLE
            }
            background.animate().alpha(1f).setDuration(FADE_MS).start()
        } else {
            background.animate().alpha(0f).setDuration(FADE_MS)
                .withEndAction { background.visibility = View.GONE }.start()
        }
    }

    companion object {
        private const val MODE_MS = 300L
        private const val FADE_MS = 250L
    }
}
