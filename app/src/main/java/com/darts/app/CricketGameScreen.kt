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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.BoardGeometry
import com.darts.core.CricketEvent
import com.darts.core.CricketMode
import com.darts.core.CricketState
import com.darts.core.DartHit
import com.darts.core.MatchRecord
import com.darts.core.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CricketGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(CricketMode.newGame(players)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { cricketMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<CricketState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var hitTick by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<CricketFeedback?>(null) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    val boardOverlay = rememberCricketBoardOverlay(
        players = state.players,
        marks = state.marks,
        lastTarget = lastTarget,
        hitTick = hitTick,
    )

    fun applyDart(hit: DartHit) {
        val previousPlayerIndex = state.currentPlayerIndex
        undoStack.add(state)
        val reduction = CricketMode.apply(state, hit)
        state = reduction.state
        val visitAdvanced = state.currentPlayerIndex != previousPlayerIndex || state.finished

        flashTick++
        hitTick++
        lastTarget = hit.number?.takeIf { it in CricketMode.TARGETS }
        feedback = feedbackFor(hit, reduction.events)

        if (visitAdvanced) {
            recentHits.clear()
        } else if (!hit.isMiss && hit.number != null) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }

        if (soundEnabled) {
            cricketSoundCue(reduction.events)?.let { sounds.play(it) }
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            feedback = null
            recentHits.clear()
            flashTick++
            hitTick++
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = CricketMode.advanceTurn(state).state
        lastTarget = null
        feedback = null
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = CricketMode.newGame(players)
        undoStack.clear()
        lastTarget = null
        feedback = null
        recentHits.clear()
        flashTick++
        hitTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { applyDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != state.rules.dartsPerTurn) {
                state = CricketMode.advanceTurn(state).state
                lastTarget = null
                feedback = null
                recentHits.clear()
                flashTick++
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick)
    GameScreenFrame(
        game = GameType.CRICKET,
        title = "CRICKET",
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
        modeLabel = "20–15 + BULL",
        finished = state.finished,
        exitMessage = "Return to setup? The current Cricket game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = { twoPane ->
            CricketScoreboard(state)
            CricketBoardPanel(
                state = state,
                feedback = feedback,
                allPlayers = twoPane,
            )
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null,
                playScale = CRICKET_PLAY_SCALE,
                accent = DartsPalette.DartCyan,
                onHit = { if (autodarts == null) applyDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = boardOverlay,
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
                    if (autodarts != null) endTurn() else applyDart(DartHit(null, 0, com.darts.core.Ring.MISS))
                },
                onMenu = onMenu,
                onReset = autodarts?.takeIf { it.mode == ScoringMode.AUTODARTS }?.let { cfg -> { scope.launch { withContext(Dispatchers.IO) { AutodartsClient(cfg.url).reset() } }; Unit } },
            )
        },
        completeOverlay = {
            CricketCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

private data class CricketFeedback(val text: String, val color: Color)

private fun feedbackFor(hit: DartHit, events: List<CricketEvent>): CricketFeedback? {
    val closed = events.filterIsInstance<CricketEvent.TargetClosed>().firstOrNull()
    if (closed != null) {
        return CricketFeedback("CLOSED ${cricketTargetLabel(closed.target)}", DartsPalette.Green)
    }
    val scored = events.filterIsInstance<CricketEvent.PointsScored>().firstOrNull()
    if (scored != null) {
        return CricketFeedback("+${scored.points} PTS", DartsPalette.DartCyan)
    }
    val marks = events.filterIsInstance<CricketEvent.MarksAdded>().firstOrNull()
    if (marks != null) {
        return CricketFeedback(
            "${if (marks.marks == 1) "MARK" else "${marks.marks} MARKS"} · ${marks.total}/3",
            DartsPalette.Text,
        )
    }
    return if (hit.isMiss) CricketFeedback("MISS", DartsPalette.Muted) else null
}

internal fun cricketTargetLabel(target: Int): String =
    if (target == BoardGeometry.BULL) "BULL" else target.toString()

@Composable
private fun CricketScoreboard(state: CricketState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val color = playerColor(player.colorIndex)
            val closed = state.closedCount(player.id)
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
                    text = "$closed/7 CLOSED",
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
                            .fillMaxWidth(closed / 7f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (closed >= 7) color else color.copy(alpha = 0.8f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun CricketBoardPanel(
    state: CricketState,
    feedback: CricketFeedback?,
    allPlayers: Boolean = false,
) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp),
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
                    text = "${state.dartsLeft} DARTS LEFT · ${state.pointsFor(player.id)} PTS · ${state.openTargets(player.id).size} OPEN",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            Text(
                text = feedback?.text ?: "",
                color = feedback?.color ?: Color.Transparent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
            )
        }

        val visiblePlayers = if (allPlayers) state.players else listOf(player)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(30.dp))
                visiblePlayers.forEach { entry ->
                    Text(
                        text = entry.name.replace("Player ", "P").take(5).uppercase(),
                        color = playerColor(entry.colorIndex),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            CricketMode.TARGETS.forEach { target ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = cricketTargetLabel(target),
                        color = DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.width(30.dp),
                    )
                    visiblePlayers.forEach { entry ->
                        val marks = state.marks(entry.id, target)
                        val closed = marks >= CricketMode.MARKS_TO_CLOSE
                        val isCurrent = entry.id == player.id
                        val cellColor = playerColor(entry.colorIndex)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 0.5.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    when {
                                        closed -> cellColor.copy(alpha = 0.22f)
                                        marks > 0 -> DartsPalette.SurfaceRaised
                                        else -> DartsPalette.Surface
                                    },
                                )
                                .border(
                                    width = if (isCurrent) 1.2.dp else 0.8.dp,
                                    color = when {
                                        closed -> cellColor.copy(alpha = 0.9f)
                                        isCurrent -> DartsPalette.DartCyan.copy(alpha = 0.65f)
                                        else -> DartsPalette.Border
                                    },
                                    shape = RoundedCornerShape(5.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = markSymbol(marks),
                                color = when {
                                    closed -> cellColor
                                    marks > 0 -> DartsPalette.Text
                                    else -> DartsPalette.Muted.copy(alpha = 0.5f)
                                },
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.Black,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun markSymbol(marks: Int): String = when {
    marks <= 0 -> "·"
    marks == 1 -> "/"
    marks == 2 -> "//"
    else -> "✕"
}
