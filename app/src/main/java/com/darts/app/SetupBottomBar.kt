package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupBottomBar(
    accent: Color,
    scoringMode: ScoringMode,
    onScoringModeChange: (ScoringMode) -> Unit,
    startLabel: String,
    onStart: () -> Unit,
    supportsCamera: Boolean = false,
) {
    val context = LocalContext.current
    val settings = remember(context) { AppSettingsStore(context) }
    var shareEnabled by remember { mutableStateOf(settings.loadShareEnabled()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DartsPalette.Bar.copy(alpha = 0.95f),
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .border(
                1.dp,
                DartsPalette.Border,
                RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LayoutPolicy.CONTENT_MAX_WIDTH_DP.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "SCORING",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.weight(1f))
                SetupMethodChip(
                    label = "MANUAL",
                    selected = scoringMode == ScoringMode.MANUAL,
                    accent = accent,
                    onClick = { onScoringModeChange(ScoringMode.MANUAL) },
                )
                SetupMethodChip(
                    label = "AUTODARTS",
                    selected = scoringMode == ScoringMode.AUTODARTS,
                    accent = accent,
                    onClick = { onScoringModeChange(ScoringMode.AUTODARTS) },
                )
                if (supportsCamera) {
                    SetupMethodChip(
                        label = "CAMERA",
                        selected = scoringMode == ScoringMode.CAMERA,
                        accent = accent,
                        onClick = { onScoringModeChange(ScoringMode.CAMERA) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "SECOND SCREEN",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Spacer(Modifier.weight(1f))
                SetupMethodChip(
                    label = "SHARE",
                    selected = shareEnabled,
                    accent = DartsPalette.Green,
                    onClick = {
                        val next = !shareEnabled
                        shareEnabled = next
                        settings.saveShareEnabled(next)
                    },
                )
            }
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = DartsPalette.onColor(accent),
                ),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Text(
                    text = startLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun SetupMethodChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else DartsPalette.ChipOff)
            .border(
                1.2.dp,
                if (selected) accent else DartsPalette.Border,
                RoundedCornerShape(8.dp),
            )
            .clickable(role = Role.Button) { onClick() }
            .semantics { this.selected = selected }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) accent else DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}
