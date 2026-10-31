package com.cappielloantonio.tempo.ui.fragment

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SearchStatusBarInsetTest {

    private val appBar = View(RuntimeEnvironment.getApplication())
    private val results = View(RuntimeEnvironment.getApplication()).apply { setPadding(1, 64, 2, 3) }

    private fun dispatchStatusBar(top: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(appBar, insets)
    }

    @Test
    fun searchBarSitsBelowTheStatusBar() {
        SearchFragment.keepBelowStatusBar(appBar, results)

        dispatchStatusBar(40)

        assertEquals(40, appBar.paddingTop)
    }

    @Test
    fun resultsMoveDownByTheSameAmountKeepingTheirOtherPadding() {
        SearchFragment.keepBelowStatusBar(appBar, results)

        dispatchStatusBar(40)

        assertEquals(104, results.paddingTop)
        assertEquals(1, results.paddingLeft)
        assertEquals(2, results.paddingRight)
        assertEquals(3, results.paddingBottom)
    }

    @Test
    fun repeatedInsetsDoNotAddUp() {
        SearchFragment.keepBelowStatusBar(appBar, results)

        dispatchStatusBar(40)
        dispatchStatusBar(40)
        dispatchStatusBar(24)

        assertEquals(24, appBar.paddingTop)
        assertEquals(88, results.paddingTop)
    }
}
