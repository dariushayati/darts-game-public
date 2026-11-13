package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.AutodartsState
import com.darts.core.deriveBoardStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AutodartsScreen(
    autodartsUrl: String,
    onAutodartsUrlChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<AutodartsState?>(null) }
    var draftUrl by remember { mutableStateOf(autodartsUrl) }
    var reachable by remember { mutableStateOf<Boolean?>(null) }
    var version by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autodartsUrl, refreshKey) {
        val client = AutodartsClient(autodartsUrl)
        version = withContext(Dispatchers.IO) { client.fetchVersion() }
        while (isActive) {
            val state = withContext(Dispatchers.IO) { client.fetchState(1500) }
            snapshot = state
            reachable = state != null
            delay(1000)
        }
    }

    fun runControl(label: String, action: (AutodartsClient) -> AutodartsControlResult) {
        scope.launch {
            busy = true
            message = null
            val client = AutodartsClient(draftUrl)
            val result = withContext(Dispatchers.IO) { action(client) }
            message = when (result) {
                AutodartsControlResult.OK -> "$label sent"
                AutodartsControlResult.UNSUPPORTED -> "$label not supported by this Board Manager"
                AutodartsControlResult.FAILED -> "$label failed — board unreachable"
            }
            busy = false
            refreshKey++
        }
    }

    val status = deriveBoardStatus(snapshot)
    val statusColor = status.color()
    val statusLabel = when (status) {
        AutodartsBoardStatus.READY -> "READY"
        AutodartsBoardStatus.TAKEOUT -> "TAKING OUT"
        AutodartsBoardStatus.OFFLINE -> "OFFLINE"
        AutodartsBoardStatus.UNKNOWN -> "STOPPED"
    }

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
            Text(
                text = "AUTODARTS",
                color = DartsPalette.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "Connect to the board manager on your network.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )

            // Status card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = when (reachable) {
                            true -> "REACHABLE"
                            false -> "UNREACHABLE"
                            null -> "CHECKING…"
                        },
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                InfoLine("Board", normalizeAutodartsUrl(autodartsUrl))
                InfoLine("Connected", snapshot?.connected?.toString() ?: "—")
                InfoLine("Detection", snapshot?.running?.toString() ?: "—")
                snapshot?.event?.let { InfoLine("Event", it) }
                snapshot?.status?.let { InfoLine("Status", it) }
                InfoLine("Version", version ?: "—")
                Button(
                    onClick = {
                        onAutodartsUrlChange(draftUrl)
                        version = null
                        refreshKey++
                    },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DartsPalette.Blue,
                        contentColor = DartsPalette.onColor(DartsPalette.Blue),
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        text = "CONNECT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }
            }

            // Controls card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "BOARD CONTROLS",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ControlButton(
                        label = "START",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { runControl("Start") { it.start() } }
                    ControlButton(
                        label = "STOP",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    ) { runControl("Stop") { it.stop() } }
                }
                OutlinedButton(
                    onClick = { runControl("Calibration") { it.calibrate() } },
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DartsPalette.Border),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DartsPalette.Text),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        text = "CALIBRATE CAMERAS",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                }
                if (busy) {
                    Text(
                        text = "Working…",
                        color = DartsPalette.Muted,
                        fontSize = 11.sp,
                    )
                }
                message?.let {
                    Text(
                        text = it,
                        color = DartsPalette.GreenText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Settings card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "BOARD MANAGER URL",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                OutlinedTextField(
                    value = draftUrl,
                    onValueChange = { draftUrl = it },
                    label = { Text("Board manager URL") },
                    placeholder = { Text(DEFAULT_AUTODARTS_URL) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Default: $DEFAULT_AUTODARTS_URL",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }

            Text(
                text = "Reset the board during a game from the action bar.",
                color = DartsPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label.uppercase(),
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            color = DartsPalette.Text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DartsPalette.SurfaceRaised,
            contentColor = DartsPalette.Text,
        ),
        modifier = modifier.height(48.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
        )
    }
}

private fun AutodartsBoardStatus.color(): Color = when (this) {
    AutodartsBoardStatus.READY -> DartsPalette.Green
    AutodartsBoardStatus.TAKEOUT -> DartsPalette.Yellow
    AutodartsBoardStatus.OFFLINE -> DartsPalette.Red
    AutodartsBoardStatus.UNKNOWN -> DartsPalette.Amber
}
