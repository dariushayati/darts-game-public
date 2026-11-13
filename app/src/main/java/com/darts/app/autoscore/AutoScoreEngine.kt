package com.darts.app.autoscore

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.darts.app.BuildConfig
import io.github.thomasff.bulltrack.core.vision.FrameDetectionUpdate
import io.github.thomasff.bulltrack.core.vision.NativeDartDetector
import io.github.thomasff.bulltrack.core.vision.PrepBoardGuide
import io.github.thomasff.bulltrack.core.vision.RedetectDetectionUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns one BullTrack native session and serialises every call onto a single thread,
 * because the native session is not thread-safe. Camera frames are handed in as
 * ARGB_8888 bitmaps and recycled here once the detector is done with them.
 *
 * The state machine is deliberately free of Compose and CameraX so it can be unit
 * tested with a fake [AutoScoreDriver].
 */
class AutoScoreEngine internal constructor(
    private val driver: AutoScoreDriver,
    private val dispatcher: CoroutineDispatcher,
    private val ownsDispatcher: Boolean,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val busy = AtomicBoolean(false)
    private val _state = MutableStateFlow<AutoScoreState>(AutoScoreState.Idle)
    private val _darts = MutableStateFlow<List<AutoScoredDart>>(emptyList())
    private val _ready = MutableStateFlow(false)

    val state: StateFlow<AutoScoreState> = _state.asStateFlow()
    val darts: StateFlow<List<AutoScoredDart>> = _darts.asStateFlow()
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var settings = AutoScoreSettings()
    private var sessionId = 0L
    private var initialized = false
    private var roundActive = false
    private var closed = false
    private var lastFrameAt = Long.MIN_VALUE / 2
    private var lastRedetectAt = Long.MIN_VALUE / 2
    private var lastTimingLogAt = Long.MIN_VALUE / 2

    fun initialize(settings: AutoScoreSettings) {
        if (initialized || closed) return
        this.settings = settings
        runExclusive(null) {
            driver.init()
            driver.setYoloForceCpu(settings.forceCpu)
            driver.setDebugImagesEnabled(settings.debugImages)
            driver.warmUp()
            sessionId = driver.createSession()
            initialized = true
            _ready.value = true
            _state.value = AutoScoreState.Idle
        }
    }

    /** Starts a player turn from the first frame the camera delivered. */
    fun beginRound(firstFrame: Bitmap, rotationDegrees: Int = 0) {
        if (!initialized || sessionId == 0L || closed) {
            firstFrame.recycle()
            _state.value = AutoScoreState.Error("Auto-score engine is not ready")
            return
        }
        if (busy.get()) {
            firstFrame.recycle()
            return
        }
        roundActive = false
        lastFrameAt = Long.MIN_VALUE / 2
        lastRedetectAt = Long.MIN_VALUE / 2
        _darts.value = emptyList()
        runExclusive(firstFrame) {
            _state.value = AutoScoreState.Calibrating
            val update = driver.startRound(sessionId, firstFrame, rotationDegrees, settings.softBoard)
            val error = update.errorMessage
            if (error != null) {
                _state.value = AutoScoreState.Error(error)
                return@runExclusive
            }
            roundActive = true
            _state.value = AutoScoreState.Ready(update.startDartCount)
        }
    }

    /** Feeds one analysis frame. Throttled; frames are recycled even when dropped. */
    fun processFrame(frame: Bitmap, rotationDegrees: Int = 0) {
        if (!roundActive || sessionId == 0L || closed || _darts.value.size >= MAX_DARTS) {
            frame.recycle()
            return
        }
        val now = clock()
        if (now - lastFrameAt < MIN_FRAME_INTERVAL_MS) {
            frame.recycle()
            return
        }
        lastFrameAt = now
        runExclusive(frame) {
            val update = driver.processFrame(sessionId, frame, rotationDegrees)
            handleFrameUpdate(update, frame, rotationDegrees)
            if (BuildConfig.DEBUG) {
                val now = clock()
                if (now - lastTimingLogAt >= TIMING_LOG_INTERVAL_MS) {
                    lastTimingLogAt = now
                    Log.d(TAG, driver.describeFrameStageTimes(sessionId))
                }
            }
        }
    }

    /** Re-reads the board after the camera moved or detection was lost. */
    fun redetect(frame: Bitmap, rotationDegrees: Int = 0) {
        if (!roundActive || sessionId == 0L || closed) {
            frame.recycle()
            return
        }
        if (busy.get()) {
            frame.recycle()
            return
        }
        runExclusive(frame) {
            _state.value = AutoScoreState.Redetecting
            applyRedetect(driver.redetect(sessionId, frame, rotationDegrees))
            lastRedetectAt = clock()
        }
    }

    /**
     * Called when the player ends a turn manually. Asks the native session for darts
     * that were never confirmed and merges them before reporting the turn complete.
     */
    fun endTurn() {
        if (!roundActive || sessionId == 0L || closed) return
        runExclusive(null) {
            val update = driver.findMissedDarts(sessionId)
            val missed = update.throws.map { it.toAutoScoredDart() }
            val merged = (_darts.value + missed).take(MAX_DARTS)
            _darts.value = merged
            roundActive = false
            _state.value = AutoScoreState.TurnComplete(merged)
        }
    }

    /** Applies setting changes without rebuilding the native session. */
    fun updateSettings(settings: AutoScoreSettings) {
        this.settings = settings
        if (!initialized || closed) return
        runExclusive(null) {
            driver.setYoloForceCpu(settings.forceCpu)
            driver.setDebugImagesEnabled(settings.debugImages)
        }
    }

    /**
     * Replaces the visible dart list after a manual correction. The native session is
     * not mutated; the corrected list is what the game should score.
     */
    fun correctDarts(darts: List<AutoScoredDart>) {
        val corrected = darts.take(MAX_DARTS)
        _darts.value = corrected
        _state.value = if (roundActive) {
            AutoScoreState.DartDetected(corrected, corrected.size)
        } else {
            AutoScoreState.TurnComplete(corrected)
        }
    }

    /** Runs [AutoScoreDriver.detectPrepBoard] to drive the placement guide. */
    fun checkPlacement(frame: Bitmap, rotationDegrees: Int = 0) {
        if (!initialized || roundActive || closed) {
            frame.recycle()
            return
        }
        if (busy.get()) {
            frame.recycle()
            return
        }
        runExclusive(frame) {
            _state.value = AutoScoreState.PlacingCamera(null)
            val guide = driver.detectPrepBoard(frame, rotationDegrees).toPlacementGuide()
            _state.value = AutoScoreState.PlacingCamera(guide)
        }
    }

    /**
     * Runs [AutoScoreDriver.detectPrepBoard] on the engine thread without changing the
     * round state. Used to wait until the player has removed the previous darts.
     */
    suspend fun detectPrepBoard(
        frame: Bitmap,
        rotationDegrees: Int = 0,
    ): PrepBoardGuide? = withContext(dispatcher) {
        if (closed) return@withContext null
        runCatching { driver.detectPrepBoard(frame, rotationDegrees) }.getOrNull()
    }

    /**
     * Clears the visible state when a different screen takes over the shared engine.
     * The native round is reset by the next [beginRound].
     */
    fun clearForNewOwner() {
        _darts.value = emptyList()
        _state.value = AutoScoreState.Idle
    }

    fun release() {
        if (closed) return
        closed = true
        roundActive = false
        _ready.value = false
        scope.launch {
            runCatching { if (sessionId != 0L) driver.destroySession(sessionId) }
            runCatching { driver.releaseDetector() }
            sessionId = 0L
            if (ownsDispatcher) (dispatcher as? ExecutorCoroutineDispatcher)?.close()
        }
    }

    private fun handleFrameUpdate(
        update: FrameDetectionUpdate,
        frame: Bitmap,
        rotationDegrees: Int,
    ) {
        val error = update.errorMessage
        if (error != null) {
            _state.value = AutoScoreState.Error(error)
            return
        }

        val newDarts = update.newThrows.map { throwResult ->
            throwResult?.toAutoScoredDart() ?: missAutoScoredDart()
        }
        if (newDarts.isNotEmpty()) {
            val merged = (_darts.value + newDarts).take(MAX_DARTS)
            _darts.value = merged
            _state.value = AutoScoreState.DartDetected(merged, merged.size)
            return
        }

        if (looksLikeCameraMoved(update.gateReason)) {
            _state.value = AutoScoreState.CameraMoved(update.gateReason)
            val now = clock()
            if (now - lastRedetectAt >= REDETECT_INTERVAL_MS) {
                lastRedetectAt = now
                _state.value = AutoScoreState.Redetecting
                applyRedetect(driver.redetect(sessionId, frame, rotationDegrees))
            }
            return
        }

        if (_state.value !is AutoScoreState.DartDetected) {
            _state.value = AutoScoreState.Ready(_darts.value.size)
        }
    }

    private fun applyRedetect(update: RedetectDetectionUpdate) {
        val redetected = update.throws.map { it.toAutoScoredDart() }
        if (redetected.isNotEmpty()) {
            _darts.value = redetected.take(MAX_DARTS)
        }
        _state.value = if (_darts.value.isEmpty()) {
            AutoScoreState.Ready(0)
        } else {
            AutoScoreState.DartDetected(_darts.value, _darts.value.size)
        }
    }

    private fun runExclusive(bitmap: Bitmap?, block: () -> Unit) {
        if (closed) {
            bitmap?.recycle()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            bitmap?.recycle()
            return
        }
        scope.launch {
            try {
                block()
            } catch (t: Throwable) {
                fail(t)
            } finally {
                bitmap?.recycle()
                busy.set(false)
            }
        }
    }

    private fun fail(t: Throwable) {
        Log.e(TAG, "Auto-score engine failure", t)
        _state.value = AutoScoreState.Error(
            t.message?.takeIf(String::isNotBlank) ?: t.javaClass.simpleName,
        )
    }

    companion object {
        private const val TAG = "AutoScoreEngine"
        private const val MAX_DARTS = 3
        private const val MIN_FRAME_INTERVAL_MS = 140L
        private const val REDETECT_INTERVAL_MS = 2_000L
        private const val TIMING_LOG_INTERVAL_MS = 2_000L

        fun create(context: Context): AutoScoreEngine {
            val executor = Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "bulltrack-autoscore").apply { isDaemon = true }
            }
            val driver = NativeAutoScoreDriver(
                NativeDartDetector(context.applicationContext.assets),
            )
            return AutoScoreEngine(
                driver = driver,
                dispatcher = executor.asCoroutineDispatcher(),
                ownsDispatcher = true,
            )
        }

        private fun looksLikeCameraMoved(reason: String): Boolean {
            val text = reason.lowercase()
            return text.contains("moved") ||
                text.contains("ecc_alignment_failed") ||
                text.contains("alignment_failed") ||
                text.contains("correlation_failed") ||
                text.contains("board_lost") ||
                text.contains("lost_board")
        }
    }
}
