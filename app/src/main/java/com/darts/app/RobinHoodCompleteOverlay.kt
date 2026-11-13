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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.Player
import com.darts.core.RobinHoodState

@Composable
fun RobinHoodCompleteOverlay(
    state: RobinHoodState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val winner = state.winnerId?.let { id -> state.players.firstOrNull { it.id == id } }
    val solo = state.players.size == 1
    val standings = state.players.sortedWith(
        compareByDescending<Player> { state.scores[it.id] ?: 0 }
            .thenBy { it.name },
    )
    val featured = winner ?: standings.firstOrNull()
    val featuredScore = featured?.let { state.scores[it.id] } ?: 0
    val accent = featured?.let { playerColor(it.colorIndex) } ?: DartsPalette.Amber

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
                .border(1.5.dp, DartsPalette.Amber.copy(alpha = 0.6f), RoundedCornerShape(24.dp)),
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
                    text = if (solo) "RUN COMPLETE" else "MATCH COMPLETE",
                    color = DartsPalette.Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = featured?.name?.uppercase()?.ifBlank { "PLAYER" } ?: "PLAYER",
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
                        solo -> "FINAL SCORE"
                        winner != null -> "WINS"
                        else -> "NO OUTRIGHT WINNER"
                    },
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = featuredScore.toString(),
                    color = DartsPalette.Text,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp,
                )
                Text(
                    text = "POINTS",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RobinHoodEndStat("ROUNDS", "${state.rules.rounds}", Modifier.weight(1f))
                    RobinHoodEndStat("DARTS", "${state.dartsThrown}", Modifier.weight(1f))
                    RobinHoodEndStat(
                        "BEST",
                        (state.scores.values.maxOrNull() ?: 0).toString(),
                        Modifier.weight(1f),
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
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
                        RobinHoodStandingRow(
                            player = player,
                            score = state.scores[player.id] ?: 0,
                            isWinner = winner?.id == player.id,
                            accent = accent,
                        )
                    }
                }

                Button(
                    onClick = onPlayAgain,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DartsPalette.Amber,
                        contentColor = DartsPalette.onColor(DartsPalette.Amber),
                    ),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("PLAY AGAIN", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                }
                OutlinedButton(
                    onClick = onExit,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DartsPalette.Muted),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("CHANGE SETUP", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                }
            }
        }
    }
}

@Composable
private fun RobinHoodEndStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(DartsPalette.OverlayPanel, RoundedCornerShape(12.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
            .padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = DartsPalette.Text, fontSize = 17.sp, fontWeight = FontWeight.Black)
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
private fun RobinHoodStandingRow(
    player: Player,
    score: Int,
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
    }
}
