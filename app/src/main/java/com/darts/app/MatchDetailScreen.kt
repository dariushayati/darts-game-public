package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.MatchPlayerRecord
import com.darts.core.MatchRecord

/** One stored match: final standings and per-player numbers. */
@Composable
fun MatchDetailScreen(record: MatchRecord, onBack: () -> Unit) {
    val accent = record.accent()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DartsPalette.BackgroundDeep,
                        DartsPalette.Background,
                        DartsPalette.TintNavy,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LayoutPolicy.CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(DartsPalette.Blue.copy(alpha = 0.20f))
                    .border(1.5.dp, DartsPalette.BlueBright.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(DartsPalette.BlueBright, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "←",
                        color = DartsPalette.NamePlate,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "BACK TO HISTORY",
                    color = DartsPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }

            Text(
                text = record.gameTitle,
                color = accent,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = record.finishedAt.longDate(),
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )
            record.endReason?.let {
                Text(
                    text = it,
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DartsPalette.Surface)
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val winner = record.winnerName()
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(accent.copy(alpha = 0.18f), CircleShape)
                        .border(2.dp, accent.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = winner?.take(1)?.uppercase()?.ifBlank { "=" } ?: "=",
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = winner?.uppercase() ?: "DRAW",
                        color = DartsPalette.Text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if (winner != null) "WINNER" else "NO OUTRIGHT WINNER",
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }
            }

            Text(
                text = "FINAL STANDINGS",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                record.players
                    .sortedWith(compareByDescending<MatchPlayerRecord> { it.score }.thenBy { it.name })
                    .forEach { player -> PlayerResultRow(record, player, accent) }
            }
        }
    }
}

@Composable
private fun PlayerResultRow(record: MatchRecord, player: MatchPlayerRecord, accent: Color) {
    val isWinner = player.id == record.winnerId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isWinner) accent.copy(alpha = 0.12f) else DartsPalette.Surface)
            .border(
                1.dp,
                if (isWinner) accent.copy(alpha = 0.55f) else DartsPalette.Border,
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(10.dp).background(playerColor(player.colorIndex), CircleShape))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = player.name,
                color = DartsPalette.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
            )
            val detail = player.detail
            if (detail != null) {
                Text(text = detail, color = DartsPalette.Muted, fontSize = 11.sp)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${player.score}",
                color = if (isWinner) accent else DartsPalette.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = record.scoreLabel(),
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        if (player.darts > 0) {
            Spacer(Modifier.size(2.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${player.darts}",
                    color = DartsPalette.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "DARTS",
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

private fun MatchRecord.scoreLabel(): String = when (game) {
    "SHIELDS" -> "NUMBERS"
    "LAVA" -> "LIVES"
    "WHACK_A_MOLE" -> "MOLES"
    "CRICKET" -> "POINTS"
    "X01" -> "LEGS"
    "AROUND_THE_CLOCK" -> "TARGETS"
    "CHECKOUT" -> "CHECKOUTS"
    else -> "POINTS"
}
