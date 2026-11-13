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
import com.darts.core.RingRunMode
import com.darts.core.RingRunRound

const val RING_RUN_PLAY_SCALE = 0.70f

@Composable
fun rememberRingRunBoardOverlay(
    round: RingRunRound,
    hitTick: Int,
    hitPoints: Int?,
    accent: Color = DartsPalette.Yellow,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "ring-run-pulse").animateFloat(
        initialValue = 0.24f,
        targetValue = 0.58f,
        animationSpec = infiniteRepeatable(
            tween(850, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "ring-run-alpha",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(hitTick) {
        if (hitTick > 0 && (hitPoints ?: 0) > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
        }
    }

    return remember(round, accent, hitPoints) {
        { centre, playRadius ->
            drawRingRunBoardOverlay(round, accent, pulse.value, flash.value, centre, playRadius)
        }
    }
}

private fun DrawScope.drawRingRunBoardOverlay(
    round: RingRunRound,
    accent: Color,
    pulse: Float,
    flash: Float,
    centre: Offset,
    playRadius: Float,
) {
    when (round) {
        RingRunRound.DOUBLE_AND_LARGE_SINGLE -> {
            drawRing(
                inner = BoardGeometry.TREBLE_OUTER,
                outer = BoardGeometry.DOUBLE_INNER,
                playRadius = playRadius,
                centre = centre,
                accent = accent,
                pulse = pulse,
                flash = flash,
            )
            drawRing(
                inner = BoardGeometry.DOUBLE_INNER,
                outer = BoardGeometry.DOUBLE_OUTER,
                playRadius = playRadius,
                centre = centre,
                accent = accent,
                pulse = pulse,
                flash = flash,
            )
        }

        RingRunRound.LARGE_SINGLE -> drawRing(
            inner = BoardGeometry.TREBLE_OUTER,
            outer = BoardGeometry.DOUBLE_INNER,
            playRadius = playRadius,
            centre = centre,
            accent = accent,
            pulse = pulse,
            flash = flash,
            numbers = RingRunMode.ROUND_TWO_NUMBERS,
        )

        RingRunRound.SMALL_SINGLE -> drawRing(
            inner = BoardGeometry.OUTER_BULL,
            outer = BoardGeometry.TREBLE_INNER,
            playRadius = playRadius,
            centre = centre,
            accent = accent,
            pulse = pulse,
            flash = flash,
        )

        RingRunRound.BULL -> drawBull(
            playRadius = playRadius,
            centre = centre,
            accent = accent,
            pulse = pulse,
            flash = flash,
        )
    }
}

private fun DrawScope.drawRing(
    inner: Float,
    outer: Float,
    playRadius: Float,
    centre: Offset,
    accent: Color,
    pulse: Float,
    flash: Float,
    numbers: Set<Int>? = null,
) {
    for (index in 0 until BoardGeometry.SECTOR_COUNT) {
        if (numbers != null && BoardGeometry.numberAt(index) !in numbers) continue
        val path = sectorPath(
            index = index,
            inner = inner,
            outer = outer,
            radius = playRadius,
            cx = centre.x,
            cy = centre.y,
        )
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.26f + pulse * 0.20f),
        )
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.70f + pulse * 0.28f),
            style = Stroke(width = playRadius * 0.012f),
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.36f + pulse * 0.34f),
            style = Stroke(width = playRadius * 0.004f),
        )
        if (flash > 0f) {
            drawPath(path, Color.White.copy(alpha = flash * 0.46f))
        }
    }

    if (numbers == null) {
        drawCircle(
            color = accent.copy(alpha = 0.55f + pulse * 0.30f),
            radius = playRadius * outer,
            center = centre,
            style = Stroke(width = playRadius * 0.012f),
        )
        drawCircle(
            color = accent.copy(alpha = 0.55f + pulse * 0.30f),
            radius = playRadius * inner,
            center = centre,
            style = Stroke(width = playRadius * 0.012f),
        )
    }
}

private fun DrawScope.drawBull(
    playRadius: Float,
    centre: Offset,
    accent: Color,
    pulse: Float,
    flash: Float,
) {
    val outerRadius = playRadius * BoardGeometry.OUTER_BULL
    drawCircle(
        color = accent.copy(alpha = 0.28f + pulse * 0.24f),
        radius = outerRadius,
        center = centre,
    )
    drawCircle(
        color = accent.copy(alpha = 0.78f + pulse * 0.20f),
        radius = outerRadius,
        center = centre,
        style = Stroke(width = playRadius * 0.018f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.42f + pulse * 0.34f),
        radius = playRadius * BoardGeometry.INNER_BULL,
        center = centre,
        style = Stroke(width = playRadius * 0.010f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.54f + pulse * 0.32f),
        radius = playRadius * 0.026f,
        center = centre,
    )
    if (flash > 0f) {
        drawCircle(
            color = Color.White.copy(alpha = flash * 0.62f),
            radius = outerRadius,
            center = centre,
        )
    }
}
