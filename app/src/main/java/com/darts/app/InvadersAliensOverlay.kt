package com.darts.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darts.core.AlienKill
import com.darts.core.BoardGeometry
import kotlin.math.cos
import kotlin.math.sin

const val INVADERS_PLAY_SCALE = 0.58f

@Composable
fun InvadersAliensOverlay(
    aliens: Map<Int, Int>,
    lastKills: List<AlienKill>,
    killTick: Int,
    modifier: Modifier = Modifier,
) {
    val bob = rememberInfiniteTransition(label = "alien-bob").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "alien-bob",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(killTick) {
        if (killTick > 0) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(520, easing = LinearEasing))
        }
    }

    Canvas(modifier) {
        val playRadius = minOf(size.width, size.height) / 2f * INVADERS_PLAY_SCALE
        val center = Offset(size.width / 2f, size.height / 2f)
        val cell = playRadius * 0.0145f
        val ringColors = listOf(DartsPalette.Green, DartsPalette.BlueBright, DartsPalette.Amber)

        for (i in 0 until BoardGeometry.SECTOR_COUNT) {
            val number = BoardGeometry.numberAt(i)
            val remaining = aliens[number] ?: 3
            val firstAlive = 3 - remaining
            val angle = Math.toRadians(BoardGeometry.centreAngle(i).toDouble())
            for (ring in 0 until 3) {
                if (ring < firstAlive) continue
                val radius = playRadius * (1.24f + ring * 0.14f)
                val x = center.x + sin(angle).toFloat() * radius
                val y = center.y - cos(angle).toFloat() * radius
                val bobOffset = (bob.value - 0.5f) * cell * 0.55f
                drawAlien(Offset(x, y + bobOffset), cell, ringColors[ring])
            }
        }

        if (lastKills.isNotEmpty() && flash.value > 0f) {
            val reveal = sin(flash.value * Math.PI).toFloat()
            lastKills.forEach { kill ->
                val index = BoardGeometry.SECTOR_ORDER.indexOf(kill.number)
                if (index < 0) return@forEach
                val angle = Math.toRadians(BoardGeometry.centreAngle(index).toDouble())
                val radius = playRadius * (1.24f + kill.index * 0.14f)
                val x = center.x + sin(angle).toFloat() * radius
                val y = center.y - cos(angle).toFloat() * radius
                val r = cell * (2f + 4f * flash.value)
                drawCircle(
                    color = ringColors[kill.index].copy(alpha = 0.65f * reveal),
                    radius = r,
                    center = Offset(x, y),
                    style = Stroke(width = cell * 0.9f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.8f * reveal),
                    radius = r * 0.55f,
                    center = Offset(x, y),
                    style = Stroke(width = cell * 0.55f),
                )
            }
        }
    }
}

private fun DrawScope.drawAlien(center: Offset, cell: Float, color: Color) {
    val pattern = listOf(
        "01010",
        "11111",
        "10101",
        "11111",
        "10001",
    )
    drawCircle(color.copy(alpha = 0.10f), cell * 3.2f, center)
    val left = center.x - cell * 2.5f
    val top = center.y - cell * 2.5f
    pattern.forEachIndexed { row, line ->
        line.forEachIndexed { col, pixel ->
            if (pixel == '1') {
                drawRect(
                    color = color,
                    topLeft = Offset(left + col * cell, top + row * cell),
                    size = Size(cell * 0.92f, cell * 0.92f),
                )
            }
        }
    }
}
