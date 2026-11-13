package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GeneralSettingsScreen(
    soundEnabled: Boolean,
    onSoundEnabledChange: (Boolean) -> Unit,
    voiceCalloutsEnabled: Boolean,
    onVoiceCalloutsChange: (Boolean) -> Unit,
    autodartsUrl: String,
    onAutodartsUrlChange: (String) -> Unit,
    autoScoreSoftBoard: Boolean,
    onAutoScoreSoftBoardChange: (Boolean) -> Unit,
    autoScoreForceCpu: Boolean,
    onAutoScoreForceCpuChange: (Boolean) -> Unit,
    autoScoreDebugImages: Boolean,
    onAutoScoreDebugImagesChange: (Boolean) -> Unit,
    cameraTrackingRate: CameraTrackingRate,
    onCameraTrackingRateChange: (CameraTrackingRate) -> Unit,
    onOpenAutoScore: () -> Unit,
    onOpenLicences: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background),
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
                text = "SETTINGS",
                color = DartsPalette.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "Tap the \u24d8 next to any setting for an explanation.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )

            SettingsSection(
                title = "AUDIO",
                info = "Sound and speech settings for the whole app.",
            ) {
                SettingsSwitchRow(
                    title = "SOUND EFFECTS",
                    subtitle = "Play cues for hits, locks and lost lives",
                    checked = soundEnabled,
                    onCheckedChange = onSoundEnabledChange,
                    info = "Plays short sound cues during a game: hits, locks, removals and " +
                        "life loss. Turn it off for silent scoring.",
                    description = "About sound effects",
                )
                SettingsSwitchRow(
                    title = "VOICE CALLOUTS",
                    subtitle = "Speak the Gunslinger target number",
                    checked = voiceCalloutsEnabled,
                    onCheckedChange = onVoiceCalloutsChange,
                    info = "Uses Android text-to-speech to read the Gunslinger target aloud. " +
                        "Other modes do not speak.",
                    description = "About voice callouts",
                )
            }

            SettingsSection(
                title = "AUTODARTS",
                info = "Settings for the optional Autodarts board manager on your LAN.",
            ) {
                OutlinedTextField(
                    value = autodartsUrl,
                    onValueChange = onAutodartsUrlChange,
                    label = { Text("Board manager URL") },
                    placeholder = { Text(DEFAULT_AUTODARTS_URL) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Used when a game is set to Autodarts. Default: $DEFAULT_AUTODARTS_URL",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }

            SettingsSection(
                title = "CAMERA SCORING",
                info = "BullTrack runs on-device. Camera scoring is selected per game in its setup screen.",
            ) {
                SettingsSwitchRow(
                    title = "SOFT-TIP BOARD",
                    subtitle = "Use wider rings for a soft-tip board",
                    checked = autoScoreSoftBoard,
                    onCheckedChange = onAutoScoreSoftBoardChange,
                    info = "Soft-tip boards have wider doubles, trebles and bull rings. Turn " +
                        "this on for a red/blue plastic board; leave it off for steel.",
                    description = "About soft-tip boards",
                )
                SettingsSwitchRow(
                    title = "FORCE CPU INFERENCE",
                    subtitle = "Slower YOLO, but avoids GPU driver problems",
                    checked = autoScoreForceCpu,
                    onCheckedChange = onAutoScoreForceCpuChange,
                    info = "Runs the YOLO model on the CPU. It is slower and uses more CPU, but " +
                        "can avoid GPU driver crashes, heat or Vulkan problems.",
                    description = "About CPU inference",
                )
                SettingsSwitchRow(
                    title = "DEBUG IMAGES",
                    subtitle = "Developer diagnostics; slows processing",
                    checked = autoScoreDebugImages,
                    onCheckedChange = onAutoScoreDebugImagesChange,
                    info = "Saves BullTrack debug images and frame-stage timing logs. Useful for " +
                        "diagnosing missed darts, but it slows processing and uses storage.",
                    description = "About debug images",
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "TRACKING RATE",
                            color = DartsPalette.Text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        SettingsInfoTip(
                            message = "How often the camera is sampled for detection. A higher " +
                                "rate reacts faster but uses more battery and heats the phone; " +
                                "BATTERY is gentler.",
                            description = "About tracking rate",
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CameraTrackingRate.entries.forEach { rate ->
                            val selected = rate == cameraTrackingRate
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) DartsPalette.Blue else DartsPalette.SurfaceRaised,
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) DartsPalette.Blue else DartsPalette.Border,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .clickable(role = Role.Button) {
                                        onCameraTrackingRateChange(rate)
                                    }
                                    .semantics { this.selected = selected },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = rate.label,
                                    color = if (selected) Color.White else DartsPalette.Text,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }
                    Text(
                        text = "More frames = quicker detection, more battery and heat.",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenAutoScore,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("CAMERA TEST", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onOpenLicences,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("LICENCES", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Camera test opens the full preview for placement, zoom and re-detect. " +
                        "Licences lists BullTrack, the model and other components.",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }

            SettingsSection(
                title = "APPEARANCE",
                info = "Choose whether the app follows the device light/dark setting.",
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = mode == themeMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) DartsPalette.Blue else DartsPalette.SurfaceRaised,
                                )
                                .border(
                                    1.dp,
                                    if (selected) DartsPalette.Blue else DartsPalette.Border,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(role = Role.Button) { onThemeModeChange(mode) }
                                .semantics { this.selected = selected }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = mode.name,
                                color = if (selected) Color.White else DartsPalette.Text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                }
                Text(
                    text = "System follows your device light/dark setting.",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }

            Text(
                text = "Game rules are edited from a game's setup screen.",
                color = DartsPalette.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    info: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            SettingsInfoTip(message = info, description = "About $title")
        }
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    info: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                SettingsInfoTip(message = info, description = description)
            }
            Text(
                text = subtitle,
                color = DartsPalette.Muted,
                fontSize = 10.sp,
            )
        }
        Switch(
            checked = checked,
            modifier = Modifier.semantics { contentDescription = title },
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DartsPalette.Blue,
                uncheckedThumbColor = DartsPalette.Muted,
                uncheckedTrackColor = DartsPalette.ChipOff,
            ),
        )
    }
}

@Composable
private fun SettingsInfoTip(
    message: String,
    description: String,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = DartsPalette.Muted,
                modifier = Modifier.size(15.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                },
                onClick = { open = false },
            )
        }
    }
}
