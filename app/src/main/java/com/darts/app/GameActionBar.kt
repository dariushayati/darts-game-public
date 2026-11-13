package com.darts.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.app.autoscore.AutoScoreState
import com.darts.core.Player

@Composable
fun GameActionBar(
    player: Player,
    accent: Color,
    primaryColor: Color,
    autodarts: Boolean,
    camera: Boolean = false,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onPrimary: () -> Unit,
    onMenu: () -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var showResetConfirm by remember { mutableStateOf(false) }
    val cameraState = if (camera) {
        CameraScoringHost.controller?.state?.collectAsState()?.value
    } else {
        null
    }
    val cameraBusy = camera && when (cameraState) {
        is AutoScoreState.Ready, is AutoScoreState.DartDetected,
        is AutoScoreState.TurnComplete -> false
        else -> true
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(DartsPalette.Bar.copy(alpha = 0.92f), RoundedCornerShape(20.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .border(1.5.dp, accent, RoundedCornerShape(12.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(playerColor(player.colorIndex), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = player.name.take(1).uppercase(),
                    color = DartsPalette.NamePlate,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DartsPalette.Surface)
                .border(1.5.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
                .clickable { onMenu() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "Menu",
                tint = accent,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onReset != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DartsPalette.Surface)
                    .border(1.5.dp, DartsPalette.Border, RoundedCornerShape(12.dp))
                    .clickable { showResetConfirm = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reset board",
                    tint = DartsPalette.Amber,
                )
            }
        }
        OutlinedButton(
            onClick = onUndo,
            enabled = canUndo,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
                1.5.dp,
                if (canUndo) accent else DartsPalette.Border,
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accent,
                disabledContentColor = DartsPalette.Border,
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
        ) {
            Text("↶", fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Button(
            onClick = onPrimary,
            enabled = !cameraBusy,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                contentColor = DartsPalette.onColor(primaryColor),
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            modifier = Modifier.height(48.dp),
        ) {
            Text(
                text = if (autodarts) "END TURN" else "MISS",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset board?") },
            text = {
                Text("Clears the board manager's current detection. Your game score is unchanged.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onReset?.invoke()
                    },
                ) { Text("RESET") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("CANCEL") }
            },
        )
    }
}
