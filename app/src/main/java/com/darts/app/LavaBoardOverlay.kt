package com.darts.app

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darts.core.BoardGeometry
import kotlin.math.cos
import kotlin.math.sin

const val LAVA_PLAY_SCALE = 0.74f

@Composable
fun rememberLavaBoardOverlay(
    lavaNumbers: Set<Int>,
    lastBurned: List<Int>,
    burnTick: Int,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "lava-pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "lava-pulse",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(burnTick) {
        if (burnTick > 0) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(620, easing = LinearEasing))
        }
    }

    return remember(lavaNumbers, lastBurned) {
        { centre, playRadius ->
            drawLavaBoardOverlay(lavaNumbers, lastBurned, pulse.value, flash.value, centre, playRadius)
        }
    }
}

private fun DrawScope.drawLavaBoardOverlay(
    lavaNumbers: Set<Int>,
    lastBurned: List<Int>,
    pulse: Float,
    flash: Float,
    centre: Offset,
    playRadius: Float,
) {
    lavaNumbers.forEach { number ->
        val index = BoardGeometry.SECTOR_ORDER.indexOf(number)
        if (index < 0) return@forEach
        val path = sectorPath(index, 0.095f, 1.16f, playRadius, centre.x, centre.y)
        val angle = Math.toRadians(BoardGeometry.centreAngle(index).toDouble())
        val outer = Offset(
            centre.x + sin(angle).toFloat() * playRadius,
            centre.y - cos(angle).toFloat() * playRadius,
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFFFFD54F).copy(alpha = 0.92f),
                    DartsPalette.Lava.copy(alpha = 0.88f),
                    Color(0xFF7A1B00).copy(alpha = 0.94f),
                ),
                start = centre,
                end = outer,
            ),
        )
        drawPath(
            path = path,
            color = Color(0xFFFFB300).copy(alpha = 0.20f + pulse * 0.35f),
            style = Stroke(width = playRadius * 0.010f),
        )
    }

    if (lastBurned.isNotEmpty() && flash > 0f) {
        val reveal = sin(flash * Math.PI).toFloat()
        lastBurned.forEach { number ->
            val index = BoardGeometry.SECTOR_ORDER.indexOf(number)
            if (index < 0) return@forEach
            val angle = Math.toRadians(BoardGeometry.centreAngle(index).toDouble())
            val point = Offset(
                centre.x + sin(angle).toFloat() * playRadius * 1.02f,
                centre.y - cos(angle).toFloat() * playRadius * 1.02f,
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.85f * reveal),
                radius = playRadius * (0.02f + 0.10f * flash),
                center = point,
            )
            drawCircle(
                color = Color(0xFFFF6D00).copy(alpha = 0.75f * reveal),
                radius = playRadius * (0.06f + 0.20f * flash),
                center = point,
                style = Stroke(width = playRadius * 0.014f),
            )
        }
    }
}
