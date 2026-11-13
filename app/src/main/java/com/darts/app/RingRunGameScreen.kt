package com.darts.app

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.MatchRecord
import com.darts.core.BoardGeometry
import com.darts.core.DartHit
import com.darts.core.Player
import com.darts.core.RingRunEvent
import com.darts.core.RingRunMode
import com.darts.core.RingRunRound
import com.darts.core.RingRunRules
import com.darts.core.RingRunState
import com.darts.core.Ring
import com.darts.core.SingleBand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RingRunGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(RingRunMode.newGame(players, RingRunRules())) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { ringRunMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<RingRunState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var lastPoints by remember { mutableStateOf<Int?>(null) }
    var lastHitLabel by remember { mutableStateOf<String?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    val ringRunBoardOverlay = rememberRingRunBoardOverlay(
        round = state.round,
        hitTick = flashTick,
        hitPoints = lastPoints,
    )

    fun throwDart(hit: DartHit) {
        if (state.finished) return
        val previousPlayer = state.currentPlayerIndex
        val previousRound = state.roundIndex
        undoStack.add(state)
        val reduction = RingRunMode.apply(state, hit)
        state = reduction.state
        val visitAdvanced = state.currentPlayerIndex != previousPlayer ||
            state.roundIndex != previousRound ||
            state.finished
        val points = reduction.events.filterIsInstance<RingRunEvent.DartScored>()
            .firstOrNull()?.points ?: 0
        lastTarget = if (visitAdvanced) null else hit.number
        lastPoints = if (visitAdvanced) null else points
        lastHitLabel = if (visitAdvanced) null else precisionHitLabel(hit)
        flashTick++
        if (visitAdvanced) {
            recentHits.clear()
        } else {
            hit.number?.let {
                recentHits.add(hit)
                while (recentHits.size > 3) recentHits.removeAt(0)
            }
        }
        if (soundEnabled) {
            if (points > 0) {
                sounds.play(
                    if (state.round == RingRunRound.BULL) SoundCue.LOCK else SoundCue.ADD,
                )
            } else {
                sounds.play(SoundCue.LIFE_LOST)
            }
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            lastPoints = null
            lastHitLabel = null
            recentHits.clear()
            flashTick++
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = RingRunMode.advanceTurn(state).state
        lastTarget = null
        lastPoints = null
        lastHitLabel = null
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = RingRunMode.newGame(players, RingRunRules())
        undoStack.clear()
        lastTarget = null
        lastPoints = null
        lastHitLabel = null
        recentHits.clear()
        flashTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { throwDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != state.rules.dartsPerTurn) {
                state = RingRunMode.advanceTurn(state).state
                lastTarget = null
                lastPoints = null
                lastHitLabel = null
                recentHits.clear()
                flashTick++
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick, lastPoints)
    GameScreenFrame(
        game = GameType.RING_RUN,
        title = "RING RUN",
        accent = DartsPalette.Yellow,
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
        modeLabel = "ROUND ${state.roundNumber}/4",
        finished = state.finished,
        exitMessage = "Return to setup? The current Ring Run game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = {
            RingRunScoreboard(state)
            RingRunRoundPanel(
                state = state,
                lastPoints = lastPoints,
                lastHitLabel = lastHitLabel,
            )
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null,
                playScale = RING_RUN_PLAY_SCALE,
                accent = DartsPalette.Yellow,
                onHit = { if (autodarts == null) throwDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = ringRunBoardOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Yellow,
                primaryColor = DartsPalette.Yellow,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) endTurn() else throwDart(DartHit(null, 0, Ring.MISS))
                },
                onMenu = onMenu,
                onReset = autodarts?.takeIf { it.mode == ScoringMode.AUTODARTS }?.let { cfg -> { scope.launch { withContext(Dispatchers.IO) { AutodartsClient(cfg.url).reset() } }; Unit } },
            )
        },
        completeOverlay = {
            RingRunCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun RingRunScoreboard(state: RingRunState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val score = state.scores[player.id] ?: 0
            val color = playerColor(player.colorIndex)
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
                    text = score.toString(),
                    color = if (active) DartsPalette.Text else DartsPalette.Muted,
                    fontSize = 21.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "PTS",
                    color = DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
        }
    }
}

@Composable
private fun RingRunRoundPanel(
    state: RingRunState,
    lastPoints: Int?,
    lastHitLabel: String?,
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
                    text = "ROUND ${state.roundNumber} · ${state.dartsLeft} DARTS LEFT",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "SCORES",
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = "+${state.round.points}",
                    color = DartsPalette.Yellow,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.round.title,
                    color = DartsPalette.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = state.round.description,
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }
            if (lastHitLabel != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = lastHitLabel,
                        color = DartsPalette.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        text = if ((lastPoints ?: 0) > 0) "+$lastPoints PTS" else "NO SCORE",
                        color = if ((lastPoints ?: 0) > 0) DartsPalette.Yellow else DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RingRunMode.ROUNDS.forEachIndexed { index, _ ->
                val done = index < state.roundIndex
                val current = index == state.roundIndex && !state.finished
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            when {
                                done -> DartsPalette.Yellow.copy(alpha = 0.8f)
                                current -> DartsPalette.Yellow
                                else -> DartsPalette.Border
                            },
                        ),
                )
            }
        }
    }
}

private fun precisionHitLabel(hit: DartHit): String {
    val number = hit.number ?: return "MISS"
    if (number == BoardGeometry.BULL) return "BULL"
    return when (hit.ring) {
        Ring.DOUBLE -> "D$number"
        Ring.TREBLE -> "T$number"
        Ring.SINGLE -> when (hit.singleBand) {
            SingleBand.LARGE -> "S$number LARGE"
            SingleBand.SMALL -> "S$number SMALL"
            null -> "S$number"
        }
        else -> "S$number"
    }
}
