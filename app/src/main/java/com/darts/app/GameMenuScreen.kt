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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GameMenuScreen(
    onSelect: (GameType) -> Unit,
    onJoin: () -> Unit,
) {
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
                modifier = Modifier.fillMaxWidth().height(108.dp),
            )

            JoinCta(onClick = onJoin)

            GameType.entries.filterNot { it.isTraining }.forEach { game ->
                GameCard(game = game, onClick = { onSelect(game) })
            }
        }
    }
}

@Composable
private fun JoinCta(onClick: () -> Unit) {
    val ink = DartsPalette.onColor(DartsPalette.Blue)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DartsPalette.Blue,
                contentColor = DartsPalette.onColor(DartsPalette.Blue),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(ink.copy(alpha = 0.18f), CircleShape)
                        .border(1.dp, ink.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "J",
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Text(
                    text = "JOIN A GAME",
                    color = ink,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "SECOND SCREEN",
                    color = ink.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
        Text(
            text = "Use this device as a second screen for a game running nearby.",
            color = DartsPalette.Muted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun GameCard(game: GameType, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DartsPalette.Surface)
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClickLabel = "Open ${game.title}", onClick = onClick)
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
        }
        Text(
            text = "›",
            color = DartsPalette.BlueBright,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
