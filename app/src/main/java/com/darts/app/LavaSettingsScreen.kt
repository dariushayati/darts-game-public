package com.darts.app

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.LavaRules

@Composable
fun LavaSettingsScreen(
    rules: LavaRules,
    onSave: (LavaRules) -> Unit,
    onBack: () -> Unit,
) {
    var lives by remember { mutableIntStateOf(rules.lives) }
    var lavaPerRound by remember { mutableIntStateOf(rules.lavaPerRound) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background, DartsPalette.TintLava),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LayoutPolicy.CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DartsPalette.Surface)
                        .border(1.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = DartsPalette.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "RULES",
                    color = DartsPalette.Text,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "Changes apply to the next game.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )

            SettingStepper(
                label = "LIVES",
                value = lives,
                range = 1..9,
                step = 1,
                suffix = " LIVES",
            ) { lives = it }

            SettingStepper(
                label = "LAVA PER ROUND",
                value = lavaPerRound,
                range = 1..5,
                step = 1,
                suffix = " NUMBERS",
            ) { lavaPerRound = it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = "HOW IT PLAYS",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "A round is one turn for every live player.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "The first round starts with $lavaPerRound lava numbers. After each round, $lavaPerRound more random numbers become lava.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Hitting lava or missing costs one life; the bull always stays safe.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = "When all 20 numbers are lava, the board is consumed; most lives wins.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = "$lives lives each. Last player standing wins; solo play ends when your lives run out.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Button(
                onClick = { onSave(LavaRules(lives = lives, lavaPerRound = lavaPerRound)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DartsPalette.Blue,
                    contentColor = DartsPalette.onColor(DartsPalette.Blue),
                ),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    text = "SAVE RULES",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}
