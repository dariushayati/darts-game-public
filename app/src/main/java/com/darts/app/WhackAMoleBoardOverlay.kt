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
import com.darts.core.MoleTarget
import com.darts.core.Ring

const val WHACK_A_MOLE_PLAY_SCALE = 0.74f

/**
 * Highlights the mole the current player must whack next. A normal mole lights the
 * whole number wedge; a hard-mode mole lights only the required double or treble
 * band. Returns a [DrawScope] callback so [BoardCanvas] draws it above the board but
 * beneath the magnifier loupe.
 */
@Composable
fun rememberWhackAMoleBoardOverlay(
    target: MoleTarget?,
    whackTick: Int,
    accent: Color = DartsPalette.Mole,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "mole-pulse").animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            tween(720, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "mole-alpha",
    )
    val flash = remember { Animatable(0f) }
    LaunchedEffect(whackTick) {
        if (whackTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(560, easing = FastOutSlowInEasing))
        }
    }

    return remember(target, accent) {
        { centre, playRadius ->
            drawWhackAMoleBoardOverlay(target, accent, pulse.value, flash.value, centre, playRadius)
        }
    }
}

private fun DrawScope.drawWhackAMoleBoardOverlay(
    target: MoleTarget?,
    accent: Color,
    pulse: Float,
    flash: Float,
    centre: Offset,
    playRadius: Float,
) {
    val mole = target ?: return
    val index = BoardGeometry.SECTOR_ORDER.indexOf(mole.number)
    if (index < 0) return

    val inner: Float
    val outer: Float
    when (mole.ring) {
        Ring.DOUBLE -> {
            inner = BoardGeometry.DOUBLE_INNER
            outer = BoardGeometry.DOUBLE_OUTER
        }
        Ring.TREBLE -> {
            inner = BoardGeometry.TREBLE_INNER
            outer = BoardGeometry.TREBLE_OUTER
        }
        else -> {
            inner = BoardGeometry.OUTER_BULL
            outer = BoardGeometry.DOUBLE_OUTER
        }
    }

    val path = sectorPath(
        index = index,
        inner = inner,
        outer = outer,
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
