package com.darts.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.BoardGeometry
import com.darts.core.DartHit
import com.darts.core.GameState
import com.darts.core.Ring
import com.darts.core.SingleBand
import kotlin.math.cos
import kotlin.math.sin

/** Playing-surface radius as a fraction of the available half-dimension. */
private const val PLAY_SCALE = 0.74f
private const val NUMBER_RING = 1.16f

@Composable
fun BoardCanvas(
    state: GameState,
    lastTarget: Int?,
    flashTick: Int,
    onHit: (DartHit) -> Unit,
    modifier: Modifier = Modifier,
    recentTargets: List<DartHit> = emptyList(),
    crackTarget: Int? = null,
    crackColor: Color? = null,
    crackCount: Int = 0,
    crackTick: Int = 0,
    enabled: Boolean = true,
    playScale: Float = PLAY_SCALE,
    accent: Color = DartsPalette.Green,
    zonePalette: BoardZonePalette? = null,
    boardOverlay: (DrawScope.(centre: Offset, playRadius: Float) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()

    // One animated level per target: drives fill opacity and colour as shields accumulate.
    val levelStates = BoardGeometry.TARGETS.map { target ->
        animateFloatAsState(
            targetValue = state.sector(target).shields.toFloat(),
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            label = "shield-level-$target",
        )
    }
    val levels = BoardGeometry.TARGETS.zip(levelStates).toMap()

    val hasLiveBoardSignal = state.sectors.values.any { it.shields > 0 } || recentTargets.isNotEmpty()
    val pulse: State<Float> = if (hasLiveBoardSignal) {
        rememberInfiniteTransition(label = "lock-pulse").animateFloat(
            initialValue = 0.35f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                tween(1100, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "lock-alpha",
        )
    } else {
        remember { mutableFloatStateOf(0.55f) }
    }

    val crack = remember { Animatable(0f) }
    LaunchedEffect(crackTick) {
        if (crackTick > 0 && crackTarget != null) {
            crack.snapTo(0f)
            crack.animateTo(1f, tween(durationMillis = 780, easing = LinearEasing))
        }
    }

    val flash = remember { Animatable(0f) }
    LaunchedEffect(flashTick) {
        if (flashTick > 0) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(durationMillis = 650, easing = LinearEasing))
        }
    }

    var loupeFinger by remember { mutableStateOf<Offset?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val currentOnHit by rememberUpdatedState(onHit)
    LaunchedEffect(enabled) {
        if (!enabled) {
            loupeFinger = null
            showManualEntry = false
        }
    }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "Dartboard"
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction("Enter dart manually") {
                            showManualEntry = true
                            true
                        },
                    )
                }
            }
            .pointerInput(enabled) {
            if (!enabled) return@pointerInput

            fun hitAt(pos: Offset): DartHit {
                val radius = minOf(size.width, size.height) / 2f * playScale
                return if (radius > 0f) {
                    BoardGeometry.hitTest(
                        x = (pos.x - size.width / 2f) / radius,
                        y = (pos.y - size.height / 2f) / radius,
                    )
                } else {
                    DartHit(null, 0, Ring.MISS)
                }
            }

            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var position = down.position
                val outcome = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull GestureOutcome.Up
                        position = change.position
                        if (!change.pressed) return@withTimeoutOrNull GestureOutcome.Up
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            return@withTimeoutOrNull GestureOutcome.Moved
                        }
                    }
                }
                when (outcome) {
                    null -> {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            loupeFinger = position
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                position = change.position
                                loupeFinger = position
                                change.consume()
                                if (!change.pressed) break
                            }
                        } finally {
                            loupeFinger = null
                        }
                        currentOnHit(hitAt(position))
                    }
                    GestureOutcome.Up -> currentOnHit(hitAt(position))
                    GestureOutcome.Moved -> {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            position = change.position
                            if (!change.pressed) break
                        }
                        currentOnHit(hitAt(position))
                    }
                }
            }
        },
    ) {
        val boardRadius = minOf(size.width, size.height) / 2f * playScale
        val boardCentre = Offset(size.width / 2f, size.height / 2f)
        drawBoard(
            state = state,
            levels = levels,
            pulse = pulse,
            crack = crack,
            flash = flash,
            lastTarget = lastTarget,
            recentTargets = recentTargets,
            crackTarget = crackTarget,
            crackColor = crackColor,
            crackCount = crackCount,
            measurer = measurer,
            centre = boardCentre,
            playRadius = boardRadius,
            accent = accent,
            zonePalette = zonePalette,
        )
        boardOverlay?.invoke(this, boardCentre, boardRadius)
        val finger = loupeFinger
        if (finger != null) {
            drawMagnifier(
                finger = finger,
                boardCentre = boardCentre,
                boardRadius = boardRadius,
                state = state,
                levels = levels,
                pulse = pulse,
                crack = crack,
                flash = flash,
                lastTarget = lastTarget,
                recentTargets = recentTargets,
                crackTarget = crackTarget,
                crackColor = crackColor,
                crackCount = crackCount,
                measurer = measurer,
                accent = accent,
                boardOverlay = boardOverlay,
                zonePalette = zonePalette,
            )
        }
    }

    if (showManualEntry) {
        ManualEntryDialog(
            onDismiss = { showManualEntry = false },
            onHit = { hit ->
                currentOnHit(hit)
                showManualEntry = false
            },
        )
    }
}

