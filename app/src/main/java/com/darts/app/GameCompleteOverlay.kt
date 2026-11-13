package com.darts.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.GameState
import com.darts.core.Player
import com.darts.core.lockedCount
import com.darts.core.ownedCount
import kotlin.random.Random

@Composable
fun GameCompleteOverlay(
    state: GameState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    showPlayAgain: Boolean = true,
    exitLabel: String = "BACK TO SETUP",
) {
    val winner = state.winnerId?.let { id -> state.players.firstOrNull { it.id == id } }
    val accent = winner?.let { playerColor(it.colorIndex) } ?: DartsPalette.BlueBright
    val standings = state.players.sortedWith(
        compareByDescending<Player> { state.ownedCount(it.id) }
            .thenByDescending { state.lockedCount(it.id) }
            .thenByDescending { player ->
                state.sectors.values
                    .filter { it.ownerId == player.id }
                    .sumOf { it.shields }
            },
    )
    val top = standings.firstOrNull()
    val points = winner?.let { state.ownedCount(it.id) } ?: top?.let { state.ownedCount(it.id) } ?: 0
    val locked = winner?.let { state.lockedCount(it.id) } ?: top?.let { state.lockedCount(it.id) } ?: 0
    val reason = if (state.rules.endOnBoardFull && state.boardFull()) {
        "Board complete"
    } else {
        "Dart limit reached"
    }

    val entrance = remember { Animatable(0f) }
    val confetti = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        confetti.animateTo(1f, tween(2300, easing = LinearEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DartsPalette.Scrim),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.30f),
                    radius = size.minDimension * 0.62f,
                ),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.30f),
            )
        }

        val density = LocalDensity.current.density
        val pieces = remember(accent) {
            val random = Random(11)
            val colors = listOf(
                accent,
                DartsPalette.Green,
                DartsPalette.BlueBright,
                DartsPalette.Amber,
                Color.White,
            )
            List(32) {
                ConfettiPiece(
                    x = random.nextFloat(),
                    delay = random.nextFloat() * 0.30f,
                    drift = (random.nextFloat() - 0.5f) * 0.08f,
                    width = 2.5f + random.nextFloat() * 4.5f,
                    height = 5f + random.nextFloat() * 9f,
                    color = colors[random.nextInt(colors.size)],
                )
            }
        }


        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .graphicsLayer {
                        alpha = entrance.value
                        val scale = 0.94f + 0.06f * entrance.value
                        scaleX = scale
                        scaleY = scale
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
            ) {
                ShardBackdrop()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DartsPalette.OverlayCard)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "MATCH COMPLETE",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                        )
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .background(accent.copy(alpha = 0.18f), CircleShape)
                                .border(2.dp, accent.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = winner?.name?.take(1)?.uppercase()?.ifBlank { "=" } ?: "=",
                                color = accent,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                    Text(
                        text = winner?.name?.uppercase()?.ifBlank { "PLAYER" } ?: "DRAW",
                        color = DartsPalette.Text,
                        fontSize = 34.sp,
                        lineHeight = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (winner != null) "WINNER" else "NO OUTRIGHT WINNER",
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = reason,
                        color = DartsPalette.Muted,
                        fontSize = 11.sp,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatTile("POINTS", points, accent, Modifier.weight(1f))
                        StatTile("LOCKED", locked, DartsPalette.Text, Modifier.weight(1f))
                        StatTile("DARTS", state.dartsThrown, DartsPalette.Text, Modifier.weight(1f))
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "FINAL STANDINGS",
                            color = DartsPalette.Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                        )
                        standings.forEach { player ->
                            StandingRow(
                                player = player,
                                score = state.ownedCount(player.id),
                                locked = state.lockedCount(player.id),
                                isWinner = winner?.id == player.id,
                                accent = accent,
                            )
                        }
                    }

                    if (showPlayAgain) {
                        Button(
                            onClick = onPlayAgain,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DartsPalette.Blue,
                                contentColor = DartsPalette.onColor(DartsPalette.Blue),
                            ),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Text(
                                text = "PLAY AGAIN",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onExit,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, DartsPalette.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = DartsPalette.Muted,
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text(
                            text = exitLabel,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                        )
                    }
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val progress = confetti.value
            pieces.forEach { piece ->
                val local = ((progress - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val x = (piece.x + piece.drift * local).coerceIn(0f, 0.99f) * size.width
                val y = (-0.06f + local * 1.18f) * size.height
                drawRect(
                    color = piece.color.copy(alpha = (1f - local * 0.85f).coerceIn(0f, 1f)),
                    topLeft = Offset(x, y),
                    size = Size(piece.width * density, piece.height * density),
                )
            }
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val delay: Float,
    val drift: Float,
    val width: Float,
    val height: Float,
    val color: Color,
)

@Composable
private fun StatTile(label: String, value: Int, valueColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(DartsPalette.OverlayPanel, RoundedCornerShape(12.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            color = valueColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = label,
            color = DartsPalette.Muted,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun StandingRow(
    player: Player,
    score: Int,
    locked: Int,
    isWinner: Boolean,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isWinner) accent.copy(alpha = 0.16f) else DartsPalette.OverlayPanel)
            .border(
                width = if (isWinner) 1.5.dp else 1.dp,
                color = if (isWinner) accent.copy(alpha = 0.8f) else DartsPalette.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(8.dp).background(playerColor(player.colorIndex), CircleShape))
        Text(
            text = player.name,
            color = if (isWinner) DartsPalette.Text else DartsPalette.Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$score PTS",
            color = if (isWinner) accent else DartsPalette.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "$locked L",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
