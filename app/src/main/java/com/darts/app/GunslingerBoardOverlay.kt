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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.darts.core.BoardGeometry

const val GUNSLINGER_PLAY_SCALE = 0.70f

/**
 * Highlights the revealed Gunslinger target and flashes the last dart. In Normal mode
 * [neighbourTargets] are drawn in a lighter shade so the two scoring side numbers are
 * visible without competing with the called target.
 */
@Composable
fun rememberGunslingerBoardOverlay(
    target: Int?,
    lastTarget: Int?,
    hitTick: Int,
    accent: Color = DartsPalette.Red,
    neighbourTargets: Set<Int> = emptySet(),
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "gunslinger-pulse").animateFloat(
        initialValue = 0.34f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            tween(480, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "gunslinger-alpha",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(hitTick) {
        if (hitTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }

    return remember(target, lastTarget, accent, neighbourTargets) {
        { centre, playRadius ->
            val neighbourColor = lerp(accent, Color.White, 0.48f)
            if (target != null) {
                neighbourTargets.forEach { neighbour ->
                    if (neighbour == BoardGeometry.BULL) {
                        drawCircle(
                            color = neighbourColor.copy(alpha = 0.10f + pulse.value * 0.12f),
                            radius = BoardGeometry.OUTER_BULL * playRadius,
                            center = centre,
                        )
                        drawCircle(
                            color = neighbourColor.copy(alpha = 0.28f + pulse.value * 0.22f),
                            radius = BoardGeometry.OUTER_BULL * playRadius,
                            center = centre,
                            style = Stroke(width = playRadius * 0.012f),
                        )
                    } else {
                        val index = BoardGeometry.SECTOR_ORDER.indexOf(neighbour)
                        if (index >= 0) {
                            val path = sectorPath(
                                index = index,
                                inner = BoardGeometry.OUTER_BULL,
                                outer = BoardGeometry.DOUBLE_OUTER,
                                radius = playRadius,
                                cx = centre.x,
                                cy = centre.y,
                            )
                            drawPath(
                                path = path,
                                color = neighbourColor.copy(alpha = 0.08f + pulse.value * 0.12f),
                            )
                            drawPath(
                                path = path,
                                color = neighbourColor.copy(alpha = 0.30f + pulse.value * 0.22f),
                                style = Stroke(width = playRadius * 0.010f),
                            )
                        }
                    }
                }
                if (target == BoardGeometry.BULL) {
                    drawCircle(
                        color = accent.copy(alpha = 0.22f + pulse.value * 0.30f),
                        radius = BoardGeometry.OUTER_BULL * playRadius,
                        center = centre,
                    )
                    drawCircle(
                        color = accent.copy(alpha = pulse.value),
                        radius = BoardGeometry.OUTER_BULL * playRadius,
                        center = centre,
                        style = Stroke(width = playRadius * 0.022f),
                    )
                } else {
                    val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
                    if (index >= 0) {
                        val path = sectorPath(
                            index = index,
                            inner = BoardGeometry.OUTER_BULL,
                            outer = BoardGeometry.DOUBLE_OUTER,
                            radius = playRadius,
                            cx = centre.x,
                            cy = centre.y,
                        )
                        drawPath(path, color = accent.copy(alpha = 0.16f + pulse.value * 0.24f))
                        drawPath(
                            path = path,
                            color = accent.copy(alpha = 0.70f + pulse.value * 0.28f),
                            style = Stroke(width = playRadius * 0.020f),
                        )
                    }
                }
            }

            if (flash.value > 0f && lastTarget != null) {
                val flashColor = Color.White.copy(alpha = flash.value * 0.50f)
                if (lastTarget == BoardGeometry.BULL) {
                    drawCircle(
                        color = flashColor,
                        radius = BoardGeometry.OUTER_BULL * playRadius,
                        center = centre,
                    )
                } else {
                    val index = BoardGeometry.SECTOR_ORDER.indexOf(lastTarget)
                    if (index >= 0) {
                        drawPath(
                            path = sectorPath(
                                index = index,
                                inner = BoardGeometry.OUTER_BULL,
                                outer = BoardGeometry.DOUBLE_OUTER,
                                radius = playRadius,
                                cx = centre.x,
                                cy = centre.y,
                            ),
                            color = flashColor,
                        )
                    }
                }
            }
        }
    }
}
