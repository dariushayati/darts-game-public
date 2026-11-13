package com.darts.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun JoinGameScreen(
    onBack: () -> Unit,
    onJoined: (host: String, port: Int, code: String) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { AppSettingsStore(context) }
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(settings.loadMirrorAddress()) }
    var code by remember { mutableStateOf(settings.loadMirrorCode()) }
    var nearby by remember { mutableStateOf<List<DiscoveredGame>>(emptyList()) }
    var discoveryError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val discovery = MirrorDiscovery(
            context = context,
            onGames = { games -> nearby = games },
            onError = { message -> discoveryError = message },
        )
        discovery.start()
        onDispose { discovery.stop() }
    }

    fun connect(host: String, port: Int, codeText: String) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                MirrorClient(host, port, codeText).fetch()
            }
            busy = false
            when (result) {
                is MirrorFetchResult.Success,
                MirrorFetchResult.Pending -> {
                    settings.saveMirrorAddress("$host:$port")
                    settings.saveMirrorCode(codeText)
                    onJoined(host, port, codeText)
                }

                MirrorFetchResult.Denied ->
                    error = "The host declined this device."

                MirrorFetchResult.WrongCode ->
                    error = "The code doesn't match. Check the code shown on the host."

                MirrorFetchResult.Incompatible ->
                    error = "The host is running a different app version. Update both devices."

                MirrorFetchResult.Unreachable ->
                    error = "Couldn't reach that game. Check that the host is still sharing."
            }
        }
    }

    fun join() {
        val target = parseMirrorTarget(address)
        if (target == null) {
            error = "Enter the host address, for example 192.168.1.42:8787."
            return
        }
        val codeText = code.trim()
        if (codeText.isBlank()) {
            error = "Enter the code shown on the host device."
            return
        }
        connect(target.first, target.second, codeText)
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
                text = "JOIN A GAME",
                color = DartsPalette.Text,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Text(
                text = "Start a game on the other device, tap SHARE until it turns green, and keep both devices on the same Wi-Fi with the same app version.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )
            error?.let {
                Text(it, color = DartsPalette.Red, fontSize = 11.sp)
            }

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
                        text = "NEARBY SHARED GAMES",
                        color = DartsPalette.Muted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (nearby.isEmpty()) "SCANNING…" else "${nearby.size} FOUND",
                        color = DartsPalette.BlueBright,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
                discoveryError?.let {
                    Text(it, color = DartsPalette.Amber, fontSize = 11.sp)
                }
                if (nearby.isEmpty()) {
                    Text(
                        text = "No shared games yet. The host's SHARE button must be green, use the same app version, and be on the same Wi-Fi.",
                        color = DartsPalette.Muted,
                        fontSize = 11.sp,
                    )
                } else {
                    nearby.forEach { game ->
                        NearbyGameRow(
                            game = game,
                            enabled = !busy,
                            onClick = {
                                address = "${game.host}:${game.port}"
                                code = game.code
                                connect(game.host, game.port, game.code)
                            },
                        )
                    }
                }
            }

            Text(
                text = "OR JOIN MANUALLY",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "HOST ADDRESS",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        error = null
                    },
                    label = { Text("IP address or IP:port") },
                    placeholder = { Text("192.168.1.42:8787") },
                    singleLine = true,
                    enabled = !busy,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "CODE",
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.filter(Char::isDigit).take(4)
                        error = null
                    },
                    label = { Text("4-digit code") },
                    placeholder = { Text("0000") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = ::join,
                    enabled = !busy,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DartsPalette.Blue,
                        contentColor = DartsPalette.onColor(DartsPalette.Blue),
                    ),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text(
                        text = if (busy) "CONNECTING…" else "JOIN AS SECOND SCREEN",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        fontSize = 13.sp,
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("BACK", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
    }
}

@Composable
private fun NearbyGameRow(
    game: DiscoveredGame,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DartsPalette.PanelDeep)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = game.gameTitle,
                color = DartsPalette.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            )
            Text(
                text = listOfNotNull(
                    game.hostName.ifBlank { null },
                    "${game.host}:${game.port}",
                ).joinToString(" · "),
                color = DartsPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.height(48.dp),
            shape = RoundedCornerShape(50),
            border = BorderStroke(
                1.dp,
                if (enabled) DartsPalette.BlueBright else DartsPalette.Border,
            ),
            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = DartsPalette.BlueBright,
                disabledContentColor = DartsPalette.Muted,
            ),
        ) {
            Text(
                text = if (enabled) "JOIN" else "…",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }
    }
}
