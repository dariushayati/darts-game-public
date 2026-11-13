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
import com.darts.core.MatchRecord
import com.darts.core.DartHit
import com.darts.core.Player
import com.darts.core.Ring
import com.darts.core.WhackAMoleEvent
import com.darts.core.WhackAMoleMode
import com.darts.core.WhackAMoleRules
import com.darts.core.WhackAMoleState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WhackAMoleGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    rules: WhackAMoleRules,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(WhackAMoleMode.newGame(players, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { whackAMoleMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<WhackAMoleState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var whackTick by remember { mutableIntStateOf(0) }
    var lastWhacked by remember { mutableStateOf<Boolean?>(null) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    val moleOverlay = rememberWhackAMoleBoardOverlay(
        target = state.target,
        whackTick = whackTick,
    )

    fun applyDart(hit: DartHit) {
        val previousPlayerIndex = state.currentPlayerIndex
        undoStack.add(state)
        val reduction = WhackAMoleMode.apply(state, hit)
        state = reduction.state
        val whacked = reduction.events.any { it is WhackAMoleEvent.MoleWhacked }
        val visitAdvanced = state.currentPlayerIndex != previousPlayerIndex || state.finished

        flashTick++
        if (whacked) whackTick++
        lastWhacked = whacked
        lastTarget = if (whacked) hit.number else null

        if (visitAdvanced) {
            recentHits.clear()
        } else if (!hit.isMiss && hit.number != null) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }

        if (soundEnabled) {
            sounds.play(if (whacked) SoundCue.LOCK else SoundCue.REMOVE)
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            lastWhacked = null
            recentHits.clear()
            flashTick++
            whackTick++
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = WhackAMoleMode.advanceTurn(state).state
        lastTarget = null
        lastWhacked = null
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = WhackAMoleMode.newGame(players, rules)
        undoStack.clear()
        lastTarget = null
        lastWhacked = null
        recentHits.clear()
        flashTick++
        whackTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { applyDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != state.rules.dartsPerTurn) {
                state = WhackAMoleMode.advanceTurn(state).state
                lastTarget = null
                lastWhacked = null
                recentHits.clear()
                flashTick++
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick, whackTick)
    GameScreenFrame(
        game = GameType.WHACK_A_MOLE,
        title = "WHACK A MOLE",
        accent = DartsPalette.Mole,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintWhack,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = "${state.moles} MOLES",
        finished = state.finished,
        exitMessage = "Return to setup? The current Whack a Mole game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = { twoPane ->
            WhackAMoleScoreboard(state)
            WhackAMoleTargetPanel(
                state = state,
                lastWhacked = lastWhacked,
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
                playScale = WHACK_A_MOLE_PLAY_SCALE,
                accent = DartsPalette.Mole,
                onHit = { if (autodarts == null) applyDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = moleOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Mole,
                primaryColor = DartsPalette.Mole,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) endTurn() else applyDart(DartHit(null, 0, Ring.MISS))
                },
                onMenu = onMenu,
                onReset = autodarts?.takeIf { it.mode == ScoringMode.AUTODARTS }?.let { cfg -> { scope.launch { withContext(Dispatchers.IO) { AutodartsClient(cfg.url).reset() } }; Unit } },
            )
        },
        completeOverlay = {
            WhackAMoleCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun WhackAMoleScoreboard(state: WhackAMoleState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val whacked = state.whacked(player.id)
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
                    text = "$whacked",
                    color = if (active) DartsPalette.Text else DartsPalette.Muted,
                    fontSize = 21.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "OF ${state.moles}",
                    color = DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
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
                            .fillMaxWidth(if (state.moles == 0) 0f else whacked / state.moles.toFloat())
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (whacked >= state.moles) color else color.copy(alpha = 0.8f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WhackAMoleTargetPanel(
    state: WhackAMoleState,
    lastWhacked: Boolean?,
    allPlayers: Boolean = false,
) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    val target = state.target
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
                    text = "${state.dartsLeft} DARTS LEFT · ${state.left(player.id)} TO GO",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "WHACK",
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                Text(
                    text = target?.number?.toString() ?: "DONE",
                    color = DartsPalette.Mole,
                    fontSize = 30.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = when (target?.ring) {
                        Ring.DOUBLE -> "DOUBLE"
                        Ring.TREBLE -> "TREBLE"
                        else -> if (target != null) "ANY RING" else "  "
                    },
                    color = when (target?.ring) {
                        Ring.DOUBLE, Ring.TREBLE -> DartsPalette.Red
                        else -> DartsPalette.Muted
                    },
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }

        // Always reserve the status line so it appearing does not change the panel
        // height and shift the board below it.
        Text(
            text = when (lastWhacked) {
                true -> "WHACKED!"
                false -> "MISSED"
                null -> ""
            },
            color = when (lastWhacked) {
                true -> DartsPalette.Green
                false -> DartsPalette.Muted
                null -> Color.Transparent
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        val done = state.whacked(player.id)
        if (allPlayers) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.players.forEach { entry ->
                    val pColor = playerColor(entry.colorIndex)
                    val pDone = state.whacked(entry.id)
                    val isActive = entry.id == player.id && !state.finished
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.width(56.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(pColor, CircleShape),
                            )
                            Text(
                                text = entry.name.replace("Player ", "P"),
                                color = if (isActive) DartsPalette.Text else DartsPalette.Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            state.targets.forEachIndexed { index, mole ->
                                val isSpecial = mole.ring != null
                                val isDone = index < pDone
                                val isNext = index == pDone && !state.finished
                                val highlight = isNext && isActive
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            when {
                                                isDone -> pColor.copy(alpha = 0.85f)
                                                highlight -> DartsPalette.Mole
                                                else -> DartsPalette.Surface
                                            },
                                        )
                                        .border(
                                            if (isNext) 1.5.dp else 1.dp,
                                            when {
                                                highlight -> DartsPalette.Mole
                                                isNext -> pColor
                                                isSpecial -> DartsPalette.Red.copy(alpha = 0.8f)
                                                else -> DartsPalette.Border
                                            },
                                            RoundedCornerShape(5.dp),
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = mole.number.toString(),
                                        color = when {
                                            isDone || highlight -> Color.White
                                            isSpecial -> DartsPalette.Red
                                            else -> DartsPalette.Muted
                                        },
                                        fontSize = 8.sp,
                                        lineHeight = 8.sp,
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
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                state.targets.forEachIndexed { index, mole ->
                    val isSpecial = mole.ring != null
                    val isDone = index < done
                    val isCurrent = index == done && !state.finished
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isDone -> color.copy(alpha = 0.28f)
                                    isCurrent -> DartsPalette.Mole
                                    else -> DartsPalette.Surface
                                },
                            )
                            .border(
                                1.dp,
                                when {
                                    isCurrent -> DartsPalette.Mole
                                    isDone -> color.copy(alpha = 0.7f)
                                    isSpecial -> DartsPalette.Red.copy(alpha = 0.8f)
                                    else -> DartsPalette.Border
                                },
                                RoundedCornerShape(6.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mole.number.toString(),
                            color = when {
                                isCurrent -> DartsPalette.onColor(color)
                                isDone -> DartsPalette.Text
                                isSpecial -> DartsPalette.Red
                                else -> DartsPalette.Muted
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}