@Composable
private fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onHit: (DartHit) -> Unit,
) {
    var ring by remember { mutableStateOf(ManualRing.OUTER_SINGLE) }

    fun hitFor(number: Int): DartHit = when (ring) {
        ManualRing.OUTER_SINGLE -> DartHit(number, 1, Ring.SINGLE, SingleBand.LARGE)
        ManualRing.INNER_SINGLE -> DartHit(number, 1, Ring.SINGLE, SingleBand.SMALL)
        ManualRing.DOUBLE -> DartHit(number, 2, Ring.DOUBLE)
        ManualRing.TREBLE -> DartHit(number, 3, Ring.TREBLE)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DartsPalette.Surface,
        titleContentColor = DartsPalette.Text,
        textContentColor = DartsPalette.Muted,
        title = {
            Text("ENTER DART", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Choose a ring, then a number. Bull and miss score immediately.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until 4).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (1..5).forEach { column ->
                                val number = row * 5 + column
                                Button(
                                    onClick = { onHit(hitFor(number)) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DartsPalette.ChipOff,
                                        contentColor = DartsPalette.Text,
                                    ),
                                    modifier = Modifier.weight(1f).height(48.dp),
                                ) {
                                    Text(
                                        text = "$number",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                    )
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ManualRing.entries.forEach { option ->
                        FilterChip(
                            selected = ring == option,
                            onClick = { ring = option },
                            label = {
                                Text(
                                    text = option.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DartsPalette.ChipOff,
                                labelColor = DartsPalette.Muted,
                                selectedContainerColor = DartsPalette.Blue.copy(alpha = 0.16f),
                                selectedLabelColor = DartsPalette.BlueBright,
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = {
                            onHit(DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL))
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DartsPalette.Green.copy(alpha = 0.18f),
                            contentColor = DartsPalette.GreenText,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("BULL 25", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = {
                            onHit(DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL))
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DartsPalette.Green.copy(alpha = 0.18f),
                            contentColor = DartsPalette.GreenText,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("BULL 50", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Button(
                        onClick = { onHit(DartHit(null, 0, Ring.MISS)) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DartsPalette.Red.copy(alpha = 0.18f),
                            contentColor = DartsPalette.Text,
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text("MISS", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        },
    )
}

private enum class ManualRing(val label: String) {
    OUTER_SINGLE("OUTER"),
    INNER_SINGLE("INNER"),
    DOUBLE("DOUBLE"),
    TREBLE("TREBLE"),
}

private fun DrawScope.drawBoard(
    state: GameState,
    levels: Map<Int, State<Float>>,
    pulse: State<Float>,
    crack: Animatable<Float, AnimationVector1D>,
    flash: Animatable<Float, AnimationVector1D>,
    lastTarget: Int?,
    recentTargets: List<DartHit>,
    crackTarget: Int?,
    crackColor: Color?,
    crackCount: Int,
    measurer: TextMeasurer,
    centre: Offset,
    playRadius: Float,
    accent: Color,
    zonePalette: BoardZonePalette? = null,
) {
    val outerRadius = playRadius * NUMBER_RING
    val cx = centre.x
    val cy = centre.y

    // Green halo behind the board.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                accent.copy(alpha = 0.24f),
                accent.copy(alpha = 0.06f),
                Color.Transparent,
            ),
            center = centre,
            radius = playRadius * 1.30f,
        ),
        radius = playRadius * 1.30f,
        center = centre,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(DartsPalette.Blue.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(cx, cy + playRadius * 0.8f),
            radius = playRadius * 1.35f,
        ),
        radius = playRadius * 1.35f,
        center = centre,
    )

    // Surround + green rim.
    drawCircle(Color(0xFF05080A), outerRadius, centre)
    drawCircle(
        color = accent.copy(alpha = 0.55f),
        radius = outerRadius,
        center = centre,
        style = Stroke(width = playRadius * 0.012f),
    )
    drawCircle(
        color = accent.copy(alpha = 0.16f),
        radius = outerRadius * 0.985f,
        center = centre,
        style = Stroke(width = playRadius * 0.02f),
    )

    // Playing surface.
    drawCircle(Color(0xFF0B0E12), playRadius, centre)

    val red = Color(0xFFD32F2F)
    val green = Color(0xFF2E7D32)
    val cream = Color(0xFFE9DCC0)
    val black = Color(0xFF0E1013)

    for (i in 0 until BoardGeometry.SECTOR_COUNT) {
        val start = 270f + i * BoardGeometry.SECTOR_ANGLE - BoardGeometry.HALF_SECTOR + 0.9f
        val sweep = BoardGeometry.SECTOR_ANGLE - 1.8f
        val single = if (i % 2 == 0) black else cream
        val ring = if (i % 2 == 0) red else green

        band(single, BoardGeometry.OUTER_BULL, BoardGeometry.TREBLE_INNER, start, sweep, playRadius, cx, cy)
        band(ring, BoardGeometry.TREBLE_INNER, BoardGeometry.TREBLE_OUTER, start, sweep, playRadius, cx, cy)
        band(single, BoardGeometry.TREBLE_OUTER, BoardGeometry.DOUBLE_INNER, start, sweep, playRadius, cx, cy)
        band(ring, BoardGeometry.DOUBLE_INNER, BoardGeometry.DOUBLE_OUTER, start, sweep, playRadius, cx, cy)
    }

    drawCircle(green, playRadius * BoardGeometry.OUTER_BULL, centre)
    drawCircle(red, playRadius * BoardGeometry.INNER_BULL, centre)

    zonePalette?.let { palette ->
        drawScoringZones(
            palette = palette,
            playRadius = playRadius,
            centre = centre,
        )
    }

    val wire = Color(0xFF232830)
    for (i in 0 until BoardGeometry.SECTOR_COUNT) {
        val a = Math.toRadians((270.0 + i * BoardGeometry.SECTOR_ANGLE - BoardGeometry.HALF_SECTOR))
        drawLine(
            color = wire,
            start = Offset(cx + sin(a).toFloat() * playRadius * BoardGeometry.OUTER_BULL, cy - cos(a).toFloat() * playRadius * BoardGeometry.OUTER_BULL),
            end = Offset(cx + sin(a).toFloat() * playRadius, cy - cos(a).toFloat() * playRadius),
            strokeWidth = 1.4f,
        )
    }

    // Numbers live on the black number ring, outside the double.
    val numberStyle = TextStyle(
        color = Color(0xFFF2F2F2),
        fontSize = (playRadius * 0.058f).toSp(),
        fontWeight = FontWeight.Bold,
    )
    for (i in 0 until BoardGeometry.SECTOR_COUNT) {
        val angle = Math.toRadians(BoardGeometry.centreAngle(i).toDouble())
        val lx = cx + sin(angle).toFloat() * playRadius * 1.075f
        val ly = cy - cos(angle).toFloat() * playRadius * 1.075f
        val layout = measurer.measure(
            text = AnnotatedString(BoardGeometry.numberAt(i).toString()),
            style = numberStyle,
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(lx - layout.size.width / 2f, ly - layout.size.height / 2f),
        )
    }

    // Claimed sectors: the whole wedge is washed in the owner's colour. Opacity and
    // brightness are driven by the animated shield level, so growth visibly deepens.
    state.sectors.forEach { (target, sector) ->
        val owner = sector.ownerId ?: return@forEach
        val color = state.players.firstOrNull { it.id == owner }
            ?.let { playerColor(it.colorIndex) } ?: Color.Gray
        val level = levels[target]?.value ?: sector.shields.toFloat()
        val locked = state.isLocked(target)
        val fill = lerp(color, Color.White, (0.04f + (level / 3f) * 0.16f).coerceIn(0f, 0.22f))
        val alpha = (0.62f + 0.11f * level).coerceIn(0f, 0.96f)

        val path = if (target == BoardGeometry.BULL) {
            null
        } else {
            val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
            if (index < 0) return@forEach
            sectorPath(index, 0.095f, 0.985f, playRadius, cx, cy)
        }

        if (path == null) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(fill, Color.White, 0.30f).copy(alpha = alpha),
                        fill.copy(alpha = alpha * 0.85f),
                    ),
                    center = centre,
                    radius = playRadius * BoardGeometry.OUTER_BULL,
                ),
                radius = playRadius * BoardGeometry.OUTER_BULL * 0.98f,
                center = centre,
            )
            drawCircle(
                color = Color(0xFF05070D).copy(alpha = 0.85f),
                radius = playRadius * BoardGeometry.OUTER_BULL,
                center = centre,
                style = Stroke(width = playRadius * 0.009f),
            )
            if (target == lastTarget && flash.value > 0f) {
                drawCircle(
                    color = Color.White.copy(alpha = flash.value * 0.32f),
                    radius = playRadius * BoardGeometry.OUTER_BULL,
                    center = centre,
                )
            }
            if (locked) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.20f + pulse.value * 0.45f),
                    radius = playRadius * BoardGeometry.OUTER_BULL,
                    center = centre,
                    style = Stroke(width = playRadius * 0.016f),
                )
            }
        } else {
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(
                        lerp(fill, Color.White, 0.34f).copy(alpha = alpha),
                        fill.copy(alpha = alpha),
                    ),
                    center = centre,
                    radius = playRadius,
                ),
            )
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                    start = Offset(cx, cy - playRadius),
                    end = Offset(cx, cy + playRadius * 0.4f),
                ),
            )
            drawPath(
                path = path,
                color = Color(0xFF05070D).copy(alpha = 0.88f),
                style = Stroke(width = playRadius * 0.017f),
            )
            drawPath(
                path = path,
                color = fill.copy(alpha = 0.98f),
                style = Stroke(width = playRadius * 0.009f),
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.48f),
                style = Stroke(width = playRadius * 0.003f),
            )
            if (locked) {
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.20f + pulse.value * 0.45f),
                    style = Stroke(width = playRadius * 0.024f),
                )
            }
            if (target == lastTarget && flash.value > 0f) {
                drawPath(path, Color.White.copy(alpha = flash.value * 0.38f))
            }
        }

        val point = if (target == BoardGeometry.BULL) {
            centre
        } else {
            val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
            val angle = Math.toRadians(BoardGeometry.centreAngle(index).toDouble())
            Offset(
                cx + sin(angle).toFloat() * playRadius * 0.52f,
                cy - cos(angle).toFloat() * playRadius * 0.52f,
            )
        }
        val base = if (target == BoardGeometry.BULL) 0.026f else 0.050f
        val grow = if (target == BoardGeometry.BULL) 0.003f else 0.006f
        val shake = if (target == crackTarget) {
            sin(crack.value * 34f) * (1f - crack.value) * playRadius * 0.009f
        } else {
            0f
        }
        drawShield(
            center = Offset(point.x + shake, point.y),
            halfWidth = playRadius * (base + grow * level) *
                ShieldVisualPolicy.flashScale(target, lastTarget, flash.value),
            color = fill,
            fill = ShieldVisualPolicy.fillFraction(level),
            count = sector.shields,
            measurer = measurer,
            pulse = pulse.value,
            locked = locked,
        )
    }

    // Shatter effect: shaking, spreading cracks, and a fading ghost if the target went neutral.
    val shatterTarget = crackTarget
    val shatterColor = crackColor
    if (shatterTarget != null && shatterColor != null) {
        val progress = crack.value
        val reveal = sin(progress * Math.PI).toFloat()
        if (reveal > 0f) {
            val index = if (shatterTarget == BoardGeometry.BULL) -1 else
                BoardGeometry.SECTOR_ORDER.indexOf(shatterTarget)
            val neutral = state.sector(shatterTarget).ownerId == null
            val shake = sin(progress * 34f) * (1f - progress) * playRadius * 0.009f

            if (neutral) {
                if (index >= 0) {
                    val path = sectorPath(index, 0.095f, 0.985f, playRadius, cx, cy)
                    drawPath(path, shatterColor.copy(alpha = 0.45f * reveal))
                    drawPath(
                        path = path,
                        color = shatterColor.copy(alpha = 0.8f * reveal),
                        style = Stroke(width = playRadius * 0.012f),
                    )
                    val point = sectorPoint(index, 0.55f, playRadius, cx, cy)
                    val ghostWidth = playRadius * 0.050f
                    val ghost = shieldPath(Offset(point.x + shake, point.y), ghostWidth)
                    drawPath(ghost, shatterColor.copy(alpha = 0.55f * reveal))
                    drawPath(
                        path = ghost,
                        color = Color.White.copy(alpha = 0.35f * reveal),
                        style = Stroke(width = ghostWidth * 0.12f),
                    )
                    drawCount(
                        center = Offset(point.x + shake, point.y),
                        halfWidth = ghostWidth,
                        count = crackCount,
                        alpha = reveal,
                        measurer = measurer,
                    )
                } else {
                    drawCircle(
                        color = shatterColor.copy(alpha = 0.55f * reveal),
                        radius = playRadius * BoardGeometry.OUTER_BULL,
                        center = centre,
                    )
                }
            }

            if (index >= 0) {
                drawPath(
                    path = sectorPath(index, 0.095f, 0.985f, playRadius, cx, cy),
                    color = Color.White.copy(alpha = 0.30f * reveal),
                )
            }

            drawCrack(index, playRadius, cx, cy, progress)
        }
    }

    // Darts from the current visit, at their exact landing point when known.
    recentTargets.forEachIndexed { index, hit ->
        if (hit.number == null && (hit.x == null || hit.y == null)) return@forEachIndexed
        drawHitDot(
            point = hitDotPoint(hit, cx, cy, playRadius),
            radius = playRadius * 0.026f,
            pulse = pulse.value,
            newest = index == recentTargets.lastIndex,
        )
    }
}

