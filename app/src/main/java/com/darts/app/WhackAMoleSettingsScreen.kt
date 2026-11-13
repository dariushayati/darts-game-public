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
import com.darts.core.WhackAMoleRules

@Composable
fun WhackAMoleSettingsScreen(
    rules: WhackAMoleRules,
    onSave: (WhackAMoleRules) -> Unit,
    onBack: () -> Unit,
) {
    var moles by remember { mutableIntStateOf(rules.moles) }
    var hardMode by remember { mutableStateOf(rules.hardMode) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background, DartsPalette.TintWhack),
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
                label = "MOLES TO WHACK",
                value = moles,
                range = 1..20,
                step = 1,
                suffix = " MOLES",
            ) { moles = it }

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
                        text = "HARD MODE",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "One or two moles must be hit in the double or treble only",
                        color = DartsPalette.Text,
                        fontSize = 12.sp,
                    )
                }
                Switch(
                    checked = hardMode,
                    onCheckedChange = { hardMode = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = DartsPalette.Mole,
                        uncheckedThumbColor = DartsPalette.Muted,
                        uncheckedTrackColor = DartsPalette.ChipOff,
                    ),
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
                    text = "A random ordered list of $moles numbers is set. Each player throws 3 darts, then the next player.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "Whack the highlighted number — any single, double or treble counts — then the next one.",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                )
                Text(
                    text = "No penalty for misses or wrong numbers. First to clear the whole list wins.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
                if (hardMode) {
                    Text(
                        text = "HARD MODE: 1–2 moles need the double (D) or treble (T) band — the board highlights only that ring.",
                        color = DartsPalette.Mole,
                        fontSize = 11.sp,
                    )
                }
            }

            Button(
                onClick = { onSave(WhackAMoleRules(moles = moles, hardMode = hardMode)) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DartsPalette.Mole,
                    contentColor = DartsPalette.onColor(DartsPalette.Mole),
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
