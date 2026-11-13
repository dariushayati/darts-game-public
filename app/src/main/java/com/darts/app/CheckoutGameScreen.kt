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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsBoardStatus
import com.darts.core.CheckoutEvent
import com.darts.core.CheckoutMode
import com.darts.core.CheckoutRules
import com.darts.core.CheckoutState
import com.darts.core.DartHit
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CheckoutGameScreen(
    player: Player,
    autodarts: AutodartsConfig?,
    rules: CheckoutRules,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(CheckoutMode.newGame(player, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { checkoutMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<CheckoutState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var lastMatched by remember { mutableStateOf<Boolean?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(listOf(player))

    val targetOverlay = rememberCheckoutBoardOverlay(
        target = state.expectedDart,
        hitTick = flashTick,
    )

    fun applyDart(hit: DartHit) {
        if (state.finished) return
        undoStack.add(state)
        val reduction = CheckoutMode.apply(state, hit)
        state = reduction.state
        val thrown = reduction.events.filterIsInstance<CheckoutEvent.DartThrown>().firstOrNull()
        val completed = reduction.events.any { it is CheckoutEvent.CheckoutCompleted }

        lastMatched = thrown?.matched
        lastTarget = if (completed) null else hit.number
        flashTick++
        if (completed) {
            recentHits.clear()
        } else if (!hit.isMiss && hit.number != null) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }
        if (completed && !state.finished && autodarts?.mode == ScoringMode.CAMERA) {
            CameraScoringHost.controller?.waitForTurnEnd()
        }
        if (soundEnabled) {
            sounds.play(
                when {
                    completed -> SoundCue.LOCK
                    thrown?.matched == false -> SoundCue.REMOVE
                    else -> SoundCue.ADD
                },
            )
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            lastMatched = null
            recentHits.clear()
            flashTick++
        }
    }

    fun endAttempt() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = CheckoutMode.advanceTurn(state).state
        lastTarget = null
        lastMatched = null
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = CheckoutMode.newGame(player, rules)
        undoStack.clear()
        lastTarget = null
        lastMatched = null
        recentHits.clear()
        flashTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { applyDart(it) },
        // Checkout keeps its path progress across visits. Do not rewind state here;
        // a late visit-end callback must never overwrite a just-completed checkout.
        onVisitEnded = {},
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick)
    GameScreenFrame(
        game = GameType.CHECKOUT,
        title = "CHECKOUT",
        accent = DartsPalette.Amber,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintRobinHood,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = if (state.finished) {
            "COMPLETE"
        } else {
            "${state.rules.difficulty.name} · ${state.index + 1}/${state.total}"
        },
        finished = state.finished,
        exitMessage = "Return to training setup? The current checkout run will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = {
            CheckoutScoreboard(state)
            CheckoutPathPanel(state, lastMatched)
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null,
                playScale = CHECKOUT_PLAY_SCALE,
                accent = DartsPalette.Amber,
                onHit = { if (autodarts == null) applyDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = targetOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Amber,
                primaryColor = DartsPalette.Amber,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) {
                        endAttempt()
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
            CheckoutCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun CheckoutScoreboard(state: CheckoutState) {
    val player = state.currentPlayer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .width(34.dp)
                .clip(CircleShape)
                .background(playerColor(player.colorIndex)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = player.name.take(1).uppercase(),
                color = DartsPalette.NamePlate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.name.uppercase(),
                color = DartsPalette.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                maxLines = 1,
            )
            Text(
                text = "SOLO CHECKOUT PRACTICE",
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${state.completed}/${state.total}",
                color = DartsPalette.Amber,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "CHECKOUTS",
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${state.dartsThrown}",
                color = DartsPalette.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "DARTS",
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
private fun CheckoutPathPanel(
    state: CheckoutState,
    lastMatched: Boolean?,
) {
    val path = state.currentPath
    val step = state.step
    val finished = state.finished
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (finished) "SESSION COMPLETE" else "CHECKOUT ${state.index + 1} OF ${state.total}",
                color = DartsPalette.Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Box(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(
                        DartsPalette.Amber.copy(alpha = 0.16f),
                        RoundedCornerShape(50),
                    )
                    .border(
                        1.dp,
                        DartsPalette.Amber.copy(alpha = 0.6f),
                        RoundedCornerShape(50),
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(
                    text = state.rules.difficulty.name,
                    color = DartsPalette.Amber,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
        Text(
            text = if (finished) "ALL DONE" else "${state.score ?: 0}",
            color = DartsPalette.Amber,
            fontSize = 52.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-2).sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (finished) "${state.total} CHECKOUTS" else "TO CHECK OUT",
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )

        if (path != null && !finished) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                path.darts.forEachIndexed { index, dart ->
                    val done = index < step
                    val current = index == step
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    done -> DartsPalette.Green.copy(alpha = 0.16f)
                                    current -> DartsPalette.Amber.copy(alpha = 0.20f)
                                    else -> DartsPalette.ChipOff
                                },
                            )
                            .border(
                                1.5.dp,
                                when {
                                    done -> DartsPalette.Green.copy(alpha = 0.7f)
                                    current -> DartsPalette.Amber
                                    else -> DartsPalette.Border
                                },
                                RoundedCornerShape(10.dp),
                            )
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = dart.label,
                            color = when {
                                done -> DartsPalette.GreenText
                                current -> DartsPalette.Amber
                                else -> DartsPalette.Muted
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = if (done) "HIT" else if (current) "NEXT" else "THEN",
                            color = if (current) DartsPalette.Amber else DartsPalette.Muted,
                            fontSize = 7.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        )
                    }
                }
            }
            Text(
                text = when (lastMatched) {
                    true -> "HIT · KEEP GOING"
                    false -> "MISS · TARGET KEPT"
                    null -> "HIT THE PATH IN ORDER"
                },
                color = when (lastMatched) {
                    true -> DartsPalette.GreenText
                    false -> DartsPalette.Red
                    null -> DartsPalette.Muted
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = path.label,
                    color = DartsPalette.Text,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
                Box(Modifier.weight(1f))
                Text(
                    text = if (state.dartsLeft == 1) "1 DART LEFT" else "${state.dartsLeft} DARTS LEFT",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
            }
            Text(
                text = "Hit the path in order; the last dart must be a double or inner bull.",
                color = DartsPalette.Muted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
