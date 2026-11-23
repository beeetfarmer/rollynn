package com.cappielloantonio.tempo.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.constraintlayout.widget.ConstraintLayout

/** Tells [touchWatcher] about every touch in it, before its children; the watcher may take the touch. */
class TouchWatchConstraintLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    ConstraintLayout(context, attrs) {

    fun interface TouchWatcher {
        /** Returns true to keep the event from the children. */
        fun onTouch(event: MotionEvent): Boolean
    }

    var touchWatcher: TouchWatcher? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (touchWatcher?.onTouch(ev) == true) return true
        return super.dispatchTouchEvent(ev)
    }
}
