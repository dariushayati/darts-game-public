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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.darts.core.Player
import com.darts.core.X01Rules

@Composable
fun X01SetupScreen(
    rules: X01Rules,
    draft: PlayerSetupDraft,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    autodartsUrl: String,
    scoringMode: ScoringMode,
    onScoringModeChange: (ScoringMode) -> Unit,
    onStart: (List<Player>, AutodartsConfig?, X01Rules) -> Unit,
) {
    val accent = DartsPalette.Blue
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
                            .border(1.5.dp, DartsPalette.Blue.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                            .clickable { onBack() }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(DartsPalette.Blue, RoundedCornerShape(8.dp)),
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
                        text = "X01",
                        color = DartsPalette.Text,
                        fontSize = 48.sp,
                        lineHeight = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        text = "Start at ${rules.startingScore} · ${rules.inRule.label} IN · ${rules.outRule.label} OUT. Subtract every dart and finish exactly on zero.",
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
                                val isSelected = draft.count == n
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) accent else DartsPalette.ChipOff)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) accent else DartsPalette.Border,
                                            RoundedCornerShape(10.dp),
                                        )
                                        .clickable(role = Role.Button) { draft.count = n }
                                        .semantics { this.selected = isSelected },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$n",
                                        color = if (isSelected) DartsPalette.onColor(accent) else DartsPalette.Muted,
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "RULES",
                                color = DartsPalette.Muted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "EDIT",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.clickable { onSettings() },
                            )
                        }
                        Text(
                            text = "${rules.startingScore} · ${rules.inRule.label} IN · ${rules.outRule.label} OUT",
                            color = accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "3 DARTS PER TURN · FIRST TO ${rules.legsLabel()}",
                            color = DartsPalette.Text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Reach exactly zero. A bust returns your score to the start of the turn and forfeits the remaining darts.",
                            color = DartsPalette.Text,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = when {
                                rules.inRule == com.darts.core.X01InRule.STRAIGHT &&
                                    rules.outRule == com.darts.core.X01OutRule.DOUBLE ->
                                    "Straight in, double out: any dart starts; the final dart must be a double."
                                rules.inRule != com.darts.core.X01InRule.STRAIGHT &&
                                    rules.outRule != com.darts.core.X01OutRule.STRAIGHT ->
                                    "${rules.inRule.label} in, ${rules.outRule.label} out: open and close with the required ring."
                                else -> "${rules.inRule.description} ${rules.outRule.description}"
                            },
                            color = DartsPalette.Muted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            SetupBottomBar(
                accent = accent,
                scoringMode = scoringMode,
                onScoringModeChange = onScoringModeChange,
                supportsCamera = true,
                startLabel = "START ${rules.startingScore}",
                onStart = {
                    val players = (0 until draft.count).map { index ->
                        val name = draft.names[index].trim().ifBlank { "Player ${index + 1}" }
                        Player(id = index, name = name, colorIndex = index)
                    }
                    val config = scoringConfigFor(scoringMode, autodartsUrl)
                    onStart(players, config, rules)
                },
            )
        }
    }
}
