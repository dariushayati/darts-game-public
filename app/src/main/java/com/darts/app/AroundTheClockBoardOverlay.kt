package com.darts.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darts.core.BoardGeometry

const val AROUND_THE_CLOCK_PLAY_SCALE = 0.74f

/**
 * Highlights the current Around the Clock target. Numbers light the whole scoring
 * wedge so any single, double or treble reads as valid; the bull lights both rings.
 */
@Composable
fun rememberAroundTheClockBoardOverlay(
    target: Int?,
    hitTick: Int,
    accent: Color = DartsPalette.DartCyan,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "around-the-clock-pulse").animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            tween(760, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "around-the-clock-alpha",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(hitTick) {
        if (hitTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    return remember(target, accent) {
        { centre, playRadius ->
            drawAroundTheClockOverlay(target, accent, pulse.value, flash.value, centre, playRadius)
        }
    }
}

private fun DrawScope.drawAroundTheClockOverlay(
    target: Int?,
    accent: Color,
    pulse: Float,
    flash: Float,
    centre: Offset,
    playRadius: Float,
) {
    when (target) {
        null -> return
        BoardGeometry.BULL -> {
            val radius = playRadius * BoardGeometry.OUTER_BULL
            drawCircle(
                color = accent.copy(alpha = 0.42f + pulse * 0.28f),
                radius = radius,
                center = centre,
            )
            drawCircle(
                color = accent.copy(alpha = 0.92f + pulse * 0.08f),
                radius = radius,
                center = centre,
                style = Stroke(width = playRadius * 0.025f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.45f + pulse * 0.40f),
                radius = playRadius * BoardGeometry.INNER_BULL,
                center = centre,
                style = Stroke(width = playRadius * 0.009f),
            )
            if (flash > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = flash * 0.55f),
                    radius = radius,
                    center = centre,
                )
            }
        }

        else -> {
            val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
            if (index < 0) return
            val path = sectorPath(
                index = index,
                inner = BoardGeometry.OUTER_BULL,
                outer = BoardGeometry.DOUBLE_OUTER,
                radius = playRadius,
                cx = centre.x,
                cy = centre.y,
            )
            drawPath(path = path, color = accent.copy(alpha = 0.42f + pulse * 0.28f))
            drawPath(
                path = path,
                color = accent.copy(alpha = 0.92f + pulse * 0.08f),
                style = Stroke(width = playRadius * 0.03f),
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.45f + pulse * 0.40f),
                style = Stroke(width = playRadius * 0.009f),
            )
            if (flash > 0f) {
                drawPath(path = path, color = Color.White.copy(alpha = flash * 0.55f))
            }
        }
    }
}