private fun DrawScope.drawScoringZones(
    palette: BoardZonePalette,
    playRadius: Float,
    centre: Offset,
) {
    drawZoneRing(
        color = palette.double,
        inner = BoardGeometry.DOUBLE_INNER,
        outer = BoardGeometry.DOUBLE_OUTER,
        playRadius = playRadius,
        centre = centre,
    )
    drawZoneRing(
        color = palette.largeSingle,
        inner = BoardGeometry.TREBLE_OUTER,
        outer = BoardGeometry.DOUBLE_INNER,
        playRadius = playRadius,
        centre = centre,
    )
    drawZoneRing(
        color = palette.treble,
        inner = BoardGeometry.TREBLE_INNER,
        outer = BoardGeometry.TREBLE_OUTER,
        playRadius = playRadius,
        centre = centre,
    )
    drawZoneRing(
        color = palette.smallSingle,
        inner = BoardGeometry.OUTER_BULL,
        outer = BoardGeometry.TREBLE_INNER,
        playRadius = playRadius,
        centre = centre,
    )
    drawCircle(
        color = palette.outerBull,
        radius = playRadius * BoardGeometry.OUTER_BULL,
        center = centre,
    )
    drawCircle(
        color = palette.innerBull,
        radius = playRadius * BoardGeometry.INNER_BULL,
        center = centre,
    )
    drawCircle(
        color = Color(0xFF0B0E12),
        radius = playRadius * BoardGeometry.OUTER_BULL,
        center = centre,
        style = Stroke(width = playRadius * 0.009f),
    )
    drawCircle(
        color = Color(0xFF0B0E12),
        radius = playRadius * BoardGeometry.INNER_BULL,
        center = centre,
        style = Stroke(width = playRadius * 0.007f),
    )
}

