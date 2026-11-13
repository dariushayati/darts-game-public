package com.darts.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.GameState
import com.darts.core.MIRROR_PROTOCOL_VERSION
import com.darts.core.MirrorSessionInfo
import com.darts.core.MirrorSnapshot
import com.darts.core.MirrorUiState
import com.darts.core.MirrorView
import kotlinx.coroutines.delay
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

@Stable
class MirrorHostState internal constructor(
    val session: MirrorSessionInfo,
) {
    private val main = Handler(Looper.getMainLooper())
    private val seq = AtomicLong(0)
    private var server: MirrorServer? = null
    private var advertiser: MirrorAdvertiser? = null

    var isSharing by mutableStateOf(false)
        private set
    var showShare by mutableStateOf(false)
        private set
    var discoveryStatus by mutableStateOf<String?>(null)
        private set
    var pendingClients by mutableStateOf<List<MirrorPendingClient>>(emptyList())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val port: Int get() = server?.port ?: DEFAULT_MIRROR_PORT
    val clientCount: Int get() = server?.clientCount ?: 0

    fun openShare(context: Context) {
        showShare = true
        if (!isSharing) start(context)
    }

    fun dismissShare() {
        showShare = false
    }

    fun start(context: Context) {
        if (isSharing) return
        error = null
        val next = MirrorServer(session)
        next.onPendingChanged = { clients -> main.post { pendingClients = clients } }
        if (!next.start()) {
            error = "Could not start sharing. Check that Wi-Fi is on and try again."
            return
        }
        server = next
        val nextAdvertiser = MirrorAdvertiser(context.applicationContext, session, next.port) { status ->
            main.post { discoveryStatus = status }
        }
        nextAdvertiser.start()
        advertiser = nextAdvertiser
        isSharing = true
    }

    fun stopSharing() {
        advertiser?.stop()
        advertiser = null
        server?.stop()
        server = null
        isSharing = false
        discoveryStatus = null
        pendingClients = emptyList()
        error = null
        showShare = false
    }

    fun approve(address: String) {
        server?.approve(address)
    }

    fun deny(address: String) {
        server?.deny(address)
    }

    fun publish(
        state: GameState? = null,
        ui: MirrorUiState = MirrorUiState(),
        view: MirrorView? = null,
    ) {
        server?.publish(
            MirrorSnapshot(
                seq = seq.incrementAndGet(),
                session = session,
                state = state,
                ui = ui,
                view = view,
            ),
        )
    }

    internal fun destroy() {
        advertiser?.stop()
        advertiser = null
        server?.stop()
        server = null
    }
}

@Composable
fun rememberMirrorHost(game: GameType): MirrorHostState {
    val session = remember(game) {
        MirrorSessionInfo(
            gameId = UUID.randomUUID().toString(),
            gameType = game.name,
            gameTitle = game.title,
            code = Random.nextInt(1000, 10000).toString(),
            hostName = mirrorHostName(),
        )
    }
    val host = remember(session) { MirrorHostState(session) }
    DisposableEffect(host) {
        onDispose { host.destroy() }
    }
    return host
}

fun mirrorHostName(): String = (Build.MODEL ?: "")
    .filter { it.code in 0x20..0x7E }
    .take(40)
    .ifBlank { "Darty Time" }

@Composable
fun MirrorSharePill(host: MirrorHostState) {
    val context = LocalContext.current
    val color = if (host.isSharing) DartsPalette.Green else DartsPalette.Muted
    val background = if (host.isSharing) {
        DartsPalette.Green.copy(alpha = 0.16f)
    } else {
        DartsPalette.ChipOff
    }
    val border = if (host.isSharing) {
        DartsPalette.Green.copy(alpha = 0.7f)
    } else {
        DartsPalette.Border
    }
    Text(
        text = "SHARE",
        color = color,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(50))
            .clickable { host.openShare(context) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
fun MirrorHostOverlays(host: MirrorHostState) {
    val context = LocalContext.current
    LaunchedEffect(host.pendingClients) {
        if (host.pendingClients.isNotEmpty()) host.dismissShare()
    }

    host.pendingClients.firstOrNull()?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = DartsPalette.Surface,
            titleContentColor = DartsPalette.Text,
            textContentColor = DartsPalette.Muted,
            title = {
                Text("ALLOW SECOND SCREEN?", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            },
            text = {
                Text("${request.deviceName} (${request.address}) wants to mirror this game.")
            },
            confirmButton = {
                TextButton(
                    onClick = { host.approve(request.address) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = DartsPalette.BlueBright,
                    ),
                ) {
                    Text("ALLOW", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { host.deny(request.address) },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = DartsPalette.Red,
                    ),
                ) {
                    Text("DENY", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (host.showShare) {
        SecondScreenDialog(
            session = host.session,
            address = MirrorNetwork.localIpv4Address(context),
            port = host.port,
            clientCount = { host.clientCount },
            discoveryStatus = host.discoveryStatus,
            error = host.error,
            onStop = host::stopSharing,
            onDismiss = host::dismissShare,
        )
    }
}

@Composable
private fun SecondScreenDialog(
    session: MirrorSessionInfo,
    address: String?,
    port: Int,
    clientCount: () -> Int,
    discoveryStatus: String?,
    error: String?,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val connected = remember(tick) { clientCount() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DartsPalette.Surface,
        titleContentColor = DartsPalette.Text,
        textContentColor = DartsPalette.Muted,
        title = {
            Text("SECOND SCREEN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) {
                    Text(error, color = DartsPalette.Red)
                } else {
                    Text("On the other device open Darty Time and tap JOIN A GAME. This game should appear under NEARBY SHARED GAMES; both devices must be on the same Wi-Fi.")
                    Text("New screens need your approval before they can mirror.")
                    Text("Address", color = DartsPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        text = address?.let { "$it:$port" } ?: "No Wi-Fi address found",
                        color = DartsPalette.Text,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text("Code", color = DartsPalette.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(
                        text = session.code,
                        color = DartsPalette.Green,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                    Text(
                        text = if (connected > 0) {
                            "$connected second screen${if (connected == 1) "" else "s"} connected"
                        } else {
                            "Waiting for a second screen…"
                        },
                        color = DartsPalette.Muted,
                        fontSize = 12.sp,
                    )
                    Text(
                        text = "Protocol v$MIRROR_PROTOCOL_VERSION · nearby discovery ${discoveryStatus ?: "…"}",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                    )
                }
            }
        },
        confirmButton = {
            if (error != null) {
                TextButton(onClick = onDismiss) { Text("CLOSE") }
            } else {
                TextButton(onClick = onStop) { Text("STOP SHARING") }
            }
        },
        dismissButton = {
            if (error == null) {
                TextButton(onClick = onDismiss) { Text("DONE") }
            }
        },
    )
}
