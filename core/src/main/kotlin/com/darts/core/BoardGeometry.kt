package com.darts.core

import kotlin.math.atan2
import kotlin.math.sqrt

enum class Ring { SINGLE, DOUBLE, TREBLE, OUTER_BULL, INNER_BULL, MISS }

/** The two single bands inside a number wedge. */
enum class SingleBand { LARGE, SMALL }

/**
 * A normalised dart hit. [number] is 1..20, or [BoardGeometry.BULL] (25); null for a miss.
 * [multiplier] is 1 (single / outer bull), 2 (double / inner bull) or 3 (treble).
 * [singleBand] distinguishes the large outer single from the small inner single;
 * it is null for non-single hits and for older inputs that could not report a band.
 * [x]/[y] are the normalised landing point (centre 0,0, outer double ring radius 1);
 * they are null for inputs without a position (e.g. Autodarts payloads that omit
 * coordinates), in which case the renderer falls back to a ring-derived position.
 */
data class DartHit(
    val number: Int?,
    val multiplier: Int,
    val ring: Ring,
    val singleBand: SingleBand? = null,
    val x: Float? = null,
    val y: Float? = null,
) {
    val isMiss: Boolean get() = ring == Ring.MISS
    val score: Int get() = (number ?: 0) * multiplier
}

/**
 * Canonical dartboard geometry, shared by the camera pipeline and the renderer so a
 * detection and a drawn sector can never disagree.
 *
 * Coordinates are normalised to the outer double ring: centre (0,0), radius 1.
 * x grows right and y grows down (screen space). Sector index 0 is the 20 at
 * 12 o'clock; indices advance clockwise in 18 degree steps.
 */
object BoardGeometry {
    /** Clockwise order of numbers starting at 12 o'clock. */
    val SECTOR_ORDER: List<Int> = listOf(
        20, 1, 18, 4, 13, 6, 10, 15, 2, 17,
        3, 19, 7, 16, 8, 11, 14, 9, 12, 5,
    )

    const val SECTOR_COUNT = 20
    const val SECTOR_ANGLE = 18f
    const val HALF_SECTOR = 9f
    const val BULL = 25

    // Ring radii as a fraction of the outer double wire (standard 451 mm board).
    const val DOUBLE_OUTER = 1.000f
    const val DOUBLE_INNER = 0.953f
    const val TREBLE_OUTER = 0.629f
    const val TREBLE_INNER = 0.582f
    const val OUTER_BULL = 0.0935f
    const val INNER_BULL = 0.0374f

    /** All claimable targets: the twenty numbers plus the bull. */
    val TARGETS: List<Int> = SECTOR_ORDER + BULL

    fun numberAt(index: Int): Int = SECTOR_ORDER[Math.floorMod(index, SECTOR_COUNT)]

    /** Centre angle of [index] in degrees, clockwise from 12 o'clock. */
    fun centreAngle(index: Int): Float = index * SECTOR_ANGLE

    /** Sector index containing a point already known to be inside the double ring. */
    fun indexAt(x: Float, y: Float): Int {
        val degrees = Math.toDegrees(atan2(x.toDouble(), -y.toDouble())).toFloat()
        val shifted = ((degrees + HALF_SECTOR) % 360f + 360f) % 360f
        return (shifted / SECTOR_ANGLE).toInt().coerceIn(0, SECTOR_COUNT - 1)
    }

    fun hitTest(x: Float, y: Float): DartHit {
        val r = sqrt(x * x + y * y)
        if (r > DOUBLE_OUTER) return DartHit(null, 0, Ring.MISS, x = x, y = y)
        val number = numberAt(indexAt(x, y))
        return when {
            // The bull is one target; the ring still distinguishes the two scoring areas.
            r <= INNER_BULL -> DartHit(BULL, 1, Ring.INNER_BULL, x = x, y = y)
            r <= OUTER_BULL -> DartHit(BULL, 1, Ring.OUTER_BULL, x = x, y = y)
            r > DOUBLE_INNER -> DartHit(number, 2, Ring.DOUBLE, x = x, y = y)
            r >= TREBLE_INNER && r <= TREBLE_OUTER ->
                DartHit(number, 3, Ring.TREBLE, x = x, y = y)
            else -> DartHit(
                number = number,
                multiplier = 1,
                ring = Ring.SINGLE,
                singleBand = if (r > TREBLE_OUTER) SingleBand.LARGE else SingleBand.SMALL,
                x = x,
                y = y,
            )
        }
    }
}