private fun DrawScope.drawZoneRing(
    color: Color,
    inner: Float,
    outer: Float,
    playRadius: Float,
    centre: Offset,
) {
    val innerRadius = playRadius * inner
    val outerRadius = playRadius * outer
    val midRadius = (innerRadius + outerRadius) / 2f
    val bandWidth = (outerRadius - innerRadius).coerceAtLeast(1f)
    drawCircle(
        color = color,
        radius = midRadius,
        center = centre,
        style = Stroke(width = bandWidth),
    )
    drawCircle(
        color = Color(0xFF0B0E12),
        radius = outerRadius,
        center = centre,
        style = Stroke(width = playRadius * 0.006f),
    )
    drawCircle(
        color = Color(0xFF0B0E12),
        radius = innerRadius,
        center = centre,
        style = Stroke(width = playRadius * 0.006f),
    )
}

private fun DrawScope.drawMagnifier(
    finger: Offset,
    boardCentre: Offset,
    boardRadius: Float,
    state: GameState,
    levels: Map<Int, State<Float>>,
    pulse: State<Float>,
    crack: Animatable<Float, AnimationVector1D>,
    flash: Animatable<Float, AnimationVector1D>,
    lastTarget: Int?,
    recentTargets: List<DartHit>,
    crackTarget: Int?,
    crackColor: Color?,
    crackCount: Int,
    measurer: TextMeasurer,
    accent: Color,
    zonePalette: BoardZonePalette? = null,
    boardOverlay: (DrawScope.(centre: Offset, playRadius: Float) -> Unit)? = null,
) {
    val loupeRadius = minOf(76.dp.toPx(), size.minDimension * 0.24f)
    val pad = loupeRadius + 4.dp.toPx()
    // Always above the finger; it may overflow the board canvas into the panels above.
    val cy = finger.y - loupeRadius * 2.15f
    val cx = finger.x.coerceIn(pad, size.width - pad)
    val loupeCentre = Offset(cx, cy)
    val zoom = 2.4f


    val clip = Path().apply { addOval(Rect(center = loupeCentre, radius = loupeRadius)) }
    clipPath(clip) {
        val zoomedRadius = boardRadius * zoom
        val zoomedCentre = loupeCentre - (finger - boardCentre) * zoom
        drawBoard(
            state = state,
            levels = levels,
            pulse = pulse,
            crack = crack,
            flash = flash,
            lastTarget = lastTarget,
            recentTargets = recentTargets,
            crackTarget = crackTarget,
            crackColor = crackColor,
            crackCount = crackCount,
            measurer = measurer,
            centre = zoomedCentre,
            playRadius = zoomedRadius,
            accent = accent,
            zonePalette = zonePalette,
        )
        boardOverlay?.invoke(this, zoomedCentre, zoomedRadius)
    }

    drawCircle(
        color = DartsPalette.BlueBright.copy(alpha = 0.9f),
        radius = loupeRadius,
        center = loupeCentre,
        style = Stroke(width = 2.dp.toPx()),
    )
    val arm = loupeRadius * 0.12f
    drawLine(Color.White, loupeCentre - Offset(arm, 0f), loupeCentre + Offset(arm, 0f), strokeWidth = 1.5.dp.toPx())
    drawLine(Color.White, loupeCentre - Offset(0f, arm), loupeCentre + Offset(0f, arm), strokeWidth = 1.5.dp.toPx())
    drawCircle(Color.White, loupeRadius * 0.035f, loupeCentre)

    val hit = BoardGeometry.hitTest(
        x = (finger.x - boardCentre.x) / boardRadius,
        y = (finger.y - boardCentre.y) / boardRadius,
    )
    val style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
    val layout = measurer.measure(AnnotatedString(loupeLabel(hit)), style)
    val padH = 10.dp.toPx()
    val padV = 5.dp.toPx()
    val pillW = layout.size.width + padH * 2
    val pillH = layout.size.height + padV * 2
    val pillX = loupeCentre.x - pillW / 2f
    val pillY = loupeCentre.y + loupeRadius - pillH - 8.dp.toPx()
    drawRoundRect(
        color = DartsPalette.Surface.copy(alpha = 0.94f),
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f),
    )
    drawRoundRect(
        color = DartsPalette.BlueBright.copy(alpha = 0.9f),
        topLeft = Offset(pillX, pillY),
        size = Size(pillW, pillH),
        cornerRadius = CornerRadius(pillH / 2f),
        style = Stroke(width = 1.5.dp.toPx()),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(pillX + padH, pillY + padV),
    )
    // Draw a dot at the finger position to show where the dart will land
    drawCircle(
        color = DartsPalette.BlueBright.copy(alpha = 0.9f),
        radius = 8.dp.toPx(),
        center = finger,
    )
}

