package com.darts.app

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.DartHit
import com.darts.core.GunslingerDifficulty
import com.darts.core.GunslingerEvent
import com.darts.core.GunslingerMode
import com.darts.core.GunslingerRules
import com.darts.core.GunslingerState
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BEEP_INTERVAL_MS = 500L
private const val TIMER_TICK_MS = 80L
private const val READY_DELAY_MS = 1800L
private const val TAKEOUT_TIMEOUT_MS = 12_000L

private enum class GunslingerPhase { WAITING, COUNTDOWN, LIVE, FINISHED }

@Composable
fun GunslingerGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    rules: GunslingerRules,
    soundEnabled: Boolean,
    voiceCalloutsEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(GunslingerMode.newGame(players, rules)) }
    RecordMatchWhenFinished(state.finished, onMatchFinished) { gunslingerMatchRecord(state) }

    var phase by remember { mutableStateOf(GunslingerPhase.COUNTDOWN) }
    var beepsPlayed by remember { mutableIntStateOf(0) }
    var turnStartedAt by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var resetTick by remember { mutableIntStateOf(0) }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    var awaitingTakeout by remember { mutableStateOf(false) }
    var sawTakeout by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val voice = rememberVoiceCallouts()
    val displayState = rememberNeutralBoardState(players)

    val turnKey = state.roundNumber to state.currentPlayerIndex

    LaunchedEffect(turnKey, resetTick, state.finished, awaitingTakeout) {
        if (state.finished) {
            phase = GunslingerPhase.FINISHED
            return@LaunchedEffect
        }
        if (awaitingTakeout) {
            phase = GunslingerPhase.WAITING
            return@LaunchedEffect
        }
        phase = GunslingerPhase.COUNTDOWN
        beepsPlayed = 0
        if (autodarts != null) delay(READY_DELAY_MS)
        repeat(state.rules.beeps) { index ->
            if (soundEnabled) sounds.play(SoundCue.BEEP)
            beepsPlayed = index + 1
            delay(BEEP_INTERVAL_MS)
        }
        if (soundEnabled) sounds.play(SoundCue.ADD)
        if (voiceCalloutsEnabled) voice.speakNumber(state.target)
        phase = GunslingerPhase.LIVE
        val started = SystemClock.elapsedRealtime()
        turnStartedAt = started
        now = started
    }

    LaunchedEffect(boardStatus, awaitingTakeout) {
        if (!awaitingTakeout) {
            sawTakeout = false
            return@LaunchedEffect
        }
        when (boardStatus) {
            AutodartsBoardStatus.TAKEOUT -> sawTakeout = true
            AutodartsBoardStatus.READY -> if (sawTakeout) {
                awaitingTakeout = false
                sawTakeout = false
            }
            else -> Unit
        }
    }

    LaunchedEffect(awaitingTakeout, turnKey, resetTick) {
        if (!awaitingTakeout) return@LaunchedEffect
        delay(TAKEOUT_TIMEOUT_MS)
        if (awaitingTakeout) {
            awaitingTakeout = false
            sawTakeout = false
        }
    }

    LaunchedEffect(phase, turnKey, resetTick) {
        while (phase == GunslingerPhase.LIVE) {
            now = SystemClock.elapsedRealtime()
            delay(TIMER_TICK_MS)
        }
    }

    val elapsed = if (phase == GunslingerPhase.LIVE) {
        (now - turnStartedAt).coerceAtLeast(0L)
    } else {
        0L
    }
    val potentialPoints = if (phase == GunslingerPhase.LIVE) {
        GunslingerMode.pointsFor(elapsed, state.rules)
    } else {
        state.rules.basePoints
    }

    fun applyDart(hit: DartHit) {
        if (state.finished || phase != GunslingerPhase.LIVE) return
        val dartElapsed = (SystemClock.elapsedRealtime() - turnStartedAt).coerceAtLeast(0L)
        val previousKey = state.roundNumber to state.currentPlayerIndex
        val reduction = GunslingerMode.apply(state, hit, dartElapsed)
        state = reduction.state
        val turnAdvanced = state.finished ||
            (state.roundNumber to state.currentPlayerIndex) != previousKey
        val scored = reduction.events.filterIsInstance<GunslingerEvent.TurnScored>().firstOrNull()
        val banked = reduction.events.filterIsInstance<GunslingerEvent.PointsBanked>().firstOrNull()

        flashTick++
        lastTarget = hit.number
        if (!hit.isMiss && hit.number != null && !turnAdvanced) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }

        if (soundEnabled) {
            val cue = when {
                scored != null && scored.allHit -> SoundCue.LOCK
                banked != null -> SoundCue.ADD
                else -> SoundCue.MISS
            }
            sounds.play(cue)
        }

        if (turnAdvanced) {
            recentHits.clear()
            lastTarget = null
            if (state.finished) {
                phase = GunslingerPhase.FINISHED
            } else if (autodarts != null) {
                awaitingTakeout = true
                sawTakeout = false
                phase = GunslingerPhase.WAITING
            } else {
                awaitingTakeout = false
                phase = GunslingerPhase.COUNTDOWN
            }
        }
    }

    fun endVisit() {
        if (state.finished || phase != GunslingerPhase.LIVE) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        val hadPoints = state.turnPoints > 0
        val dartElapsed = (SystemClock.elapsedRealtime() - turnStartedAt).coerceAtLeast(0L)
        val reduction = GunslingerMode.finishTurn(state, dartElapsed)
        state = reduction.state
        recentHits.clear()
        lastTarget = null
        flashTick++
        if (soundEnabled) sounds.play(if (hadPoints) SoundCue.ADD else SoundCue.MISS)
        phase = if (state.finished) GunslingerPhase.FINISHED else GunslingerPhase.COUNTDOWN
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        voice.stop()
        state = GunslingerMode.newGame(players, rules)
        phase = GunslingerPhase.COUNTDOWN
        awaitingTakeout = false
        sawTakeout = false
        beepsPlayed = 0
        lastTarget = null
        recentHits.clear()
        flashTick++
        resetTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { applyDart(it) },
        onVisitEnded = {
            when {
                state.finished -> Unit
                awaitingTakeout -> {
                    awaitingTakeout = false
                    sawTakeout = false
                }
                phase == GunslingerPhase.LIVE &&
                    state.dartsLeft != GunslingerMode.DARTS_PER_TURN -> endVisit()
            }
        },
    )

    val targetForBoard = if (phase == GunslingerPhase.LIVE) state.target else null
    val neighbourTargets = if (
        phase == GunslingerPhase.LIVE &&
        state.rules.difficulty == GunslingerDifficulty.NORMAL
    ) {
        GunslingerMode.neighbours(state.target)
    } else {
        emptySet()
    }
    val boardOverlay = rememberGunslingerBoardOverlay(
        target = targetForBoard,
        lastTarget = lastTarget,
        hitTick = flashTick,
        neighbourTargets = neighbourTargets,
    )
    val mirrorView = state.toMirrorView(
        lastTarget = lastTarget,
        recentHits = recentHits.toList(),
        flashTick = flashTick,
        live = phase == GunslingerPhase.LIVE,
        potentialPoints = potentialPoints,
        waitingForTakeout = awaitingTakeout,
    )

    GameScreenFrame(
        game = GameType.GUNSLINGER,
        title = "GUNSLINGER",
        accent = DartsPalette.Red,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintLavaDeep,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = "${state.rules.difficulty.name} · ROUND ${state.roundNumber}/${state.rules.rounds}",
        finished = state.finished,
        exitMessage = "Return to setup? The current Gunslinger game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = { twoPane ->
            GunslingerScoreboard(state)
            GunslingerTargetPanel(
                state = state,
                phase = phase,
                beepsPlayed = beepsPlayed,
                potentialPoints = potentialPoints,
                allPlayers = twoPane,
            )
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null && phase == GunslingerPhase.LIVE,
                playScale = GUNSLINGER_PLAY_SCALE,
                accent = DartsPalette.Red,
                onHit = { applyDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = boardOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Red,
                primaryColor = DartsPalette.Red,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = false,
                onUndo = {},
                onPrimary = {
                    if (autodarts != null) endVisit() else applyDart(DartHit(null, 0, Ring.MISS))
                },
                onMenu = onMenu,
                onReset = autodarts?.takeIf { it.mode == ScoringMode.AUTODARTS }?.let { cfg ->
                    {
                        scope.launch {
                            withContext(Dispatchers.IO) { AutodartsClient(cfg.url).reset() }
                        }
                        Unit
                    }
                },
            )
        },
        completeOverlay = {
            GunslingerCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun GunslingerScoreboard(state: GunslingerState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val color = playerColor(player.colorIndex)
            val last = state.lastTurnPoints(player.id)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) color.copy(alpha = 0.16f) else DartsPalette.Surface)
                    .border(
                        width = if (active) 1.5.dp else 1.dp,
                        color = if (active) color.copy(alpha = 0.9f) else DartsPalette.Border,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .padding(vertical = 7.dp, horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.size(7.dp).background(color, CircleShape))
                    Text(
                        text = player.name.replace("Player ", "P"),
                        color = if (active) DartsPalette.Text else DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 66.dp),
                    )
                }
                Text(
                    text = "${state.pointsFor(player.id)}",
                    color = if (active) DartsPalette.Text else DartsPalette.Muted,
                    fontSize = 21.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (last > 0) "LAST +$last" else "NO SCORE",
                    color = DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(DartsPalette.Border),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                (state.pointsFor(player.id).toFloat() /
                                    (state.rules.rounds * state.rules.basePoints *
                                        GunslingerMode.DARTS_PER_TURN).toFloat())
                                    .coerceIn(0f, 1f),
                            )
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun GunslingerTargetPanel(
    state: GunslingerState,
    phase: GunslingerPhase,
    beepsPlayed: Int,
    potentialPoints: Int,
    allPlayers: Boolean = false,
) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, CircleShape)
                    .border(2.dp, DartsPalette.NamePlate, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = player.name.take(1).uppercase(),
                    color = DartsPalette.NamePlate,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = player.name.uppercase(),
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${state.roundNumber}/${state.rules.rounds} · " +
                        "${state.dartsLeft} DARTS · ${state.hits}/3 HITS",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when (phase) {
                        GunslingerPhase.LIVE -> "TARGET"
                        GunslingerPhase.WAITING -> "TAKE OUT"
                        else -> "GET READY"
                    },
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = if (phase == GunslingerPhase.LIVE) state.target.toString() else "• • •",
                    color = DartsPalette.Red,
                    fontSize = 30.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when (phase) {
                        GunslingerPhase.LIVE -> "FASTEST WINS"
                        GunslingerPhase.WAITING -> "CLEAR THE BOARD"
                        else -> "LISTEN"
                    },
                    color = if (phase == GunslingerPhase.LIVE) DartsPalette.Text else DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        val scoreFraction = (potentialPoints.toFloat() / state.rules.basePoints)
            .coerceIn(0f, 1f)
        val scoreColor = lerp(DartsPalette.Red, DartsPalette.Green, scoreFraction)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "NEXT HIT",
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$potentialPoints",
                color = scoreColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(50))
                .background(DartsPalette.SurfaceRaised)
                .border(1.dp, DartsPalette.Border, RoundedCornerShape(50)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(scoreFraction)
                    .height(11.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scoreColor),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "−${state.rules.pointsPerSecond} PTS/SEC",
                color = DartsPalette.Muted,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "BANKED ${state.turnPoints} · FLOOR ${state.rules.minimumPoints}",
                color = if (state.turnPoints > 0) DartsPalette.Green else DartsPalette.Muted,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(GunslingerMode.DARTS_PER_TURN) { index ->
                val filled = index < state.hits
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (filled) DartsPalette.Green else DartsPalette.Border),
                )
            }
        }

        when (phase) {
            GunslingerPhase.COUNTDOWN -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "BEEPS",
                        color = DartsPalette.Muted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    repeat(state.rules.beeps) { index ->
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    if (index < beepsPlayed) DartsPalette.Red else DartsPalette.Border,
                                    CircleShape,
                                ),
                        )
                    }
                    if (state.rules.beeps == 0) {
                        Text(
                            text = "GO",
                            color = DartsPalette.Red,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }
            GunslingerPhase.WAITING -> {
                Text(
                    text = "TAKE OUT THE DARTS · ${player.name.uppercase()} UP NEXT",
                    color = DartsPalette.Amber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            else -> {
                Text(
                    text = if (state.rules.difficulty == GunslingerDifficulty.NORMAL) {
                        "HIT ${state.target} OR A NEIGHBOUR · HALF VALUE"
                    } else {
                        "HIT ${state.target} ONLY"
                    },
                    color = DartsPalette.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (allPlayers) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                state.players.forEach { entry ->
                    val pColor = playerColor(entry.colorIndex)
                    val isActive = entry.id == player.id && !state.finished
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(7.dp).background(pColor, CircleShape))
                        Text(
                            text = entry.name,
                            color = if (isActive) DartsPalette.Text else DartsPalette.Muted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${state.hits(entry.id)} hits",
                            color = DartsPalette.Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${state.pointsFor(entry.id)}",
                            color = if (isActive) pColor else DartsPalette.Text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.End,
                        )
                    }
                }
            }
        }
    }
}
