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
import com.darts.core.DartHit
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.Ring
import com.darts.core.X01Event
import com.darts.core.X01InRule
import com.darts.core.X01Mode
import com.darts.core.X01Rules
import com.darts.core.X01State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun X01GameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    rules: X01Rules,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(X01Mode.newGame(players, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { x01MatchRecord(state) }
    val undoStack = remember { mutableStateListOf<X01State>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<X01Feedback?>(null) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    var ignoreAutodartsVisit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    fun applyDart(hit: DartHit) {
        if (state.finished || ignoreAutodartsVisit) return
        val previousPlayerIndex = state.currentPlayerIndex
        undoStack.add(state)
        val reduction = X01Mode.apply(state, hit)
        state = reduction.state
        val turnEndsImmediately = reduction.events.any {
            it is X01Event.Bust || it is X01Event.Checkout
        }
        val turnChanged = reduction.state.currentPlayerIndex != previousPlayerIndex
        flashTick++
        lastTarget = hit.number
        feedback = x01Feedback(reduction.events, state.rules.inRule)

        if (turnEndsImmediately || turnChanged) {
            recentHits.clear()
            if (turnEndsImmediately && autodarts != null) ignoreAutodartsVisit = true
        } else if (!hit.isMiss && hit.number != null) {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }
        if (turnEndsImmediately && autodarts?.mode == ScoringMode.CAMERA) {
            // The game will not advance a bust/checkout by itself; ask the camera to
            // wait for the darts to be removed, then tell us when the board is clear.
            CameraScoringHost.controller?.endTurnEarly()
        }

        if (soundEnabled) {
            x01SoundCue(reduction.events)?.let { sounds.play(it) }
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
            ignoreAutodartsVisit = false
            if (autodarts?.mode == ScoringMode.CAMERA) {
                CameraScoringHost.controller?.recalibrate()
            }
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) {
            CameraScoringHost.controller?.waitForTurnEnd()
        }
        state = X01Mode.advanceTurn(state).state
        lastTarget = null
        feedback = null
        recentHits.clear()
        flashTick++
        ignoreAutodartsVisit = false
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = X01Mode.newGame(players, rules)
        undoStack.clear()
        lastTarget = null
        feedback = null
        recentHits.clear()
        flashTick++
        ignoreAutodartsVisit = false
        if (autodarts?.mode == ScoringMode.CAMERA) {
            CameraScoringHost.controller?.recalibrate()
        }
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { if (!ignoreAutodartsVisit) applyDart(it) },
        onVisitEnded = {
            if (autodarts?.mode == ScoringMode.CAMERA) {
                // Only an early turn end (bust/checkout) reaches here; normal turns are
                // already advanced by X01Mode when the third dart lands.
                if (ignoreAutodartsVisit) {
                    ignoreAutodartsVisit = false
                    lastTarget = null
                    feedback = null
                    recentHits.clear()
                    flashTick++
                    if (!state.finished && state.dartsLeft != X01Mode.DARTS_PER_TURN) {
                        state = X01Mode.advanceTurn(state).state
                    }
                }
            } else if (ignoreAutodartsVisit) {
                ignoreAutodartsVisit = false
            } else if (!state.finished && state.dartsLeft != X01Mode.DARTS_PER_TURN) {
                state = X01Mode.advanceTurn(state).state
                lastTarget = null
                feedback = null
                recentHits.clear()
                flashTick++
            }
        },
        turnKey = "${state.currentPlayerIndex}:${state.turnNumber}:${state.legNumber}:${state.finished}",
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick)
    GameScreenFrame(
        game = GameType.X01,
        title = "X01",
        accent = DartsPalette.Blue,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintNavy,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = "${state.rules.startingScore} · ${state.rules.outRule.label} OUT",
        finished = state.finished,
        exitMessage = "Return to setup? The current X01 game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        panel = {
            X01Scoreboard(state)
            X01TurnPanel(state, feedback)
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null && !state.finished,
                accent = DartsPalette.Blue,
                onHit = { if (autodarts == null) applyDart(it) },
                modifier = Modifier.fillMaxSize(),
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Blue,
                primaryColor = DartsPalette.Blue,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) {
                        // The turn already ended on a bust/checkout; wait for the board
                        // to clear instead of advancing a second time.
                        if (!ignoreAutodartsVisit) endTurn()
                    } else {
                        applyDart(DartHit(null, 0, Ring.MISS))
                    }
                },
                onMenu = onMenu,
                onReset = autodarts
                    ?.takeIf { it.mode == ScoringMode.AUTODARTS }
                    ?.let { cfg ->
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
            X01CompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

private data class X01Feedback(val text: String, val color: Color)

private fun x01Feedback(events: List<X01Event>, inRule: X01InRule): X01Feedback? {
    events.filterIsInstance<X01Event.MatchFinished>().firstOrNull()?.let {
        return X01Feedback("MATCH WON", DartsPalette.GreenText)
    }
    events.filterIsInstance<X01Event.LegWon>().firstOrNull()?.let {
        return X01Feedback("LEG WON", DartsPalette.GreenText)
    }
    events.filterIsInstance<X01Event.Checkout>().firstOrNull()?.let {
        return X01Feedback("CHECKOUT", DartsPalette.GreenText)
    }
    events.filterIsInstance<X01Event.Bust>().firstOrNull()?.let {
        return X01Feedback("BUST", DartsPalette.Red)
    }
    events.filterIsInstance<X01Event.NoDoubleIn>().firstOrNull()?.let {
        return X01Feedback(
            if (inRule == X01InRule.MASTER) "MASTER IN" else "DOUBLE IN",
            DartsPalette.Text,
        )
    }
    events.filterIsInstance<X01Event.DartScored>().lastOrNull()?.let {
        return X01Feedback("-${it.before - it.after}", DartsPalette.Blue)
    }
    events.filterIsInstance<X01Event.DartMissed>().firstOrNull()?.let {
        return X01Feedback("MISS", DartsPalette.Muted)
    }
    return null
}

private fun x01SoundCue(events: List<X01Event>): SoundCue? = when {
    events.any {
        it is X01Event.Checkout || it is X01Event.LegWon || it is X01Event.MatchFinished
    } -> SoundCue.LOCK
    events.any {
        it is X01Event.Bust || it is X01Event.NoDoubleIn || it is X01Event.DartMissed
    } -> SoundCue.MISS
    events.any { it is X01Event.DartScored } -> SoundCue.ADD
    else -> null
}

@Composable
private fun X01Scoreboard(state: X01State) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val color = playerColor(player.colorIndex)
            val opened = state.isOpened(player.id)
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
                    .padding(vertical = 7.dp, horizontal = 5.dp),
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
                    text = "${state.scoreFor(player.id)}",
                    color = if (active) DartsPalette.Text else DartsPalette.Muted,
                    fontSize = 21.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "${state.legsWon(player.id)} LEG${if (state.legsWon(player.id) == 1) "" else "S"}",
                    color = DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = when {
                        opened -> "IN"
                        state.rules.inRule == X01InRule.STRAIGHT -> "IN"
                        else -> "NOT IN"
                    },
                    color = if (opened) DartsPalette.GreenText else DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp,
                )
            }
        }
    }
}

@Composable
private fun X01TurnPanel(state: X01State, feedback: X01Feedback?) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    val opened = state.isOpened(player.id)
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
                    text = "${state.dartsLeft} DARTS LEFT · LEG ${state.legNumber}",
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(DartsPalette.Surface, RoundedCornerShape(16.dp))
                .border(1.dp, DartsPalette.Border, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "REMAINING",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    text = "${state.scoreFor(player.id)}",
                    color = DartsPalette.Text,
                    fontSize = 54.sp,
                    lineHeight = 54.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${state.legsWon(player.id)}",
                    color = color,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "LEGS",
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Text(
                    text = if (opened) "IN" else "NOT IN",
                    color = if (opened) DartsPalette.GreenText else DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }

        Text(
            text = when {
                !opened && state.rules.inRule == X01InRule.DOUBLE ->
                    "OPEN WITH A DOUBLE. ${state.rules.outRule.description}"
                !opened && state.rules.inRule == X01InRule.MASTER ->
                    "OPEN WITH A DOUBLE OR TREBLE. ${state.rules.outRule.description}"
                else -> state.rules.outRule.description
            },
            color = DartsPalette.Muted,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 7.dp, start = 2.dp),
        )
    }
}
