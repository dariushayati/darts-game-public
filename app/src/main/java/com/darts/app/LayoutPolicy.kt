package com.darts.app

object LayoutPolicy {
    const val TABLET_MIN_WIDTH_DP = 600
    const val PANEL_MIN_WIDTH_DP = 320f
    const val PANEL_MAX_WIDTH_DP = 420f
    const val PANEL_FRACTION = 0.38f
    const val MENU_MAX_WIDTH_DP = 720f
    const val CONTENT_MAX_WIDTH_DP = 640f

    fun isTablet(smallestWidthDp: Int): Boolean = smallestWidthDp >= TABLET_MIN_WIDTH_DP

    fun useTwoPane(smallestWidthDp: Int, widthDp: Float, heightDp: Float): Boolean =
        widthDp > heightDp && widthDp >= 600f

    fun panelWidthDp(widthDp: Float): Float =
        maxOf(PANEL_MIN_WIDTH_DP, minOf(PANEL_MAX_WIDTH_DP, widthDp * PANEL_FRACTION))
}
