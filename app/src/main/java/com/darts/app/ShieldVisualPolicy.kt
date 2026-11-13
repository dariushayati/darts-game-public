package com.darts.app

object ShieldVisualPolicy {
    const val FLASH_GROWTH = 0.22f

    /** Shields a full shield represents (matches the core MAX_SHIELDS cap). */
    const val MAX_SHIELDS = 3

    fun flashScale(target: Int, lastTarget: Int?, flash: Float): Float =
        if (target == lastTarget) 1f + flash * FLASH_GROWTH else 1f

    /**
     * Fraction of the shield that is filled for a given level: one shield is a
     * third, two is two thirds, three is full. Values above [MAX_SHIELDS] clamp.
     */
    fun fillFraction(shields: Int): Float =
        (shields.toFloat() / MAX_SHIELDS).coerceIn(0f, 1f)

    /** Animated variant so the fill glides as the level tween advances. */
    fun fillFraction(level: Float): Float =
        (level / MAX_SHIELDS).coerceIn(0f, 1f)
}
