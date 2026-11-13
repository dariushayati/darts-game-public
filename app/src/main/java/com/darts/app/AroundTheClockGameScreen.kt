package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AroundTheClockEvent
import com.darts.core.AroundTheClockMode
import com.darts.core.AroundTheClockState
import com.darts.core.AutodartsBoardStatus
import com.darts.core.DartHit
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AroundTheClockGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(AroundTheClockMode.newGame(players)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { aroundTheClockMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<AroundTheClockState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    val targetOverlay = rememberAroundTheClockBoardOverlay(
        target = state.target,
        hitTick = flashTick,
    )

    fun applyDart(hit: DartHit) {
        if (state.finished) return
        val previousPlayerIndex = state.currentPlayerIndex
        undoStack.add(state)
        val reduction = AroundTheClockMode.apply(state, hit)
        state = reduction.state
        val targetHit = reduction.events.any { it is AroundTheClockEvent.TargetHit }
        val visitAdvanced = state.currentPlayerIndex != previousPlayerIndex || state.finished

        flashTick++
        lastTarget = if (visitAdvanced) null else hit.number
        if (visitAdvanced) {
            recentHits.clear()
        } else if (!hit.isMiss && hit.number != null) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }

        if (soundEnabled) {
            sounds.play(if (targetHit) SoundCue.LOCK else SoundCue.REMOVE)
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            recentHits.clear()
            flashTick++
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = AroundTheClockMode.advanceTurn(state).state
        lastTarget = null
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = AroundTheClockMode.newGame(players)
        undoStack.clear()
        lastTarget = null
        recentHits.clear()
        flashTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { applyDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != AroundTheClockMode.DARTS_PER_TURN) {
                state = AroundTheClockMode.advanceTurn(state).state
                lastTarget = null
                recentHits.clear()
                flashTick++
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick)
    GameScreenFrame(
        game = GameType.AROUND_THE_CLOCK,
        title = "AROUND THE CLOCK",
        accent = DartsPalette.DartCyan,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintRingRun,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = if (state.finished) "COMPLETE" else "TARGET ${state.labelFor(state.currentPlayer.id)}",
        finished = state.finished,
        exitMessage = "Return to training setup? The current Around the Clock run will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = { twoPane ->
            AroundTheClockScoreboard(state)
            AroundTheClockTargetPanel(state)
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null,
                playScale = AROUND_THE_CLOCK_PLAY_SCALE,
                accent = DartsPalette.DartCyan,
                onHit = { if (autodarts == null) applyDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = targetOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.DartCyan,
                primaryColor = DartsPalette.DartCyan,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) {
                        endTurn()
                    } else {
                        applyDart(DartHit(null, 0, Ring.MISS))
                    }
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
            AroundTheClockCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun AroundTheClockScoreboard(state: AroundTheClockState) {
    val players = state.players
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val color = playerColor(player.colorIndex)
            val completed = state.progressFor(player.id)
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
                Text(
                    text = player.name.uppercase(),
                    color = if (active) color else DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                )
                Text(
                    text = "$completed",
                    color = DartsPalette.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = if (completed >= AroundTheClockMode.TARGETS.size) {
                        "DONE"
                    } else {
                        "NEXT ${state.labelFor(player.id)}"
                    },
                    color = if (completed >= AroundTheClockMode.TARGETS.size) {
                        DartsPalette.GreenText
                    } else {
                        DartsPalette.Muted
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AroundTheClockTargetPanel(state: AroundTheClockState) {
    val player = state.currentPlayer
    val completed = state.progressFor(player.id)
    val total = AroundTheClockMode.TARGETS.size
    val finished = completed >= total
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .background(DartsPalette.Surface, RoundedCornerShape(18.dp))
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (finished) "FINISHED" else "NEXT TARGET",
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Text(
            text = if (finished) "BULL" else state.labelFor(player.id),
            color = DartsPalette.DartCyan,
            fontSize = 58.sp,
            lineHeight = 58.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (finished) "$total / $total TARGETS" else "${completed + 1} OF $total",
            color = DartsPalette.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AroundTheClockMode.TARGETS.forEachIndexed { index, _ ->
                val done = index < completed
                val current = index == completed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (current) 8.dp else 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                done -> DartsPalette.DartCyan
                                current -> DartsPalette.BlueBright
                                else -> DartsPalette.ChipOff
                            },
                        ),
                )
            }
        }
        Text(
            text = "1 → 20 → BULL",
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
        Text(
            text = "Any single, double or treble counts. Misses keep your target.",
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}
