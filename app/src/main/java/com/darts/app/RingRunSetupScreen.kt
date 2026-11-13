package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.darts.core.Player

@Composable
fun RingRunSetupScreen(
    draft: PlayerSetupDraft,
    onBack: () -> Unit,
    autodartsUrl: String,
    scoringMode: ScoringMode,
    onScoringModeChange: (ScoringMode) -> Unit,
    onStart: (List<Player>, AutodartsConfig?) -> Unit,
) {
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
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = LayoutPolicy.CONTENT_MAX_WIDTH_DP.dp)
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
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
                    Text("←", color = DartsPalette.NamePlate, fontSize = 17.sp, fontWeight = FontWeight.Black)
                }
                Text(
                    text = "BACK TO GAMES",
                    color = DartsPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "RING RUN",
                color = DartsPalette.Text,
                fontSize = 40.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = "Four rounds. The highlighted ring shrinks from the double to the bull.",
                color = DartsPalette.Muted,
                fontSize = 13.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(20.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "PLAYERS",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    (1..6).forEach { n ->
                        val selected = draft.count == n
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) DartsPalette.Yellow else DartsPalette.ChipOff)
                                .border(
                                    1.5.dp,
                                    if (selected) DartsPalette.Yellow else DartsPalette.Border,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(role = Role.Button) { draft.count = n }
                                .semantics { this.selected = selected },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "$n",
                                color = if (selected) DartsPalette.onColor(DartsPalette.Blue) else DartsPalette.Muted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                            )
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (0 until draft.count).forEach { index ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(Modifier.size(10.dp).background(playerColor(index), CircleShape))
                            OutlinedTextField(
                                value = draft.names[index],
                                onValueChange = { value ->
                                    draft.names = draft.names.toMutableList().also { it[index] = value }
                                },
                                singleLine = true,
                                label = { Text("Player ${index + 1}") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(20.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    text = "ROUNDS",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                RingRunRuleRow("1", "DOUBLE + LARGE SINGLE", "1 PT")
                RingRunRuleRow("2", "LARGE SINGLE · 10 NUMBERS", "1 PT")
                RingRunRuleRow("3", "SMALL SINGLE", "2 PTS")
                RingRunRuleRow("4", "BULL + OUTER BULL", "3 PTS")
                Text(
                    text = "Each round highlights a whole ring. Any number in that ring counts. All players throw 3 darts before the next round.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
                Text(
                    text = "Round 2 numbers: 12, 5, 20, 1, 18, 7, 19, 3, 17, 2.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Highest total after round 4 wins. Bull and outer bull both score in the final round.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }

                }
            }
            SetupBottomBar(
                accent = DartsPalette.Yellow,
                scoringMode = scoringMode,
                onScoringModeChange = onScoringModeChange,
                supportsCamera = true,
                startLabel = "START RING RUN",
                onStart = {
                    val players = (0 until draft.count).map { index ->
                        val name = draft.names[index].trim().ifBlank { "Player ${index + 1}" }
                        Player(id = index, name = name, colorIndex = index)
                    }
                    val config = scoringConfigFor(scoringMode, autodartsUrl)
                    onStart(players, config)
                },
            )
        }
    }
}

@Composable
private fun RingRunRuleRow(round: String, zone: String, points: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(DartsPalette.Yellow.copy(alpha = 0.16f), CircleShape)
                .border(1.dp, DartsPalette.Yellow.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(round, color = DartsPalette.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
        Text(
            text = zone,
            color = DartsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(points, color = DartsPalette.Yellow, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}