private fun loupeLabel(hit: DartHit): String {
    val number = hit.number ?: return "MISS"
    if (number == BoardGeometry.BULL) return "BULL"
    return when (hit.multiplier) {
        3 -> "T$number"
        2 -> "D$number"
        else -> "S$number"
    }
}

private enum class GestureOutcome { Up, Moved }


internal fun sectorPath(
    index: Int,
    inner: Float,
    outer: Float,
    radius: Float,
    cx: Float,
    cy: Float,
): Path {
    val start = 270f + index * BoardGeometry.SECTOR_ANGLE - BoardGeometry.HALF_SECTOR + 0.9f
    val sweep = BoardGeometry.SECTOR_ANGLE - 1.8f
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

/**
 * Screen position of a dart dot. Manual taps carry an exact normalised landing point;
 * Autodarts hits have no coordinates, so fall back to the ring's mid-radius at the
 * sector's centre angle.
 */
private fun hitDotPoint(hit: DartHit, cx: Float, cy: Float, playRadius: Float): Offset {
    val x = hit.x
    val y = hit.y
    if (x != null && y != null) return Offset(cx + x * playRadius, cy + y * playRadius)

    if (hit.ring == Ring.INNER_BULL || hit.ring == Ring.OUTER_BULL) return Offset(cx, cy)
    val number = hit.number ?: return Offset(cx, cy)
    val fraction = when (hit.ring) {
        Ring.DOUBLE -> (BoardGeometry.DOUBLE_INNER + BoardGeometry.DOUBLE_OUTER) / 2f
        Ring.TREBLE -> (BoardGeometry.TREBLE_INNER + BoardGeometry.TREBLE_OUTER) / 2f
        Ring.SINGLE -> when (hit.singleBand) {
            SingleBand.LARGE -> (BoardGeometry.TREBLE_OUTER + BoardGeometry.DOUBLE_INNER) / 2f
            SingleBand.SMALL -> (BoardGeometry.OUTER_BULL + BoardGeometry.TREBLE_INNER) / 2f
            null -> 0.78f
        }
        else -> 0.78f
    }
    val sectorIndex = BoardGeometry.SECTOR_ORDER.indexOf(number)
    val angle = Math.toRadians(BoardGeometry.centreAngle(sectorIndex).toDouble())
    return Offset(
        cx + sin(angle).toFloat() * playRadius * fraction,
        cy - cos(angle).toFloat() * playRadius * fraction,
    )
}

private fun sectorPoint(index: Int, fraction: Float, radius: Float, cx: Float, cy: Float): Offset {
    val angle = Math.toRadians(BoardGeometry.centreAngle(index).toDouble())
    return Offset(
        cx + sin(angle).toFloat() * radius * fraction,
        cy - cos(angle).toFloat() * radius * fraction,
    )
}

private fun shieldPath(center: Offset, w: Float): Path = Path().apply {
    moveTo(center.x - w, center.y - w * 0.88f)
    lineTo(center.x + w, center.y - w * 0.88f)
    lineTo(center.x + w * 0.82f, center.y + w * 0.12f)
    quadraticBezierTo(center.x + w * 0.62f, center.y + w * 0.72f, center.x, center.y + w * 1.12f)
    quadraticBezierTo(center.x - w * 0.62f, center.y + w * 0.72f, center.x - w * 0.82f, center.y + w * 0.12f)
    close()
}

private fun DrawScope.drawCount(
    center: Offset,
    halfWidth: Float,
    count: Int,
    alpha: Float,
    measurer: TextMeasurer,
) {
    val style = TextStyle(
        color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
        fontSize = (halfWidth * 1.15f).toSp(),
        fontWeight = FontWeight.Black,
    )
    val layout = measurer.measure(AnnotatedString(count.toString()), style)
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            center.x - layout.size.width / 2f,
            center.y - layout.size.height / 2f - halfWidth * 0.02f,
        ),
    )
}

