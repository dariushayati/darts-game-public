package com.darts.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darts.app.autoscore.AutoScoreState
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Camera state shared between the always-on source and the on-demand preview dialog. */
class CameraScoringCameraState {
    var camera by mutableStateOf<Camera?>(null)
    var zoomState by mutableStateOf<ZoomState?>(null)
    var zoomRatio by mutableFloatStateOf(1f)
}

/**
 * Keeps the in-game camera alive. With a [previewView] it shows the preview and feeds the
 * engine; without one it runs headlessly, so scoring continues while the dialog is closed.
 */
@Composable
fun CameraScoringSource(
    controller: CameraScoringController,
    previewView: PreviewView?,
    cameraState: CameraScoringCameraState,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastAnalysisAt = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(previewView, controller) {
        controller.start()
        val provider = runCatching { context.cameraProvider() }.getOrElse { error ->
            Log.e(TAG, "Camera provider unavailable", error)
            return@LaunchedEffect
        }
        try {
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { image ->
                val now = SystemClock.elapsedRealtime()
                if (now - lastAnalysisAt.get() < controller.trackingIntervalMs) {
                    image.close()
                    return@setAnalyzer
                }
                lastAnalysisAt.set(now)
                val rotation = image.imageInfo.rotationDegrees
                val bitmap = runCatching { image.toBitmap() }.getOrElse {
                    image.close()
                    return@setAnalyzer
                }
                image.close()
                controller.submitFrame(bitmap, rotation)
            }

            val preview = previewView?.let { view ->
                Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
            }
            provider.unbindAll()
            val camera = if (preview != null) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            } else {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    analysis,
                )
            }
            cameraState.camera = camera
            var initialZoomApplied = false
            val observer = Observer<ZoomState> { state ->
                cameraState.zoomState = state
                if (!initialZoomApplied) {
                    initialZoomApplied = true
                    // Keep the stored zoom authoritative during the rebind; the camera
                    // briefly reports 1x before setZoomRatio takes effect.
                    val target = controller.zoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                    camera.cameraControl.setZoomRatio(target)
                    cameraState.zoomRatio = target
                    controller.zoomRatio = target
                } else {
                    cameraState.zoomRatio = state.zoomRatio
                    controller.zoomRatio = state.zoomRatio
                }
            }
            camera.cameraInfo.zoomState.observe(lifecycleOwner, observer)
            try {
                awaitCancellation()
            } finally {
                camera.cameraInfo.zoomState.removeObserver(observer)
                cameraState.camera = null
                cameraState.zoomState = null
            }
        } catch (error: Throwable) {
            Log.e(TAG, "In-game camera source failed", error)
        } finally {
            provider.unbindAll()
        }
    }
}

/** Bottom-corner camera control. Tapping opens the full preview/zoom/calibration dialog. */
@Composable
fun CameraScoringButton(
    controller: CameraScoringController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsState()
    val info = cameraStatusInfo(state)
    Box(
        modifier = modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(DartsPalette.Surface)
            .border(1.5.dp, info.color, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Camera controls" },
        contentAlignment = Alignment.Center,
    ) {
        CameraGlyph(color = info.color, modifier = Modifier.size(26.dp))
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp)
                .size(7.dp)
                .background(info.color, CircleShape),
        )
    }
}

