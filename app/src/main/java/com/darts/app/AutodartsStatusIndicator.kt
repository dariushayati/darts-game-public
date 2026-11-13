package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * In-game Autodarts state: a coloured dot plus the state name (disconnected, stopped,
 * started, taking out). Tapping opens a one-line explanation of the state. Shows
 * LOCAL with the game's accent when manual scoring is selected.
 */
@Composable
fun AutodartsStatusIndicator(
    autodarts: AutodartsConfig?,
    boardStatus: AutodartsBoardStatus,
    accent: Color,
) {
    var expanded by remember { mutableStateOf(false) }

    if (autodarts == null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Text(
                text = "LOCAL",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        return
    }

    if (autodarts.mode == ScoringMode.CAMERA) {
        val controller = CameraScoringHost.controller
        val cameraState = (controller?.state ?: noCameraState).collectAsState().value
        val info = cameraStatusInfo(cameraState)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(info.color, CircleShape))
            Text(
                text = "CAMERA",
                color = info.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        return
    }

    val info = statusInfo(boardStatus)
    val scope = rememberCoroutineScope()
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { expanded = true }
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.size(7.dp).background(info.color, CircleShape))
            Text(
                text = info.label,
                color = info.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(info.description, fontSize = 12.sp, lineHeight = 16.sp) },
                onClick = { expanded = false },
            )
            if (boardStatus == AutodartsBoardStatus.UNKNOWN) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "START DETECTION",
                            color = DartsPalette.Green,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    },
                    onClick = {
                        expanded = false
                        scope.launch {
                            withContext(Dispatchers.IO) { AutodartsClient(autodarts.url).start() }
                        }
                    },
                )
            }
        }
    }
}

private val noCameraState = MutableStateFlow<com.darts.app.autoscore.AutoScoreState?>(null)

private data class AutodartsStatusInfo(
    val label: String,
    val color: Color,
    val description: String,
)

private fun statusInfo(status: AutodartsBoardStatus): AutodartsStatusInfo = when (status) {
    AutodartsBoardStatus.OFFLINE -> AutodartsStatusInfo(
        label = "DISCONNECTED",
        color = DartsPalette.Red,
        description = "No response from the board manager. Check it is powered on and on the same Wi-Fi.",
    )
    AutodartsBoardStatus.UNKNOWN -> AutodartsStatusInfo(
        label = "STOPPED",
        color = DartsPalette.Amber,
        description = "Connected, but detection is stopped. Start it from this menu.",
    )
    AutodartsBoardStatus.READY -> AutodartsStatusInfo(
        label = "STARTED",
        color = DartsPalette.Green,
        description = "Detection is running — darts thrown now are scored automatically.",
    )
    AutodartsBoardStatus.TAKEOUT -> AutodartsStatusInfo(
        label = "TAKE OUT",
        color = DartsPalette.Yellow,
        description = "The board is taking the darts out. Results settle in a moment.",
    )
}
