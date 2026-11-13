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
import androidx.compose.runtime.mutableStateOf
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
import com.darts.core.GunslingerDifficulty
import com.darts.core.GunslingerRules

@Composable
fun GunslingerSettingsScreen(
    rules: GunslingerRules,
    onSave: (GunslingerRules) -> Unit,
    onBack: () -> Unit,
) {
    var rounds by remember { mutableIntStateOf(rules.rounds) }
    var beeps by remember { mutableIntStateOf(rules.beeps) }
    var pointsPerSecond by remember { mutableIntStateOf(rules.pointsPerSecond) }
    var difficulty by remember { mutableStateOf(rules.difficulty) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background, DartsPalette.TintLavaDeep),
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
                label = "ROUNDS PER PLAYER",
                value = rounds,
                range = 1..20,
                step = 1,
                suffix = " ROUNDS",
            ) { rounds = it }

            SettingStepper(
                label = "BEEPS BEFORE TARGET",
                value = beeps,
                range = 0..6,
                step = 1,
                suffix = " BEEPS",
            ) { beeps = it }

            SettingStepper(
                label = "SCORE DROP PER SECOND",
                value = pointsPerSecond,
                range = 0..500,
                step = 50,
                suffix = " PTS/SEC",
            ) { pointsPerSecond = it }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    text = "MODE",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GunslingerDifficulty.entries.forEach { option ->
                        val selected = option == difficulty
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DartsPalette.Red else DartsPalette.SurfaceRaised)
                                .border(
                                    1.dp,
                                    if (selected) DartsPalette.Red else DartsPalette.Border,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { difficulty = option }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.name,
                                color = if (selected) Color.White else DartsPalette.Text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
                Text(
                    text = if (difficulty == GunslingerDifficulty.NORMAL) {
                        "Normal: the two neighbouring board numbers also score, at half value."
                    } else {
                        "Hard: only the called number scores."
                    },
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }

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
                    text = "Each turn starts with $beeps beeps. A random number from 1 to 20 is then revealed.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Hit that number with all three darts. Any ring counts, and every scoring hit banks the current value.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "The score starts at ${rules.basePoints} and falls $pointsPerSecond points per second to a floor of ${rules.minimumPoints}. " +
                        if (difficulty == GunslingerDifficulty.NORMAL) {
                            "Normal mode gives half value for the two neighbouring board numbers; misses still bank nothing."
                        } else {
                            "Hard mode only scores the called number; misses and other numbers bank nothing."
                        },
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Every player gets $rounds turns. Highest total wins; ties are draws.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Button(
                onClick = {
                    onSave(
                        rules.copy(
                            rounds = rounds,
                            beeps = beeps,
                            pointsPerSecond = pointsPerSecond,
                            difficulty = difficulty,
                        ),
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DartsPalette.Red,
                    contentColor = DartsPalette.onColor(DartsPalette.Red),
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
