package com.darts.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.darts.app.autoscore.AutoScoreEngine
import com.darts.app.autoscore.AutoScoreEngineHost
import com.darts.app.autoscore.AutoScoreSettings
import com.darts.app.autoscore.AutoScoreState
import com.darts.app.autoscore.AutoScoredDart
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the camera engine for the in-game input path, separate from the standalone
 * camera test screen. It turns the engine's cumulative dart list into a stream of
 * newly detected darts and signals the end of a three-dart turn.
 */
object CameraScoringHost {
    @Volatile
    var controller: CameraScoringController? = null
}

class CameraScoringController internal constructor(
    private val settingsProvider: () -> AutoScoreSettings,
    private val engineFactory: () -> AutoScoreEngine,
    dispatcher: CoroutineDispatcher,
    initialZoom: Float = 1f,
    private val zoomLoader: () -> Float = { initialZoom },
    private val zoomSaver: (Float) -> Unit = {},
    private val retryDelayMs: Long = 1_500L,
    private val clearSettleMs: Long = 0L,
) {
    constructor(context: Context) : this(
        settingsProvider = {
            val store = AppSettingsStore(context.applicationContext)
            AutoScoreSettings(
                softBoard = store.loadAutoScoreSoftBoard(),
                forceCpu = store.loadAutoScoreForceCpu(),
                debugImages = store.loadAutoScoreDebugImages(),
                trackingIntervalMs = store.loadCameraTrackingRate().intervalMs,
            )
        },
        engineFactory = { AutoScoreEngineHost.get(context.applicationContext) },
        dispatcher = Dispatchers.Main.immediate,
        initialZoom = AppSettingsStore(context.applicationContext).loadCameraZoom(),
        zoomLoader = { AppSettingsStore(context.applicationContext).loadCameraZoom() },
        zoomSaver = { AppSettingsStore(context.applicationContext).saveCameraZoom(it) },
        clearSettleMs = 2_500L,
    )

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val uninitializedTurn = Any()
    private val lock = Any()

    @Volatile
    private var engine: AutoScoreEngine? = null
    private var engineJob: Job? = null

    private val _state = MutableStateFlow<AutoScoreState>(AutoScoreState.Idle)
    val state: StateFlow<AutoScoreState> = _state.asStateFlow()

    private val _darts = MutableStateFlow<List<AutoScoredDart>>(emptyList())
    val darts: StateFlow<List<AutoScoredDart>> = _darts.asStateFlow()

    private val _detected = MutableSharedFlow<AutoScoredDart>(extraBufferCapacity = 8)
    val detected: SharedFlow<AutoScoredDart> = _detected.asSharedFlow()

    private val _turnComplete = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val turnComplete: SharedFlow<Unit> = _turnComplete.asSharedFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile
    var zoomRatio: Float = initialZoom

    @Volatile
    var trackingIntervalMs: Long = 200L

    @Volatile
    private var redetectRequested = false

    @Volatile
    private var active = false
    private var needsRoundStart = false
    private var waitingForBoardClear = false
    private var prepInFlight = false
    private var startingRound = false
    private var nextStartAttemptAt = 0L
    private var boardClearSince = 0L
    private var earlyTurnEnd = false
    private var lastTurnKey: Any? = uninitializedTurn
    private var lastEmitted = 0
    private var turnClosed = false

    fun start() {
        synchronized(lock) {
            if (active) return
            zoomRatio = runCatching { zoomLoader() }.getOrDefault(zoomRatio)
            trackingIntervalMs = runCatching { settings().trackingIntervalMs }
                .getOrDefault(trackingIntervalMs)
                .coerceIn(100L, 1_000L)
            active = true
            needsRoundStart = false
            waitingForBoardClear = true
            boardClearSince = 0L
            earlyTurnEnd = false
            lastEmitted = 0
            turnClosed = false
            _darts.value = emptyList()
        }
        val current = engine
        if (current == null) {
            runCatching {
                val created = engineFactory()
                created.clearForNewOwner()
                engine = created
                engineJob = scope.launch { created.state.collect(::onEngineState) }
                created.initialize(settings())
            }.onFailure { error ->
                Log.e(TAG, "Camera engine could not start", error)
                _state.value = AutoScoreState.Error(
                    error.message ?: "Camera engine could not start",
                )
            }
        } else {
            runCatching { current.updateSettings(settings()) }
                .onFailure { error ->
                    Log.e(TAG, "Camera settings update failed", error)
                    _state.value = AutoScoreState.Error(
                        error.message ?: "Camera settings update failed",
                    )
                }
        }
    }

    /** Called whenever the game's turn changes. Waits for the old darts to be removed. */
    fun beginTurn(turnKey: Any?) {
        synchronized(lock) {
            if (lastTurnKey !== uninitializedTurn && lastTurnKey == turnKey) return
            lastTurnKey = turnKey
            needsRoundStart = false
            waitingForBoardClear = true
            boardClearSince = 0L
        }
        _state.value = AutoScoreState.Calibrating
    }

    fun submitFrame(frame: Bitmap, rotationDegrees: Int = 0) {
        if (!active) {
            frame.recycle()
            return
        }
        val current = engine
        if (current == null || !current.ready.value) {
            frame.recycle()
            return
        }

        val waiting = synchronized(lock) {
            if (waitingForBoardClear) {
                // A re-detect is meaningless while waiting for the old darts to be removed.
                redetectRequested = false
                true
            } else {
                false
            }
        }
        if (waiting) {
            waitForBoardClear(current, frame, rotationDegrees)
            return
        }

        if (redetectRequested) {
            redetectRequested = false
            current.redetect(frame, rotationDegrees)
            return
        }

        val beginRound = synchronized(lock) {
            val begin = needsRoundStart
            if (begin) {
                needsRoundStart = false
                lastEmitted = 0
                turnClosed = false
                _darts.value = emptyList()
            }
            begin
        }
        if (beginRound) {
            current.beginRound(frame, rotationDegrees)
        } else {
            current.processFrame(frame, rotationDegrees)
        }
    }

    /**
     * Starts the next native round only once the player has taken the previous darts out.
     * This avoids calibrating on a hand/arm or on top of the old darts, which made the
     * native start fail after the third dart.
     */
    private fun waitForBoardClear(
        current: AutoScoreEngine,
        frame: Bitmap,
        rotationDegrees: Int,
    ) {
        val shouldCheck = synchronized(lock) {
            if (prepInFlight) {
                false
            } else {
                prepInFlight = true
                true
            }
        }
        if (!shouldCheck) {
            frame.recycle()
            return
        }
        _state.value = AutoScoreState.Calibrating
        scope.launch {
            var handedToEngine = false
            try {
                val guide = current.detectPrepBoard(frame, rotationDegrees)
                val clear = guide?.boardBox != null && guide.dartTips.isEmpty()
                val now = System.currentTimeMillis()
                val action = synchronized(lock) {
                    if (!clear) {
                        boardClearSince = 0L
                        BoardAction.WAIT
                    } else {
                        if (boardClearSince == 0L) boardClearSince = now
                        val settled = now - boardClearSince >= clearSettleMs
                        if (settled && now >= nextStartAttemptAt) {
                            val early = earlyTurnEnd
                            earlyTurnEnd = false
                            waitingForBoardClear = false
                            needsRoundStart = false
                            startingRound = true
                            boardClearSince = 0L
                            lastEmitted = 0
                            turnClosed = false
                            _darts.value = emptyList()
                            if (early) BoardAction.EMIT_AND_START_ROUND else BoardAction.START_ROUND
                        } else {
                            BoardAction.WAIT
                        }
                    }
                }
                if (action != BoardAction.WAIT) {
                    if (action == BoardAction.EMIT_AND_START_ROUND) {
                        _turnComplete.tryEmit(Unit)
                    }
                    _state.value = AutoScoreState.Calibrating
                    handedToEngine = true
                    current.beginRound(frame, rotationDegrees)
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Board-clear check failed", error)
            } finally {
                if (!handedToEngine) frame.recycle()
                synchronized(lock) { prepInFlight = false }
            }
        }
    }

    fun requestRedetect() {
        redetectRequested = true
    }

    /**
     * The game ended the turn itself (manual END TURN). Wait for the board to clear,
     * then start the next native round.
     */
    fun waitForTurnEnd() {
        synchronized(lock) {
            waitingForBoardClear = true
            needsRoundStart = false
            boardClearSince = 0L
            earlyTurnEnd = false
            turnClosed = true
        }
        _state.value = AutoScoreState.Calibrating
    }

    /**
     * The game ended the turn early (bust/checkout) but has already advanced its state.
     * Once the board is clear, signal turnComplete so the game can clear its ignore flag,
     * then start the next native round.
     */
    fun endTurnEarly() {
        synchronized(lock) {
            waitingForBoardClear = true
            needsRoundStart = false
            boardClearSince = 0L
            earlyTurnEnd = true
            turnClosed = true
        }
        _state.value = AutoScoreState.Calibrating
    }

    /** Restarts calibration after a board-detection error or a manual camera adjustment. */
    fun recalibrate() {
        synchronized(lock) {
            waitingForBoardClear = true
            needsRoundStart = false
            startingRound = false
            nextStartAttemptAt = 0L
            boardClearSince = 0L
            earlyTurnEnd = false
            redetectRequested = false
        }
        _state.value = AutoScoreState.Calibrating
    }

    fun stop() {
        runCatching { zoomSaver(zoomRatio) }
        synchronized(lock) {
            active = false
            needsRoundStart = false
            waitingForBoardClear = false
            prepInFlight = false
            startingRound = false
            nextStartAttemptAt = 0L
            boardClearSince = 0L
            redetectRequested = false
            lastTurnKey = uninitializedTurn
            lastEmitted = 0
            turnClosed = false
            _darts.value = emptyList()
            _lastError.value = null
        }
        _state.value = AutoScoreState.Idle
    }

    fun release() {
        stop()
        engineJob?.cancel()
        engineJob = null
        engine = null
        scope.cancel()
        // The engine is process-scoped and shared with the standalone camera screen.
        // It stays loaded until the process ends.
    }

    private fun onEngineState(state: AutoScoreState) {
        if (!active) return
        val waiting = synchronized(lock) { waitingForBoardClear }
        if (waiting) {
            // Keep the UI on "calibrating/waiting" while the old darts are still there;
            // a stale DartDetected state must not be re-emitted into the new turn.
            _state.value = AutoScoreState.Calibrating
            return
        }
        _state.value = state
        synchronized(lock) {
            when (state) {
                is AutoScoreState.DartDetected -> {
                    _lastError.value = null
                    _darts.value = state.darts
                    val start = lastEmitted.coerceAtMost(state.darts.size)
                    if (state.darts.size > start) {
                        state.darts.drop(start).forEach { _detected.tryEmit(it) }
                    }
                    lastEmitted = state.darts.size
                    if (state.dartsOnBoard >= TURN_DARTS && !turnClosed) {
                        turnClosed = true
                        waitingForBoardClear = true
                        needsRoundStart = false
                        boardClearSince = 0L
                        _state.value = AutoScoreState.Calibrating
                    }
                }

                is AutoScoreState.TurnComplete -> {
                    _darts.value = state.darts
                    lastEmitted = state.darts.size
                    waitingForBoardClear = true
                    needsRoundStart = false
                    boardClearSince = 0L
                    _state.value = AutoScoreState.Calibrating
                    if (!turnClosed) {
                        turnClosed = true
                        _turnComplete.tryEmit(Unit)
                    }
                }

                is AutoScoreState.Ready -> {
                    _darts.value = emptyList()
                    _lastError.value = null
                    startingRound = false
                    nextStartAttemptAt = 0L
                }

                AutoScoreState.Calibrating -> {
                    // A new native round has started; its dart list starts empty again.
                    _darts.value = emptyList()
                    lastEmitted = 0
                    turnClosed = false
                }

                is AutoScoreState.Error -> {
                    _lastError.value = state.message
                    if (startingRound) {
                        // The first frame for the new round was not good enough
                        // (hand, old darts, lighting). Wait and try again after the
                        // board is clear instead of leaving the camera stuck in Error.
                        startingRound = false
                        waitingForBoardClear = true
                        needsRoundStart = false
                        nextStartAttemptAt = System.currentTimeMillis() + retryDelayMs
                    }
                }

                AutoScoreState.Idle, is AutoScoreState.PlacingCamera,
                is AutoScoreState.CameraMoved, AutoScoreState.Redetecting -> Unit
            }
        }
    }

    private fun settings() = settingsProvider()

    private enum class BoardAction { WAIT, START_ROUND, EMIT_AND_START_ROUND }

    private companion object {
        const val TAG = "CameraScoring"
        const val TURN_DARTS = 3
    }
}
