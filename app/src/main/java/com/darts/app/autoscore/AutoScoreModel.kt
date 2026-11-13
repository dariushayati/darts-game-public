package com.darts.app.autoscore

import com.darts.core.BoardGeometry
import com.darts.core.DartHit
import com.darts.core.Ring
import com.darts.core.SingleBand
import io.github.thomasff.bulltrack.core.domain.model.RingType
import io.github.thomasff.bulltrack.core.domain.model.ThrowResult
import io.github.thomasff.bulltrack.core.vision.PrepBoardGuide
import kotlin.math.hypot

/** User choices that affect the native engine for a round. */
data class AutoScoreSettings(
    val softBoard: Boolean = false,
    val forceCpu: Boolean = false,
    val debugImages: Boolean = false,
    val trackingIntervalMs: Long = 200L,
)

/**
 * One detected dart in this app's coordinate system. [needsCheck] mirrors BullTrack's
 * "tip on a line / fallback path" hint so the player can confirm or correct it.
 */
data class AutoScoredDart(
    val hit: DartHit,
    val needsCheck: Boolean = false,
)

sealed interface AutoScoreState {
    data object Idle : AutoScoreState
    data class PlacingCamera(val guide: PlacementGuide?) : AutoScoreState
    data object Calibrating : AutoScoreState
    data class Ready(val dartsOnBoard: Int = 0) : AutoScoreState
    data class DartDetected(
        val darts: List<AutoScoredDart>,
        val dartsOnBoard: Int,
    ) : AutoScoreState

    data class CameraMoved(val reason: String? = null) : AutoScoreState
    data object Redetecting : AutoScoreState
    data class TurnComplete(val darts: List<AutoScoredDart>) : AutoScoreState
    data class Error(val message: String) : AutoScoreState
}

data class PlacementGuide(
    val boardFound: Boolean,
    val boardInSquare: Boolean,
    val boardHeightShare: Float,
    val centred: Boolean,
    val message: String,
)

/**
 * BullTrack scores on a virtual board whose outer double edge is 0.92; this app's
 * [BoardGeometry] normalises the outer double edge to 1.0. Scaling the reported
 * position keeps a marker visually on the same ring without changing the score, which
 * always comes from the detected ring type.
 */
private const val BULLTRACK_OUTER_DOUBLE = 0.92f

fun ThrowResult.toAutoScoredDart(): AutoScoredDart {
    val position = scaledPosition()
    val ring = when (ring) {
        RingType.DOUBLE -> Ring.DOUBLE
        RingType.TRIPLE -> Ring.TREBLE
        RingType.OUTER_SINGLE -> Ring.SINGLE
        RingType.INNER_SINGLE -> Ring.SINGLE
        RingType.OUTER_BULL -> Ring.OUTER_BULL
        RingType.INNER_BULL -> Ring.INNER_BULL
        RingType.MISS -> Ring.MISS
    }

    if (ring == Ring.MISS) {
        return AutoScoredDart(
            hit = DartHit(null, 0, Ring.MISS, x = position?.first, y = position?.second),
            needsCheck = needsCheck,
        )
    }

    val number = when (ring) {
        Ring.INNER_BULL, Ring.OUTER_BULL -> BoardGeometry.BULL
        else -> sector.takeIf { it in 1..20 }
            ?: return AutoScoredDart(
                hit = DartHit(null, 0, Ring.MISS, x = position?.first, y = position?.second),
                needsCheck = true,
            )
    }
    val multiplier = when (ring) {
        Ring.DOUBLE -> 2
        Ring.TREBLE -> 3
        else -> 1
    }
    val singleBand = when (ring) {
        Ring.SINGLE -> if (this.ring == RingType.OUTER_SINGLE) SingleBand.LARGE else SingleBand.SMALL
        else -> null
    }
    return AutoScoredDart(
        hit = DartHit(
            number = number,
            multiplier = multiplier,
            ring = ring,
            singleBand = singleBand,
            x = position?.first,
            y = position?.second,
        ),
        needsCheck = needsCheck,
    )
}

fun missAutoScoredDart(needsCheck: Boolean = true): AutoScoredDart =
    AutoScoredDart(DartHit(null, 0, Ring.MISS), needsCheck = needsCheck)

private fun ThrowResult.scaledPosition(): Pair<Float, Float>? {
    val point = position ?: return null
    val scale = 1f / BULLTRACK_OUTER_DOUBLE
    val x = point.x * scale
    val y = point.y * scale
    val radius = hypot(x, y)
    return if (radius <= 1f || radius == 0f) {
        x to y
    } else {
        (x / radius) to (y / radius)
    }
}

fun PrepBoardGuide.toPlacementGuide(): PlacementGuide {
    val box = boardBox
    if (box == null) {
        return PlacementGuide(
            boardFound = false,
            boardInSquare = false,
            boardHeightShare = 0f,
            centred = false,
            message = "No board found. Aim the camera at the whole board.",
        )
    }
    val heightShare = if (bandHeight > 0) box.height().toFloat() / bandHeight else 0f
    val inSquare = box.left >= 0 && box.top >= bandTop &&
        box.right <= frameWidth && box.bottom <= bandTop + bandHeight
    val centred = kotlin.math.abs(box.centerX() - frameWidth / 2f) <= frameWidth * 0.15f
    val message = when {
        !inSquare -> "Keep the whole board in frame."
        heightShare < 0.35f -> "Move closer to the board."
        heightShare > 0.85f -> "Move further from the board."
        !centred -> "Centre the board in the frame."
        else -> "Board looks good. Start the round."
    }
    return PlacementGuide(
        boardFound = true,
        boardInSquare = inSquare,
        boardHeightShare = heightShare,
        centred = centred,
        message = message,
    )
}
