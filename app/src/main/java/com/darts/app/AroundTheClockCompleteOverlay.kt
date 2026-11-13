package com.darts.app

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AroundTheClockMode
import com.darts.core.AroundTheClockState
import com.darts.core.Player

@Composable
fun AroundTheClockCompleteOverlay(
    state: AroundTheClockState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val winner = state.winnerId?.let { id -> state.players.firstOrNull { it.id == id } }
    val solo = state.players.size == 1
    val total = AroundTheClockMode.TARGETS.size
    val standings = state.players.sortedWith(
        compareByDescending<Player> { state.progressFor(it.id) }
            .thenBy { it.name },
    )
    val accent = winner?.let { playerColor(it.colorIndex) } ?: DartsPalette.DartCyan

    Box(
        modifier = Modifier.fillMaxSize().background(DartsPalette.Scrim),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
        ) {
            ShardBackdrop()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.OverlayCard)
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = if (solo) "CLOCK COMPLETE" else "AROUND THE CLOCK",
                    color = DartsPalette.DartCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = winner?.name?.uppercase()?.ifBlank { "PLAYER" } ?: "NO WINNER",
                    color = DartsPalette.Text,
                    fontSize = 30.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = when {
                        solo -> "COMPLETED 1–20 + BULL"
                        winner != null -> "FIRST AROUND THE CLOCK"
                        else -> "RUN COMPLETE"
                    },
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = (winner?.let { state.progressFor(it.id) }
                        ?: standings.firstOrNull()?.let { state.progressFor(it.id) }
                        ?: 0).toString(),
                    color = DartsPalette.Text,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp,
                )
                Text(
                    text = "TARGETS HIT",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AroundTheClockEndStat("TARGETS", "$total", Modifier.weight(1f))
                    AroundTheClockEndStat("DARTS", "${state.dartsThrown}", Modifier.weight(1f))
                    AroundTheClockEndStat("PLAYERS", "${state.players.size}", Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "PROGRESS",
                        color = DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    standings.forEach { player ->
                        val progress = state.progressFor(player.id)
                        AroundTheClockStandingRow(
                            player = player,
                            progress = progress,
                            total = total,
                            next = if (progress >= total) "FINISHED" else "NEXT ${state.labelFor(player.id)}",
                            isWinner = winner?.id == player.id,
                            accent = accent,
                        )
                    }
                }

                Button(
                    onClick = onPlayAgain,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DartsPalette.DartCyan,
                        contentColor = DartsPalette.onColor(DartsPalette.DartCyan),
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
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = DartsPalette.Muted,
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        text = "CHANGE SETUP",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AroundTheClockEndStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(DartsPalette.OverlayPanel, RoundedCornerShape(12.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = DartsPalette.Text,
            fontSize = 18.sp,
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
private fun AroundTheClockStandingRow(
    player: Player,
    progress: Int,
    total: Int,
    next: String,
    isWinner: Boolean,
    accent: androidx.compose.ui.graphics.Color,
) {
    val color = playerColor(player.colorIndex)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isWinner) accent.copy(alpha = 0.12f) else DartsPalette.OverlayPanel,
            )
            .border(
                1.dp,
                if (isWinner) accent.copy(alpha = 0.5f) else DartsPalette.Border,
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .height(8.dp)
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(DartsPalette.ChipOff),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((progress.toFloat() / total).coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(color),
            )
        }
        Text(
            text = player.name,
            color = DartsPalette.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
        Text(
            text = "$progress/$total",
            color = if (isWinner) accent else DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = next,
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}
