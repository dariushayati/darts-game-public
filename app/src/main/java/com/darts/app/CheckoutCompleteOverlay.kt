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
import com.darts.core.CheckoutPath
import com.darts.core.CheckoutState

@Composable
fun CheckoutCompleteOverlay(
    state: CheckoutState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val player = state.players.firstOrNull()
    val accent = player?.let { playerColor(it.colorIndex) } ?: DartsPalette.Amber

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
                    text = "CHECKOUT COMPLETE",
                    color = DartsPalette.Amber,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = player?.name?.uppercase()?.ifBlank { "PLAYER" } ?: "PLAYER",
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
                    text = "${state.rules.difficulty.name} · ${state.total} CHECKOUTS",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = "${state.completed}",
                    color = DartsPalette.Text,
                    fontSize = 64.sp,
                    lineHeight = 64.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-3).sp,
                )
                Text(
                    text = "CHECKOUTS CLEARED",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CheckoutEndStat("CHECKOUTS", "${state.completed}/${state.total}", Modifier.weight(1f))
                    CheckoutEndStat("DARTS", "${state.dartsThrown}", Modifier.weight(1f))
                    CheckoutEndStat("MODE", state.rules.difficulty.name, Modifier.weight(1f))
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = "CLEARED",
                        color = DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    state.order.forEach { path ->
                        CheckoutClearedRow(path)
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
private fun CheckoutEndStat(
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
private fun CheckoutClearedRow(path: CheckoutPath) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(DartsPalette.OverlayPanel)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "✓",
            color = DartsPalette.GreenText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "${path.score}",
            color = DartsPalette.Text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.widthIn(min = 34.dp),
        )
        Text(
            text = path.label,
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
