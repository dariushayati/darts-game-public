package com.darts.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darts.app.autoscore.AutoScoreEngine
import com.darts.app.autoscore.AutoScoreEngineHost
import com.darts.app.autoscore.AutoScoreSettings
import com.darts.app.autoscore.AutoScoreState
import com.darts.app.autoscore.AutoScoredDart
import com.darts.app.autoscore.missAutoScoredDart
import com.darts.core.Ring
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Experimental camera auto-scoring screen. It owns the preview, feeds frames to
 * [AutoScoreEngine], and exposes the placement guide, detection status and a minimal
 * correction list. Wiring the confirmed darts into each game mode is a follow-up.
 */
@Composable
fun AutoScoreCameraScreen(
    settings: AutoScoreSettings,
    onSettingsChange: (AutoScoreSettings) -> Unit,
    onOpenLicences: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    var engine by remember { mutableStateOf<AutoScoreEngine?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
        runCatching { AutoScoreEngineHost.get(context) }
            .onSuccess { created ->
                created.clearForNewOwner()
                created.initialize(settings)
                engine = created
            }
            .onFailure { error ->
                initError = error.message ?: "BullTrack could not start"
            }
    }
    LaunchedEffect(engine, settings) { engine?.updateSettings(settings) }

    val startRequested = remember { AtomicBoolean(false) }
    val checkPlacementRequested = remember { AtomicBoolean(false) }
    val redetectRequested = remember { AtomicBoolean(false) }

    val idleState = remember { MutableStateFlow<AutoScoreState>(AutoScoreState.Idle) }
    val idleDarts = remember { MutableStateFlow<List<AutoScoredDart>>(emptyList()) }
    val autoState by (engine?.state ?: idleState).collectAsState()
    val darts by (engine?.darts ?: idleDarts).collectAsState()

    KeepScreenOn()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(DartsPalette.BackgroundDeep, DartsPalette.Background),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "BACK",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "AUTO-SCORE",
                    color = DartsPalette.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "LICENCES",
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier
                        .clickable { onOpenLicences() }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }

            Text(
                text = "Experimental camera scoring powered by BullTrack. Keep the whole board in frame; " +
                    "the camera may sit at an angle.",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )

            if (!hasPermission) {
                PermissionCard(
                    onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            } else {
                CameraPreview(
                    engine = engine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp)),
                    startRequested = startRequested,
                    checkPlacementRequested = checkPlacementRequested,
                    redetectRequested = redetectRequested,
                    onError = { initError = it },
                )
            }

            SettingsRow(settings = settings, onSettingsChange = onSettingsChange)

            StatusCard(
                state = autoState,
                initError = initError,
                darts = darts,
            )

            if (darts.isNotEmpty()) {
                DartsMiniMap(
                    darts = darts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                )
                CorrectionList(
                    darts = darts,
                    onRemove = { index ->
                        engine?.correctDarts(darts.filterIndexed { i, _ -> i != index })
                    },
                    onAddMiss = {
                        if (darts.size < 3) {
                            engine?.correctDarts(darts + missAutoScoredDart(needsCheck = false))
                        }
                    },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { checkPlacementRequested.set(true) },
                    enabled = engine != null && hasPermission,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("CHECK PLACEMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { startRequested.set(true) },
                    enabled = engine != null && hasPermission,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DartsPalette.Blue),
                ) {
                    Text("START ROUND", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { redetectRequested.set(true) },
                    enabled = engine != null && darts.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("RE-DETECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { engine?.endTurn() },
                    enabled = engine != null && darts.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DartsPalette.Green),
                ) {
                    Text("END TURN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Private/internal build only. BullTrack and its YOLO11s model are AGPL-3.0; " +
                    "distributing this app requires releasing the complete source under AGPL-3.0.",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun CameraPreview(
    engine: AutoScoreEngine?,
    modifier: Modifier,
    startRequested: AtomicBoolean,
    checkPlacementRequested: AtomicBoolean,
    redetectRequested: AtomicBoolean,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomState by remember { mutableStateOf<ZoomState?>(null) }
    val zoomRatioState = remember { mutableFloatStateOf(AppSettingsStore(context).loadCameraZoom()) }
    var zoomRatio by zoomRatioState
    var linearZoom by remember { mutableFloatStateOf(0f) }
    val currentCamera by rememberUpdatedState(camera)
    val currentZoomState by rememberUpdatedState(zoomState)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastAnalysisAt = remember { AtomicLong(0L) }
    val frameIntervalMs = remember {
        AppSettingsStore(context).loadCameraTrackingRate().intervalMs
    }

    DisposableEffect(Unit) {
        onDispose {
            AppSettingsStore(context).saveCameraZoom(zoomRatioState.floatValue)
            executor.shutdown()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            update = { previewView = it },
        )

        // Transparent gesture layer above the preview, below the zoom controls.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        val state = currentZoomState ?: return@detectTransformGestures
                        val next = (zoomRatioState.floatValue * zoomChange)
                            .coerceIn(state.minZoomRatio, state.maxZoomRatio)
                        zoomRatio = next
                        currentCamera?.cameraControl?.setZoomRatio(next)
                    }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.48f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ZOOM ${"%.1f".format(zoomRatio)}×",
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
                            val state = currentZoomState ?: return@clickable
                            zoomRatio = state.minZoomRatio
                            currentCamera?.cameraControl?.setZoomRatio(state.minZoomRatio)
                        }
                        .padding(4.dp),
                )
            }
            Slider(
                value = linearZoom,
                onValueChange = { value ->
                    linearZoom = value
                    currentCamera?.cameraControl?.setLinearZoom(value)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Camera zoom" },
                enabled = currentZoomState?.let { it.maxZoomRatio > it.minZoomRatio } == true,
                colors = SliderDefaults.colors(
                    thumbColor = DartsPalette.BlueBright,
                    activeTrackColor = DartsPalette.Blue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
        }
    }

    LaunchedEffect(previewView, engine) {
        val view = previewView ?: return@LaunchedEffect
        if (engine == null) return@LaunchedEffect
        val provider = runCatching { context.cameraProvider() }.getOrElse { error ->
            onError(error.message ?: "Camera could not start")
            return@LaunchedEffect
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        analysis.setAnalyzer(executor) { image ->
            val now = SystemClock.elapsedRealtime()
            if (now - lastAnalysisAt.get() < frameIntervalMs) {
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
            when {
                startRequested.getAndSet(false) -> engine.beginRound(bitmap, rotation)
                checkPlacementRequested.getAndSet(false) -> engine.checkPlacement(bitmap, rotation)
                redetectRequested.getAndSet(false) -> engine.redetect(bitmap, rotation)
                else -> engine.processFrame(bitmap, rotation)
            }
        }
        try {
            provider.unbindAll()
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            camera = bound
            val rememberedZoom = zoomRatio
            var initialZoomApplied = false
            val observer = Observer<ZoomState> { state ->
                zoomState = state
                if (!initialZoomApplied) {
                    initialZoomApplied = true
                    bound.cameraControl.setZoomRatio(
                        rememberedZoom.coerceIn(state.minZoomRatio, state.maxZoomRatio),
                    )
                }
                zoomRatio = state.zoomRatio
                linearZoom = state.linearZoom
            }
            bound.cameraInfo.zoomState.observe(lifecycleOwner, observer)
            try {
                awaitCancellation()
            } finally {
                bound.cameraInfo.zoomState.removeObserver(observer)
                camera = null
                zoomState = null
            }
        } catch (error: Throwable) {
            onError(error.message ?: "Camera could not start")
        } finally {
            provider.unbindAll()
        }
    }
}

internal suspend fun Context.cameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { continuation.resume(it) }
                    .onFailure { continuation.resumeWithException(it) }
            },
            ContextCompat.getMainExecutor(this),
        )
        continuation.invokeOnCancellation { future.cancel(true) }
    }

@Composable
private fun PermissionCard(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "CAMERA PERMISSION",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = "Auto-scoring needs the camera to find the board and your darts. " +
                "Frames are processed on-device and are not uploaded.",
            color = DartsPalette.Text,
            fontSize = 12.sp,
        )
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DartsPalette.Blue),
        ) {
            Text("GRANT CAMERA ACCESS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsRow(
    settings: AutoScoreSettings,
    onSettingsChange: (AutoScoreSettings) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingChip("SOFT BOARD", settings.softBoard) {
            onSettingsChange(settings.copy(softBoard = it))
        }
        SettingChip("CPU YOLO", settings.forceCpu) {
            onSettingsChange(settings.copy(forceCpu = it))
        }
        SettingChip("DEBUG", settings.debugImages) {
            onSettingsChange(settings.copy(debugImages = it))
        }
    }
}