private fun crackPoints(index: Int, playRadius: Float, cx: Float, cy: Float): List<Offset> {
    val base = BoardGeometry.centreAngle(index)
    val points = ArrayList<Offset>()
    val steps = 12
    for (k in 0..steps) {
        val t = k / steps.toFloat()
        val radius = (0.98f - 0.86f * t) * playRadius
        val jitter = sin(t * 9.1f + index * 2.3f) * 3.4f + sin(t * 21.7f + index) * 1.3f
        val angle = Math.toRadians((base + jitter).toDouble())
        points.add(Offset(cx + sin(angle).toFloat() * radius, cy - cos(angle).toFloat() * radius))
    }
    return points
}

private fun DrawScope.drawCrack(
    index: Int,
    playRadius: Float,
    cx: Float,
    cy: Float,
    progress: Float,
) {
    val reveal = sin(progress * Math.PI).toFloat()
    if (reveal <= 0f) return

    if (index < 0) {
        for (k in 0 until 6) {
            val angle = Math.toRadians(k * 60.0 + 13.0)
            val end = Offset(
                cx + sin(angle).toFloat() * playRadius * BoardGeometry.OUTER_BULL,
                cy - cos(angle).toFloat() * playRadius * BoardGeometry.OUTER_BULL,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.5f * reveal),
                start = Offset(cx, cy),
                end = end,
                strokeWidth = playRadius * 0.014f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f * reveal),
                start = Offset(cx, cy),
                end = end,
                strokeWidth = playRadius * 0.007f,
                cap = StrokeCap.Round,
            )
        }
        return
    }

    val points = crackPoints(index, playRadius, cx, cy)
    val visible = (progress * 1.7f * points.size).toInt().coerceIn(2, points.size)
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until visible) path.lineTo(points[i].x, points[i].y)

    drawPath(
        path = path,
        color = Color.Black.copy(alpha = 0.55f * reveal),
        style = Stroke(width = playRadius * 0.016f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.95f * reveal),
        style = Stroke(width = playRadius * 0.008f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    if (progress > 0.25f) {
        val branchReveal = ((progress - 0.25f) / 0.35f).coerceIn(0f, 1f)
        for (k in listOf(3, 6, 8)) {
            if (k >= visible) continue
            val start = points[k]
            val side = if (k % 2 == 0) 1f else -1f
            val end = Offset(
                start.x + side * playRadius * 0.045f * branchReveal,
                start.y + playRadius * 0.035f * branchReveal,
            )
            drawLine(
                color = Color.Black.copy(alpha = 0.45f * reveal),
                start = start,
                end = end,
                strokeWidth = playRadius * 0.012f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Color.White.copy(alpha = 0.9f * reveal),
                start = start,
                end = end,
                strokeWidth = playRadius * 0.006f,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.drawShield(
    center: Offset,
    halfWidth: Float,
    color: Color,
    fill: Float,
    count: Int,
    measurer: TextMeasurer,
    pulse: Float,
    locked: Boolean,
) {
    val w = halfWidth
    val path = shieldPath(center, w)
    val topY = center.y - w * 0.88f
    val bottomY = center.y + w * 1.12f
    val fullH = bottomY - topY
    val fraction = fill.coerceIn(0f, 1f)
    val fillTop = bottomY - fraction * fullH

    if (locked) {
        drawCircle(color.copy(alpha = 0.16f + pulse * 0.22f), w * 2.8f, center)
    }
    drawPath(path, Color(0xFF05070D), style = Stroke(width = w * 0.30f))
    clipPath(path) {
        // Empty shell underneath the fill.
        drawRect(
            color = lerp(color, Color(0xFF05070D), 0.82f).copy(alpha = 0.95f),
            topLeft = Offset(center.x - w, topY),
            size = Size(w * 2f, fullH),
        )
        if (fraction > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        lerp(color, Color.White, 0.40f),
                        color,
                        lerp(color, Color.Black, 0.28f),
                    ),
                    startY = fillTop,
                    endY = bottomY,
                ),
                topLeft = Offset(center.x - w, fillTop),
                size = Size(w * 2f, bottomY - fillTop),
            )
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(center.x - w, fillTop),
                end = Offset(center.x + w, fillTop),
                strokeWidth = w * 0.06f,
            )
        }
    }
    drawPath(path, Color.White.copy(alpha = 0.38f), style = Stroke(width = w * 0.10f))
    clipPath(path) {
        drawLine(
            color = Color.White.copy(alpha = 0.22f),
            start = Offset(center.x - w * 1.1f, center.y - w * 0.35f),
            end = Offset(center.x + w * 1.1f, center.y - w * 0.95f),
            strokeWidth = w * 0.40f,
        )
    }
    val style = TextStyle(
        color = Color.White,
        fontSize = (w * 1.15f).toSp(),
        fontWeight = FontWeight.Black,
    )
    val layout = measurer.measure(AnnotatedString(count.toString()), style)
    val tx = center.x - layout.size.width / 2f
    val ty = center.y - layout.size.height / 2f - w * 0.02f
    drawText(
        textLayoutResult = layout,
        color = Color.Black.copy(alpha = 0.65f),
        topLeft = Offset(tx + w * 0.07f, ty + w * 0.07f),
    )
    drawText(textLayoutResult = layout, topLeft = Offset(tx, ty))
    if (locked) {
        drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = w * 0.14f))
        val lx = center.x
        val ly = center.y + w * 0.72f
        drawRoundRect(
            color = Color(0xFFFFD54F),
            topLeft = Offset(lx - w * 0.24f, ly),
            size = Size(w * 0.48f, w * 0.36f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        )
        drawArc(
            color = Color(0xFFFFD54F),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(lx - w * 0.17f, ly - w * 0.26f),
            size = Size(w * 0.34f, w * 0.34f),
            style = Stroke(width = w * 0.11f),
        )
    }
}

private fun DrawScope.drawHitDot(point: Offset, radius: Float, pulse: Float, newest: Boolean) {
    drawCircle(DartsPalette.DartCyan.copy(alpha = 0.22f), radius * 1.7f, point)
    drawCircle(Color.White.copy(alpha = 0.95f), radius * 0.55f, point)
    drawCircle(DartsPalette.DartCyan, radius * 0.85f, point, style = Stroke(width = radius * 0.30f))
    if (newest) {
        drawCircle(
            color = DartsPalette.DartCyan.copy(alpha = pulse * 0.9f),
            radius = radius * 1.9f,
            center = point,
            style = Stroke(width = radius * 0.26f),
        )
    }
}

private fun DrawScope.band(
    color: Color,
    inner: Float,
    outer: Float,
    startAngle: Float,
    sweepAngle: Float,
    radius: Float,
    cx: Float,
    cy: Float,
) {
    val mid = (inner + outer) / 2f * radius
    val width = (outer - inner) * radius
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        useCenter = false,
        topLeft = Offset(cx - mid, cy - mid),
        size = Size(mid * 2f, mid * 2f),
        style = Stroke(width = width),
    )
}
