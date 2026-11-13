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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.X01InRule
import com.darts.core.X01OutRule
import com.darts.core.X01Rules

@Composable
fun X01SettingsScreen(
    rules: X01Rules,
    onSave: (X01Rules) -> Unit,
    onBack: () -> Unit,
) {
    val accent = DartsPalette.Blue
    var startingScore by remember { mutableIntStateOf(rules.startingScore) }
    var inRule by remember { mutableStateOf(rules.inRule) }
    var outRule by remember { mutableStateOf(rules.outRule) }
    var legsToWin by remember { mutableIntStateOf(rules.legsToWin) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background, DartsPalette.TintNavy),
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
                    text = "X01 RULES",
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

            X01ChoiceRow(
                label = "STARTING SCORE",
                options = X01Rules.STARTING_SCORES,
                selected = startingScore,
                labelFor = { it.toString() },
                onSelect = { startingScore = it },
                accent = accent,
            )

            X01ChoiceRow(
                label = "IN RULE",
                options = X01InRule.entries.toList(),
                selected = inRule,
                labelFor = { it.label },
                onSelect = { inRule = it },
                accent = accent,
            )
            Text(
                text = inRule.description,
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            X01ChoiceRow(
                label = "OUT RULE",
                options = X01OutRule.entries.toList(),
                selected = outRule,
                labelFor = { it.label },
                onSelect = { outRule = it },
                accent = accent,
            )
            Text(
                text = outRule.description,
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )

            SettingStepper(
                label = "LEGS TO WIN",
                value = legsToWin,
                range = 1..7,
                step = 1,
                suffix = if (legsToWin == 1) " LEG" else " LEGS",
            ) { legsToWin = it }

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
                    text = "Start on $startingScore. Every dart subtracts its value. Reach exactly zero to win the leg.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "${inRule.label} in: ${inRule.description}",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "${outRule.label} out: ${outRule.description}",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "A bust returns the score to the start of the turn and ends the turn immediately. The inner bull counts as a double.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Every turn is three darts. First to ${if (legsToWin == 1) "1 leg" else "$legsToWin legs"} wins the match.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }

            Button(
                onClick = {
                    onSave(
                        rules.copy(
                            startingScore = startingScore,
                            inRule = inRule,
                            outRule = outRule,
                            legsToWin = legsToWin,
                        ),
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = DartsPalette.onColor(accent),
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

@Composable
private fun <T> X01ChoiceRow(
    label: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    accent: androidx.compose.ui.graphics.Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = label,
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) accent else DartsPalette.SurfaceRaised)
                        .border(
                            1.dp,
                            if (isSelected) accent else DartsPalette.Border,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable(role = Role.Button) { onSelect(option) }
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = labelFor(option),
                        color = if (isSelected) DartsPalette.onColor(accent) else DartsPalette.Text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