@Composable
private fun SettingChip(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (checked) DartsPalette.Blue else DartsPalette.SurfaceRaised)
            .border(
                1.dp,
                if (checked) DartsPalette.Blue else DartsPalette.Border,
                RoundedCornerShape(10.dp),
            )
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (checked) Color.White else DartsPalette.Text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun StatusCard(
    state: AutoScoreState,
    initError: String?,
    darts: List<AutoScoredDart>,
) {
    val text = when (state) {
        AutoScoreState.Idle -> "Ready. Check placement, then start a round."
        is AutoScoreState.PlacingCamera ->
            state.guide?.message ?: "Checking where the board is\u2026"
        AutoScoreState.Calibrating -> "Calibrating the board from the first frame\u2026"
        is AutoScoreState.Ready -> "Board ready. Throw your darts."
        is AutoScoreState.DartDetected -> "Darts detected: ${state.dartsOnBoard}/3"
        is AutoScoreState.CameraMoved ->
            "Camera moved or board lost. Re-detecting\u2026"
        AutoScoreState.Redetecting -> "Re-detecting the board\u2026"
        is AutoScoreState.TurnComplete -> "Turn complete. Review the detected darts."
        is AutoScoreState.Error -> state.message
    }
    val warning = initError ?: (state as? AutoScoreState.Error)?.message
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(
                1.dp,
                if (warning != null) DartsPalette.Red else DartsPalette.Border,
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "STATUS",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        Text(
            text = text,
            color = if (warning != null) DartsPalette.Red else DartsPalette.Text,
            fontSize = 13.sp,
        )
        if (darts.any { it.needsCheck }) {
            Text(
                text = "One or more darts landed near a line. Check them before scoring.",
                color = DartsPalette.Amber,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CorrectionList(
    darts: List<AutoScoredDart>,
    onRemove: (Int) -> Unit,
    onAddMiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CORRECTIONS",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        darts.forEachIndexed { index, dart ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${index + 1}. ${dartLabel(dart)}" +
                        if (dart.needsCheck) "  (check)" else "",
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "REMOVE",
                    color = DartsPalette.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clickable { onRemove(index) }
                        .padding(6.dp),
                )
            }
        }
        if (darts.size < 3) {
            Text(
                text = "+ ADD MISS",
                color = DartsPalette.BlueBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clickable { onAddMiss() }
                    .padding(vertical = 4.dp),
            )
        }
        Text(
            text = "Changing a dart's score from this screen is not wired yet; tap the game board after " +
                "returning to the match for now.",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun DartsMiniMap(darts: List<AutoScoredDart>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f * 0.86f
        drawCircle(DartsPalette.SurfaceRaised, radius, centre)
        listOf(1f, 0.8f, 0.629f, 0.45f, 0.2f, 0.0935f).forEach { fraction ->
            drawCircle(
                color = DartsPalette.Border,
                radius = radius * fraction,
                center = centre,
                style = Stroke(width = 1f),
            )
        }
        darts.forEachIndexed { index, dart ->
            val x = dart.hit.x ?: 0f
            val y = dart.hit.y ?: 0f
            val color = when (index) {
                0 -> DartsPalette.BlueBright
                1 -> DartsPalette.Green
                else -> DartsPalette.Amber
            }
            val point = centre + Offset(x * radius, y * radius)
            drawCircle(color, 12f, point)
            drawCircle(Color.White, 4f, point)
        }
    }
}

private fun dartLabel(dart: AutoScoredDart): String {
    val hit = dart.hit
    if (hit.isMiss) return "MISS"
    val number = hit.number ?: return "?"
    return when (hit.ring) {
        Ring.INNER_BULL -> "INNER BULL 50"
        Ring.OUTER_BULL -> "OUTER BULL 25"
        Ring.DOUBLE -> "DOUBLE $number"
        Ring.TREBLE -> "TREBLE $number"
        Ring.SINGLE -> "SINGLE $number"
        Ring.MISS -> "MISS"
    }
}
