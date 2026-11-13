package com.darts.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darts.core.BoardGeometry
import com.darts.core.CricketMode
import com.darts.core.Player

const val CRICKET_PLAY_SCALE = 0.70f

private const val CRICKET_INNER = 0.095f
private const val CRICKET_OUTER = 0.985f

private data class CricketMarkStatus(
    val player: Player,
    val marks: Int,
) {
    val closed: Boolean get() = marks >= CricketMode.MARKS_TO_CLOSE
    val progress: Float get() = marks.toFloat() / CricketMode.MARKS_TO_CLOSE
}

/**
 * Paints each Cricket target with the status of the players who have marks on it.
 *
 * Each player owns an angular slice of the wedge. The slice fills outward from the bull
 * as their marks grow and fills completely when the target is closed. Two closed players
 * split the wedge 50/50 side by side; more players split it into equal angular slices.
 * Partial marks show a faint full slice plus a solid inner portion so progress is
 * visible without hiding the board.
 */
@Composable
fun rememberCricketBoardOverlay(
    players: List<Player>,
    marks: Map<Int, Map<Int, Int>>,
    lastTarget: Int?,
    hitTick: Int,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val statusesByTarget = remember(players, marks) {
        CricketMode.TARGETS.associateWith { target ->
            players.mapNotNull { player ->
                val playerMarks = marks[player.id]?.get(target) ?: 0
                if (playerMarks <= 0) null else CricketMarkStatus(player, playerMarks)
            }
        }
    }

    val flash = remember { Animatable(0f) }
    LaunchedEffect(hitTick) {
        if (hitTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(520, easing = FastOutSlowInEasing))
        }
    }

    return remember(statusesByTarget, lastTarget) {
        { centre, playRadius ->
            statusesByTarget.forEach { (target, statuses) ->
                if (statuses.isNotEmpty()) {
                    if (target == BoardGeometry.BULL) {
                        drawBullStatus(statuses, centre, playRadius)
                    } else {
                        drawSectorStatus(target, statuses, centre, playRadius)
                    }
                }
            }

            if (flash.value > 0f && lastTarget != null) {
                if (lastTarget == BoardGeometry.BULL) {
                    drawCircle(
                        color = Color.White.copy(alpha = flash.value * 0.55f),
                        radius = BoardGeometry.OUTER_BULL * playRadius,
                        center = centre,
                    )
                } else {
                    val index = BoardGeometry.SECTOR_ORDER.indexOf(lastTarget)
                    if (index >= 0) {
                        val path = sectorPath(
                            index = index,
                            inner = CRICKET_INNER,
                            outer = CRICKET_OUTER,
                            radius = playRadius,
                            cx = centre.x,
                            cy = centre.y,
                        )
                        drawPath(path, Color.White.copy(alpha = flash.value * 0.42f))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSectorStatus(
    target: Int,
    statuses: List<CricketMarkStatus>,
    centre: Offset,
    playRadius: Float,
) {
    val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
    if (index < 0) return

    val sectorStart = 270f + index * BoardGeometry.SECTOR_ANGLE -
        BoardGeometry.HALF_SECTOR + 0.9f
    val totalSweep = BoardGeometry.SECTOR_ANGLE - 1.8f
    val slice = totalSweep / statuses.size
    val gap = if (statuses.size > 1) 0.35f else 0f

    statuses.forEachIndexed { position, status ->
        val color = playerColor(status.player.colorIndex)
        val start = sectorStart + slice * position + gap
        val sweep = (slice - gap * 2f).coerceAtLeast(0.5f)

        if (!status.closed) {
            drawPath(
                path = cricketSlicePath(
                    start = start,
                    sweep = sweep,
                    inner = CRICKET_INNER,
                    outer = CRICKET_OUTER,
                    radius = playRadius,
                    cx = centre.x,
                    cy = centre.y,
                ),
                color = color.copy(alpha = 0.10f),
            )
        }

        if (status.progress <= 0f) return@forEachIndexed
        val filledOuter = CRICKET_INNER +
            (CRICKET_OUTER - CRICKET_INNER) * status.progress
        val path = cricketSlicePath(
            start = start,
            sweep = sweep,
            inner = CRICKET_INNER,
            outer = filledOuter,
            radius = playRadius,
            cx = centre.x,
            cy = centre.y,
        )
        drawPath(
            path = path,
            color = color.copy(alpha = if (status.closed) 0.66f else 0.40f),
        )
        drawPath(
            path = path,
            color = color.copy(alpha = if (status.closed) 0.98f else 0.72f),
            style = Stroke(width = playRadius * if (status.closed) 0.008f else 0.005f),
        )
        if (status.closed) {
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.18f),
                style = Stroke(width = playRadius * 0.003f),
            )
        }
    }
}

private fun cricketSlicePath(
    start: Float,
    sweep: Float,
    inner: Float,
    outer: Float,
    radius: Float,
    cx: Float,
    cy: Float,
): Path {
    val outerRadius = outer * radius
    val innerRadius = inner * radius
    val path = Path()
    path.arcTo(
        rect = Rect(cx - outerRadius, cy - outerRadius, cx + outerRadius, cy + outerRadius),
        startAngleDegrees = start,
        sweepAngleDegrees = sweep,
        forceMoveTo = true,
    )
    path.arcTo(
        rect = Rect(cx - innerRadius, cy - innerRadius, cx + innerRadius, cy + innerRadius),
        startAngleDegrees = start + sweep,
        sweepAngleDegrees = -sweep,
        forceMoveTo = false,
    )
    path.close()
    return path
}

private fun DrawScope.drawBullStatus(
    statuses: List<CricketMarkStatus>,
    centre: Offset,
    playRadius: Float,
) {
    val radius = BoardGeometry.OUTER_BULL * playRadius
    val sweep = 360f / statuses.size
    statuses.forEachIndexed { position, status ->
        val color = playerColor(status.player.colorIndex)
        val start = -90f + sweep * position

        if (!status.closed) {
            drawArc(
                color = color.copy(alpha = 0.10f),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2f, radius * 2f),
            )
        }

        if (status.progress <= 0f) return@forEachIndexed
        val filled = radius * status.progress
        drawArc(
            color = color.copy(alpha = if (status.closed) 0.66f else 0.40f),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(centre.x - filled, centre.y - filled),
            size = Size(filled * 2f, filled * 2f),
        )
        drawArc(
            color = color.copy(alpha = if (status.closed) 0.98f else 0.72f),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = true,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = playRadius * if (status.closed) 0.008f else 0.005f),
        )
        if (status.closed) {
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = radius,
                center = centre,
                style = Stroke(width = playRadius * 0.003f),
            )
        }
    }
}
