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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.CheckoutDifficulty
import com.darts.core.CheckoutRules
import com.darts.core.Player

@Composable
fun CheckoutSetupScreen(
    draft: PlayerSetupDraft,
    rules: CheckoutRules,
    onRulesChange: (CheckoutRules) -> Unit,
    onBack: () -> Unit,
    autodartsUrl: String,
    scoringMode: ScoringMode,
    onScoringModeChange: (ScoringMode) -> Unit,
    onStart: (Player, AutodartsConfig?, CheckoutRules) -> Unit,
) {
    var difficulty by remember { mutableStateOf(rules.difficulty) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DartsPalette.BackgroundDeep,
                        DartsPalette.Background,
                        DartsPalette.TintRobinHood,
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
                            .background(DartsPalette.Amber.copy(alpha = 0.20f))
                            .border(
                                1.5.dp,
                                DartsPalette.Amber.copy(alpha = 0.7f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable { onBack() }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(DartsPalette.Amber, RoundedCornerShape(8.dp)),
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
                            text = "BACK TO TRAINING",
                            color = DartsPalette.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                    Text(
                        text = "CHECKOUT",
                        color = DartsPalette.Text,
                        fontSize = 40.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "Hit common finishes in order. Clear the path to move on.",
                        color = DartsPalette.Muted,
                        fontSize = 13.sp,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DartsPalette.Surface, RoundedCornerShape(20.dp))
                            .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "MODE",
                            color = DartsPalette.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CheckoutModeCard(
                                title = "EASY",
                                meta = "2 DARTS",
                                example = "60 = 20 · D20",
                                description = "Common two-dart finishes.",
                                selected = difficulty == CheckoutDifficulty.EASY,
                                onClick = {
                                    difficulty = CheckoutDifficulty.EASY
                                    onRulesChange(CheckoutRules(CheckoutDifficulty.EASY))
                                },
                                modifier = Modifier.weight(1f),
                            )
                            CheckoutModeCard(
                                title = "HARD",
                                meta = "3 DARTS",
                                example = "170 = T20 · T20 · BULL",
                                description = "Common three-dart finishes.",
                                selected = difficulty == CheckoutDifficulty.HARD,
                                onClick = {
                                    difficulty = CheckoutDifficulty.HARD
                                    onRulesChange(CheckoutRules(CheckoutDifficulty.HARD))
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DartsPalette.Surface, RoundedCornerShape(20.dp))
                            .border(1.dp, DartsPalette.Border, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "PLAYER",
                            color = DartsPalette.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(playerColor(0), CircleShape),
                            )
                            OutlinedTextField(
                                value = draft.names[0],
                                onValueChange = { value ->
                                    draft.names = draft.names.toMutableList().also {
                                        it[0] = value
                                    }
                                },
                                singleLine = true,
                                label = { Text("Player name") },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f),
                            )
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
                            text = "RULES",
                            color = DartsPalette.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                        )
                        Text(
                            text = "10 CHECKOUTS · 3 DARTS PER SET",
                            color = DartsPalette.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "Hit the displayed darts in order. A wrong dart uses a dart but keeps your target, and darts refresh every three throws.",
                            color = DartsPalette.Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                        Text(
                            text = "Every path finishes on a double or the inner bull. Clear all 10 checkouts to finish.",
                            color = DartsPalette.Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
            SetupBottomBar(
                accent = DartsPalette.Amber,
                scoringMode = scoringMode,
                onScoringModeChange = onScoringModeChange,
                supportsCamera = true,
                startLabel = "START CHECKOUT",
                onStart = {
                    val name = draft.names[0].trim().ifBlank { "Player 1" }
                    val player = Player(id = 0, name = name, colorIndex = 0)
                    val config = scoringConfigFor(scoringMode, autodartsUrl)
                    onStart(player, config, CheckoutRules(difficulty))
                },
            )
        }
    }
}

@Composable
private fun CheckoutModeCard(
    title: String,
    meta: String,
    example: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) {
                    DartsPalette.Amber.copy(alpha = 0.16f)
                } else {
                    DartsPalette.ChipOff
                },
            )
            .border(
                1.5.dp,
                if (selected) DartsPalette.Amber else DartsPalette.Border,
                RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.Button) { onClick() }
            .semantics { this.selected = selected }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = if (selected) DartsPalette.Amber else DartsPalette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
            Box(Modifier.weight(1f))
            Text(
                text = meta,
                color = if (selected) DartsPalette.Amber else DartsPalette.Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Text(
            text = example,
            color = DartsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = description,
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
        )
    }
}
