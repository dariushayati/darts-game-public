package com.darts.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.MirrorSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun MirrorGameScreen(
    host: String,
    port: Int,
    code: String,
    onExit: () -> Unit,
) {
    var snapshot by remember { mutableStateOf<MirrorSnapshot?>(null) }
    var connected by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var incompatible by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }

    KeepScreenOn()

    BackHandler { onExit() }

    LaunchedEffect(host, port, code) {
        val client = MirrorClient(host, port, code, mirrorDeviceName())
        var lastSeq = -1L
        while (isActive) {
            val result = withContext(Dispatchers.IO) { client.fetch() }
            val pause = when (result) {
                is MirrorFetchResult.Success -> {
                    connected = true
                    pending = false
                    denied = false
                    incompatible = false
                    attempts = 0
                    if (result.snapshot.seq >= lastSeq) {
                        snapshot = result.snapshot
                        lastSeq = result.snapshot.seq
                    }
                    300L
                }

                MirrorFetchResult.Pending -> {
                    connected = true
                    pending = true
                    1000L
                }

                MirrorFetchResult.Denied -> {
                    connected = true
                    pending = false
                    denied = true
                    1000L
                }

                MirrorFetchResult.WrongCode -> {
                    connected = true
                    denied = true
                    1000L
                }

                MirrorFetchResult.Incompatible -> {
                    connected = true
                    incompatible = true
                    1000L
                }

                MirrorFetchResult.Unreachable -> {
                    connected = false
                    attempts++
                    1000L
                }
            }
            delay(pause)
        }
    }

    val current = snapshot
    val state = current?.state
    val mirrorView = current?.view
    val unsupported = current != null && mirrorView == null &&
        (state == null || current.session.gameType != GameType.SHIELDS.name)
    if (current != null && !incompatible && !unsupported) {
        Box(Modifier.fillMaxSize()) {
            val shieldsState = state?.takeIf {
                prefersShieldsMirror(current.session.gameType, hasState = true)
            }
            if (shieldsState != null) {
                val budget = shieldsState.rules.dartsPerPlayer
                val playerDarts = shieldsState.dartsByPlayer[shieldsState.currentPlayer.id] ?: 0
                val modeLabel = if (budget > 0) {
                    "SHIELDS · $playerDarts/$budget DARTS"
                } else {
                    "SHIELDS · ENDLESS"
                }
                ShieldsGameContent(
                    state = shieldsState,
                    ui = current.ui,
                    autodarts = null,
                    modeLabel = modeLabel,
                    interactive = false,
                    canUndo = false,
                    mirror = true,
                    onPlayAgain = {},
                    onExit = onExit,
                )
            } else if (mirrorView != null) {
                MirrorViewContent(view = mirrorView, onExit = onExit)
            }
            MirrorBanner(pending = pending, connected = connected)
        }
    } else {
        val status = when {
            incompatible -> Triple(
                "UPDATE REQUIRED",
                "The host is running a different app version. Update both devices.",
                DartsPalette.Amber,
            )

            unsupported -> Triple(
                "GAME NOT SUPPORTED",
                "This game type cannot be mirrored yet. Update the host app.",
                DartsPalette.Amber,
            )

            pending -> Triple(
                "WAITING FOR APPROVAL",
                "Ask the host to allow ${mirrorDeviceName()} on the SHARE screen.",
                DartsPalette.Amber,
            )

            denied -> Triple(
                "CONNECTION DECLINED",
                "The host declined this device. Ask them to share again and approve the connection.",
                DartsPalette.Red,
            )

            else -> Triple(
                "CONNECTING…",
                "Looking for the game at $host:$port",
                DartsPalette.Amber,
            )
        }
        MirrorStatusScreen(
            title = status.first,
            message = status.second,
            accent = status.third,
            showRetryHint = !pending && !denied && !incompatible && !unsupported && attempts > 3,
            onExit = onExit,
        )
    }
}

internal fun prefersShieldsMirror(gameType: String, hasState: Boolean): Boolean =
    hasState && gameType == GameType.SHIELDS.name

@Composable
private fun MirrorBanner(pending: Boolean, connected: Boolean) {
    val banner = when {
        pending -> "WAITING FOR HOST APPROVAL" to DartsPalette.Amber
        !connected -> "RECONNECTING…" to DartsPalette.Amber
        else -> null
    }
    if (banner != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .background(banner.second.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .border(1.dp, banner.second.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = banner.first,
                    color = banner.second,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun MirrorStatusScreen(
    title: String,
    message: String,
    accent: Color,
    showRetryHint: Boolean,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background, DartsPalette.TintNavy),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(12.dp).background(accent, CircleShape))
            Text(
                text = title,
                color = DartsPalette.Text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = DartsPalette.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
            if (showRetryHint) {
                Text(
                    text = "Still waiting. Check that both devices are on the same Wi-Fi and the code matches.",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onExit,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DartsPalette.Blue,
                    contentColor = DartsPalette.onColor(DartsPalette.Blue),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("BACK TO JOIN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}
