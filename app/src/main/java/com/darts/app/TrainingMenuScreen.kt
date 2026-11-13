package com.darts.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrainingMenuScreen(onSelect: (GameType) -> Unit) {
    val games = GameType.entries.filter { it.isTraining }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DartsPalette.BackgroundDeep,
                        DartsPalette.Background,
                        DartsPalette.TintRingRun,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LayoutPolicy.MENU_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.darty_time_logo),
                contentDescription = "Darty Time",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().height(92.dp),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "TRAINING",
                        color = DartsPalette.Text,
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "Practice drills for solo sessions or a group warm-up.",
                        color = DartsPalette.Muted,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Box(
                    modifier = Modifier
                        .background(
                            DartsPalette.DartCyan.copy(alpha = 0.16f),
                            RoundedCornerShape(50),
                        )
                        .border(
                            1.dp,
                            DartsPalette.DartCyan.copy(alpha = 0.7f),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = "${games.size} DRILL${if (games.size == 1) "" else "S"}",
                        color = DartsPalette.DartCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }

            Text(
                text = "DRILLS",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            games.forEach { game ->
                TrainingCard(game = game, onClick = { onSelect(game) })
            }
        }
    }
}

@Composable
private fun TrainingCard(game: GameType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DartsPalette.Surface)
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "Open ${game.title}",
                onClick = onClick,
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(game.accent.copy(alpha = 0.16f), CircleShape)
                .border(1.5.dp, game.accent.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = game.title.take(1),
                color = game.accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = game.title,
                color = DartsPalette.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = game.tagline,
                color = DartsPalette.Muted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Text(
                text = game.trainingMeta.orEmpty(),
                color = game.accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = "›",
            color = DartsPalette.BlueBright,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
