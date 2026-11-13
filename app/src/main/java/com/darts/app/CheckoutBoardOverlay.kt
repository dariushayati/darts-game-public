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
import com.darts.core.CheckoutDart
import com.darts.core.Ring

const val CHECKOUT_PLAY_SCALE = 0.74f

/** Highlights the exact ring and number required by the current checkout dart. */
@Composable
fun rememberCheckoutBoardOverlay(
    target: CheckoutDart?,
    hitTick: Int,
    accent: Color = DartsPalette.Amber,
): DrawScope.(centre: Offset, playRadius: Float) -> Unit {
    val pulse = rememberInfiniteTransition(label = "checkout-pulse").animateFloat(
        initialValue = 0.40f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            tween(760, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "checkout-alpha",
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
            drawCheckoutOverlay(target, accent, pulse.value, flash.value, centre, playRadius)
        }
    }
}

private fun DrawScope.drawCheckoutOverlay(
    target: CheckoutDart?,
    accent: Color,
    pulse: Float,
    flash: Float,
    centre: Offset,
    playRadius: Float,
) {
    val dart = target ?: return
    if (dart.number == BoardGeometry.BULL) {
        val radius = playRadius * BoardGeometry.INNER_BULL
        drawCircle(
            color = accent.copy(alpha = 0.45f + pulse * 0.30f),
            radius = radius,
            center = centre,
        )
        drawCircle(
            color = accent.copy(alpha = 0.92f + pulse * 0.08f),
            radius = radius,
            center = centre,
            style = Stroke(width = playRadius * 0.018f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.48f + pulse * 0.38f),
            radius = playRadius * BoardGeometry.INNER_BULL * 1.35f,
            center = centre,
            style = Stroke(width = playRadius * 0.008f),
        )
        if (flash > 0f) {
            drawCircle(
                color = Color.White.copy(alpha = flash * 0.55f),
                radius = radius,
                center = centre,
            )
        }
        return
    }

    val index = BoardGeometry.SECTOR_ORDER.indexOf(dart.number)
    if (index < 0) return
    val inner: Float
    val outer: Float
    when (dart.ring) {
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
            outer = BoardGeometry.DOUBLE_INNER
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
