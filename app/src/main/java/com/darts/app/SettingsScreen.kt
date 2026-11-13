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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import com.darts.core.GameRules

@Composable
fun SettingsScreen(
    rules: GameRules,
    onSave: (GameRules) -> Unit,
    onBack: () -> Unit,
) {
    var singleLevels by remember { mutableIntStateOf(rules.singleLevels) }
    var doubleLevels by remember { mutableIntStateOf(rules.doubleLevels) }
    var tripleLevels by remember { mutableIntStateOf(rules.tripleLevels) }
    var rounds by remember { mutableIntStateOf(rules.dartsPerPlayer / 3) }
    var endOnBoardFull by remember { mutableStateOf(rules.endOnBoardFull) }

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
                label = "ROUNDS",
                value = rounds,
                range = 3..100,
                step = 1,
                suffix = " ROUNDS",
            ) { rounds = it }

            SettingStepper(
                label = "SINGLE ADDS",
                value = singleLevels,
                range = 1..3,
                step = 1,
                suffix = " LEVEL",
            ) { singleLevels = it }

            SettingStepper(
                label = "DOUBLE ADDS",
                value = doubleLevels,
                range = 1..4,
                step = 1,
                suffix = " LEVELS",
            ) { doubleLevels = it }

            SettingStepper(
                label = "TRIPLE ADDS & LOCKS",
                value = tripleLevels,
                range = 2..6,
                step = 1,
                suffix = " LEVELS",
            ) { tripleLevels = it }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "END WHEN BOARD FULL",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "End early once every target is locked",
                        color = DartsPalette.Text,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = endOnBoardFull,
                    onCheckedChange = { endOnBoardFull = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DartsPalette.Blue,
                        uncheckedThumbColor = DartsPalette.Muted,
                        uncheckedTrackColor = DartsPalette.ChipOff,
                    ),
                )
            }


            Text(
                text = "Every dart costs one go, so doubles and trebles are bonuses. " +
                    "The bull is one target worth one level. " +
                    "A treble adds ${tripleLevels} levels and locks the sector at ${tripleLevels}. " +
                    "Each player throws $rounds rounds (${rounds * 3} darts)" +
                    (if (endOnBoardFull) ", or early once the board is full." else "."),
                color = DartsPalette.Muted,
                fontSize = 11.sp,
            )

            Button(
                onClick = {
                    onSave(
                        GameRules(
                            dartsPerPlayer = rounds * 3,
                            endOnBoardFull = endOnBoardFull,
                            singleLevels = singleLevels,
                            doubleLevels = doubleLevels,
                            tripleLevels = tripleLevels,
                        ),
                    )
                },
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

@Composable
internal fun SettingStepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "$value$suffix",
                color = DartsPalette.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
            )
        }
        StepButton("−", enabled = value > range.first) {
            onChange((value - step).coerceIn(range))
        }
        Spacer(Modifier.width(8.dp))
        StepButton("+", enabled = value < range.last) {
            onChange((value + step).coerceIn(range))
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) DartsPalette.Blue.copy(alpha = 0.20f) else DartsPalette.ChipOff)
            .border(
                1.5.dp,
                if (enabled) DartsPalette.BlueBright else DartsPalette.Border,
                RoundedCornerShape(11.dp),
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) DartsPalette.BlueBright else DartsPalette.Border,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
