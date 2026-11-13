package com.darts.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val HeartPath = Path().apply {
    moveTo(12f, 21.35f)
    cubicTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
    cubicTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
    cubicTo(9.24f, 3f, 10.91f, 3.81f, 12f, 5.09f)
    cubicTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
    cubicTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
    cubicTo(22f, 12.28f, 18.6f, 15.36f, 12f, 21.35f)
    close()
}

@Composable
fun HeartsRow(
    lives: Int,
    max: Int,
    color: Color,
    modifier: Modifier = Modifier,
    heartSize: Dp = 14.dp,
    gap: Dp = 2.dp,
) {
    val safeMax = max.coerceAtLeast(1)
    val filled = lives.coerceIn(0, safeMax)
    Canvas(
        modifier = modifier.size(
            width = heartSize * safeMax + gap * (safeMax - 1),
            height = heartSize,
        ),
    ) {
        val heartPx = heartSize.toPx()
        val gapPx = gap.toPx()
        val factor = heartPx / 24f
        for (index in 0 until safeMax) {
            translate(left = index * (heartPx + gapPx)) {
                scale(factor, factor, pivot = Offset.Zero) {
                    if (index < filled) {
                        drawPath(HeartPath, color)
                    } else {
                        drawPath(
                            path = HeartPath,
                            color = color.copy(alpha = 0.75f),
                            style = Stroke(width = 2.4f),
                        )
                    }
                }
            }
        }
    }
}
