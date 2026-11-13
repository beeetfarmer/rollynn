package com.cappielloantonio.tempo.util

/**
 * Where the portrait nav dock and mini player go. They share the bottom row when
 * either is minimized and both fit; otherwise the mini player stacks above the
 * dock, as it always did. All values are pixels.
 */
object DockLayout {
    data class Result(
        val sameRow: Boolean,
        /** Left edge of the nav dock. */
        val navX: Int,
        /** Left edge of the mini player. */
        val playerX: Int,
        val playerWidth: Int,
    )

    /**
     * @param navWidth the dock's current width (a circle when minimized)
     * @param rowHeight dock height; a minimized player is a circle this size
     * @param stackedMargin side margin of the full-width mini player above the dock
     * @param rowMargin side margin when both share the bottom row
     * @param minPlayerWidth narrowest mini player worth showing beside the dock
     */
    @JvmStatic
    fun compute(
        screenWidth: Int,
        navWidth: Int,
        rowHeight: Int,
        navCompact: Boolean,
        playerCompact: Boolean,
        playerVisible: Boolean,
        stackedMargin: Int,
        rowMargin: Int,
        gap: Int,
        minPlayerWidth: Int,
    ): Result {
        if (playerVisible && (navCompact || playerCompact)) {
            val available = screenWidth - 2 * rowMargin
            val playerWidth = if (playerCompact) rowHeight else available - navWidth - gap
            val fits = if (playerCompact) navWidth + gap + playerWidth <= available
                       else playerWidth >= minPlayerWidth
            if (fits) {
                val start = (screenWidth - (navWidth + gap + playerWidth)) / 2
                return Result(true, start, start + navWidth + gap, playerWidth)
            }
        }
        val playerWidth = if (playerCompact) rowHeight else screenWidth - 2 * stackedMargin
        return Result(false, (screenWidth - navWidth) / 2, (screenWidth - playerWidth) / 2, playerWidth)
    }
}
