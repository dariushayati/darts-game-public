package com.darts.app

import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.GameState
import com.darts.core.MirrorUiState
import com.darts.core.MirrorView

@Composable
fun GameScreenFrame(
    game: GameType,
    title: String,
    accent: Color,
    background: Brush,
    autodarts: AutodartsConfig?,
    boardStatus: AutodartsBoardStatus,
    modeLabel: String,
    finished: Boolean,
    exitMessage: String,
    mirrorView: MirrorView,
    onExit: () -> Unit,
    mirrorState: GameState? = null,
    mirrorUi: MirrorUiState = MirrorUiState(),
    decorations: @Composable BoxScope.() -> Unit = {},
    panel: @Composable ColumnScope.(twoPane: Boolean) -> Unit,
    board: @Composable BoxScope.() -> Unit,
    actionBar: @Composable (onMenu: () -> Unit) -> Unit,
    completeOverlay: @Composable () -> Unit,
) {
    KeepScreenOn()
    val mirror = rememberMirrorHost(game)
    val context = LocalContext.current
    val autoShare = remember { AppSettingsStore(context).loadShareEnabled() }
    LaunchedEffect(mirror, autoShare) {
        if (autoShare && !mirror.isSharing) mirror.start(context)
    }
    LaunchedEffect(mirror, mirror.isSharing, mirrorState, mirrorUi, mirrorView) {
        mirror.publish(state = mirrorState, ui = mirrorUi, view = mirrorView)
    }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var cameraPreviewView by remember { mutableStateOf<PreviewView?>(null) }
    val cameraState = remember { CameraScoringCameraState() }
    val cameraController = CameraScoringHost.controller

    BackHandler(enabled = !showExitConfirm) {
        if (finished) onExit() else showExitConfirm = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        decorations()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = LayoutPolicy.useTwoPane(
                LocalConfiguration.current.smallestScreenWidthDp,
                maxWidth.value,
                maxHeight.value,
            )
            val panelWidth = LayoutPolicy.panelWidthDp(maxWidth.value).dp
            Column(Modifier.fillMaxSize()) {
                GameTopBar(
                    title = title,
                    accent = accent,
                    modeLabel = modeLabel,
                    autodarts = autodarts,
                    boardStatus = boardStatus,
                    mirror = mirror,
                )
                if (twoPane) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier.width(panelWidth).fillMaxHeight(),
                        ) {
                            panel(twoPane)
                            Spacer(Modifier.weight(1f))
                            actionBar({ showExitConfirm = true })
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            board()
                        }
                    }
                } else {
                    panel(twoPane)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        board()
                    }
                    actionBar({ showExitConfirm = true })
                }
            }
        }
        if (autodarts?.mode == ScoringMode.CAMERA && !finished && cameraController != null) {
            CameraScoringSource(
                controller = cameraController,
                previewView = cameraPreviewView,
                cameraState = cameraState,
            )
            if (showCameraDialog) {
                CameraScoringDialog(
                    controller = cameraController,
                    cameraState = cameraState,
                    onPreviewView = { cameraPreviewView = it },
                    onDismiss = {
                        showCameraDialog = false
                        cameraPreviewView = null
                    },
                )
            } else {
                CameraScoringButton(
                    controller = cameraController,
                    onClick = { showCameraDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 86.dp, end = 16.dp),
                )
            }
        }
        if (finished) completeOverlay()
    }

    if (showExitConfirm) {
        GameExitDialog(
            message = exitMessage,
            accent = accent,
            onLeave = {
                showExitConfirm = false
                onExit()
            },
            onDismiss = { showExitConfirm = false },
        )
    }

    MirrorHostOverlays(mirror)
}

@Composable
private fun GameTopBar(
    title: String,
    accent: Color,
    modeLabel: String,
    autodarts: AutodartsConfig?,
    boardStatus: AutodartsBoardStatus,
    mirror: MirrorHostState,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .background(DartsPalette.Pill, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                text = modeLabel,
                color = DartsPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        AutodartsStatusIndicator(
            autodarts = autodarts,
            boardStatus = boardStatus,
            accent = accent,
        )
        Spacer(Modifier.width(8.dp))
        MirrorSharePill(mirror)
    }
}

@Composable
private fun GameExitDialog(
    message: String,
    accent: Color,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DartsPalette.Surface,
        titleContentColor = DartsPalette.Text,
        textContentColor = DartsPalette.Muted,
        title = {
            Text("LEAVE GAME?", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onLeave,
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
            ) {
                Text("LEAVE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = DartsPalette.Muted),
            ) {
                Text("KEEP PLAYING")
            }
        },
    )
}
