package com.darts.app

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AlienKill
import com.darts.core.AutodartsBoardStatus
import com.darts.core.MatchRecord
import com.darts.core.DartHit
import com.darts.core.InvadersEvent
import com.darts.core.InvadersMode
import com.darts.core.InvadersRules
import com.darts.core.InvadersState
import com.darts.core.Player
import com.darts.core.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun InvadersGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    rules: InvadersRules,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(InvadersMode.newGame(players, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { invadersMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<InvadersState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var lastKills by remember { mutableStateOf<List<AlienKill>>(emptyList()) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)

    fun throwDart(hit: DartHit) {
        if (state.finished) return
        undoStack.add(state)
        val reduction = InvadersMode.apply(state, hit)
        state = reduction.state
        lastTarget = hit.number?.takeIf { it in InvadersState.NUMBERS }
        lastKills = reduction.state.lastKills
        flashTick++
        lastTarget?.let {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }
        if (soundEnabled && reduction.state.lastKills.isNotEmpty()) {
            val cue = if (reduction.events.any { it is InvadersEvent.NumberCleared }) {
                SoundCue.LOCK
            } else {
                SoundCue.ADD
            }
            sounds.play(cue)
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            lastKills = emptyList()
            recentHits.clear()
            flashTick++
        }
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        state = InvadersMode.advanceTurn(state).state
        lastTarget = null
        lastKills = emptyList()
        recentHits.clear()
        flashTick++
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = InvadersMode.newGame(players, rules)
        undoStack.clear()
        lastTarget = null
        lastKills = emptyList()
        recentHits.clear()
        flashTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { throwDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != state.rules.dartsPerTurn) {
                state = InvadersMode.advanceTurn(state).state
                lastTarget = null
                lastKills = emptyList()
                flashTick++
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick, lastKills)
    GameScreenFrame(
        game = GameType.INVADERS,
        title = "INVADERS",
        accent = DartsPalette.Violet,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.Background,
                DartsPalette.TintInvaders,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = "TURN ${state.turnNumber}",
        finished = state.finished,
        exitMessage = "Return to setup? The current Invaders game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        decorations = { InvadersStarfield(Modifier.fillMaxSize()) },
        panel = {
            InvadersScoreboard(state)
            InvadersCurrentPlayerPanel(state)
        },
        board = {
            Box(Modifier.fillMaxSize()) {
                BoardCanvas(
                    state = displayState,
                    lastTarget = lastTarget,
                    flashTick = flashTick,
                    recentTargets = recentHits,
                    enabled = autodarts == null,
                    playScale = INVADERS_PLAY_SCALE,
                    onHit = { if (autodarts == null) throwDart(it) },
                    modifier = Modifier.fillMaxSize(),
                )
                InvadersAliensOverlay(
                    aliens = state.aliens,
                    lastKills = lastKills,
                    killTick = flashTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Violet,
                primaryColor = DartsPalette.Violet,
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
            InvadersCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun InvadersScoreboard(state: InvadersState) {
    val maxScore = state.players.maxOf { state.scores[it.id] ?: 0 }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val score = state.scores[player.id] ?: 0
            val leader = maxScore > 0 && score == maxScore
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
                    .padding(vertical = 6.dp, horizontal = 6.dp),
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
                    color = if (leader) DartsPalette.Green else DartsPalette.Text,
                    fontSize = 24.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = "PTS",
                    color = DartsPalette.Muted,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                )
            }
        }
    }
}

@Composable
private fun InvadersCurrentPlayerPanel(state: InvadersState) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    val used = state.dartsByPlayer[player.id] ?: 0
    val score = state.scores[player.id] ?: 0
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
                    text = "TURN ${state.turnNumber} · ${state.dartsLeft} DARTS LEFT",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "DARTS $used/${state.rules.dartsPerPlayer}",
                    color = DartsPalette.Muted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.aliensLeft} ALIENS",
                    color = DartsPalette.Violet,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = score.toString(),
                color = DartsPalette.Text,
                fontSize = 60.sp,
                lineHeight = 60.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "PTS",
                color = DartsPalette.Muted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(bottom = 5.dp), horizontalAlignment = Alignment.End) {
                LegendDot(DartsPalette.Green, "1 PT")
                LegendDot(DartsPalette.BlueBright, "3 PTS")
                LegendDot(DartsPalette.Amber, "5 PTS")
            }
        }
        val progress = (1f - state.aliensLeft / InvadersState.ALL_ALIENS.toFloat()).coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(DartsPalette.Border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(DartsPalette.Green),
            )
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(
            text = label,
            color = DartsPalette.Muted,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun InvadersStarfield(modifier: Modifier = Modifier) {
    val stars = remember {
        val random = Random(23)
        List(44) {
            Star(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.7f + random.nextFloat() * 1.6f,
                alpha = 0.10f + random.nextFloat() * 0.32f,
                color = when (random.nextInt(4)) {
                    0 -> Color.White
                    1 -> DartsPalette.Violet
                    2 -> DartsPalette.Green
                    else -> DartsPalette.BlueBright
                },
            )
        }
    }
    Canvas(modifier) {
        val unit = 1.dp.toPx()
        stars.forEach { star ->
            drawCircle(
                color = star.color.copy(alpha = star.alpha),
                radius = star.radius * unit,
                center = Offset(star.x * size.width, star.y * size.height),
            )
        }
    }
}

private data class Star(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
)