/** Full preview with pinch/slider zoom, detection status and a re-detect action. */
@Composable
fun CameraScoringDialog(
    controller: CameraScoringController,
    cameraState: CameraScoringCameraState,
    onPreviewView: (PreviewView?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by controller.state.collectAsState()
    val lastError by controller.lastError.collectAsState()
    val info = cameraStatusInfo(state)
    val zoomState = cameraState.zoomState
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DartsPalette.BackgroundDeep)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "CAMERA",
                        color = DartsPalette.Text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "CLOSE",
                        color = DartsPalette.Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp),
                    )
                }

                Text(
                    text = "Pinch or use the slider to zoom. Re-detect after moving the phone " +
                        "or if the board is not being recognised.",
                    color = DartsPalette.Muted,
                    fontSize = 12.sp,
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, info.color, RoundedCornerShape(18.dp)),
                ) {
                    if (hasPermission) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PreviewView(viewContext).apply {
                                    scaleType = PreviewView.ScaleType.FIT_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    onPreviewView(this)
                                }
                            },
                            update = { onPreviewView(it) },
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoomChange, _ ->
                                        val state = cameraState.zoomState
                                            ?: return@detectTransformGestures
                                        val next = (cameraState.zoomRatio * zoomChange)
                                            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                                        cameraState.zoomRatio = next
                                        cameraState.camera?.cameraControl?.setZoomRatio(next)
                                    }
                                },
                        )
                        ZoomControls(
                            cameraState = cameraState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "TAP TO GRANT CAMERA ACCESS",
                                color = DartsPalette.Muted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                lastError?.let { message ->
                    Text(
                        text = message,
                        color = DartsPalette.Red,
                        fontSize = 11.sp,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .background(info.color, CircleShape)
                            .size(8.dp),
                    )
                    Text(
                        text = info.label,
                        color = info.color,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    val recalibrate = state is AutoScoreState.Error ||
                        state is AutoScoreState.Idle ||
                        state is AutoScoreState.Calibrating ||
                        state is AutoScoreState.PlacingCamera
                    OutlinedButton(
                        onClick = {
                            if (recalibrate) controller.recalibrate() else controller.requestRedetect()
                        },
                        shape = RoundedCornerShape(12.dp),
                        enabled = hasPermission,
                    ) {
                        Text(
                            text = if (recalibrate) "RECALIBRATE" else "RE-DETECT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DartsPalette.Blue,
                        ),
                    ) {
                        Text("DONE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { onPreviewView(null) }
    }
}

@Composable
private fun ZoomControls(
    cameraState: CameraScoringCameraState,
    modifier: Modifier = Modifier,
) {
    val zoomState = cameraState.zoomState
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.48f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ZOOM ${"%.1f".format(cameraState.zoomRatio)}\u00d7",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "PINCH OR DRAG",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "RESET",
                color = DartsPalette.BlueBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier
                    .clickable {
                        val min = cameraState.zoomState?.minZoomRatio ?: return@clickable
                        cameraState.zoomRatio = min
                        cameraState.camera?.cameraControl?.setZoomRatio(min)
                    }
                    .padding(4.dp),
            )
        }
        Slider(
            value = zoomState?.linearZoom ?: 0f,
            onValueChange = { value ->
                cameraState.camera?.cameraControl?.setLinearZoom(value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Camera zoom" },
            enabled = zoomState != null && zoomState.maxZoomRatio > zoomState.minZoomRatio,
            colors = SliderDefaults.colors(
                thumbColor = DartsPalette.BlueBright,
                activeTrackColor = DartsPalette.Blue,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}

@Composable
private fun CameraGlyph(color: Color, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.08f
        val bodyTop = size.height * 0.28f
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.06f, bodyTop),
            size = Size(size.width * 0.88f, size.height * 0.58f),
            cornerRadius = CornerRadius(size.width * 0.12f),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.30f, size.height * 0.12f),
            size = Size(size.width * 0.40f, size.height * 0.18f),
            cornerRadius = CornerRadius(size.width * 0.04f),
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.14f,
            center = Offset(size.width / 2f, bodyTop + size.height * 0.29f),
            style = Stroke(width = stroke),
        )
    }
}

internal data class CameraStatusInfo(val label: String, val color: Color)

internal fun cameraStatusInfo(state: AutoScoreState?): CameraStatusInfo = when (state) {
    null, AutoScoreState.Idle -> CameraStatusInfo("CAMERA IDLE", DartsPalette.Amber)
    is AutoScoreState.PlacingCamera -> CameraStatusInfo("PLACE", DartsPalette.Amber)
    AutoScoreState.Calibrating -> CameraStatusInfo("CALIBRATING", DartsPalette.Amber)
    is AutoScoreState.Ready -> CameraStatusInfo("READY", DartsPalette.Green)
    is AutoScoreState.DartDetected ->
        CameraStatusInfo("${state.dartsOnBoard}/3", DartsPalette.Green)
    is AutoScoreState.CameraMoved -> CameraStatusInfo("CAMERA MOVED", DartsPalette.Red)
    AutoScoreState.Redetecting -> CameraStatusInfo("RE-DETECTING", DartsPalette.Amber)
    is AutoScoreState.TurnComplete -> CameraStatusInfo("TURN DONE", DartsPalette.Green)
    is AutoScoreState.Error -> CameraStatusInfo("ERROR", DartsPalette.Red)
}

private const val TAG = "CameraScoring"
