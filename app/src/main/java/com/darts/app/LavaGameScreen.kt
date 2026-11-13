package com.darts.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import com.darts.core.AutodartsBoardStatus
import com.darts.core.MatchRecord
import com.darts.core.DartHit
import com.darts.core.LavaEvent
import com.darts.core.LavaMode
import com.darts.core.LavaRules
import com.darts.core.LavaState
import com.darts.core.Player
import com.darts.core.Ring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@Composable
fun LavaGameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig?,
    rules: LavaRules,
    soundEnabled: Boolean,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(LavaMode.newGame(players, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { lavaMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<LavaState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    var burnTick by remember { mutableIntStateOf(0) }
    var lastBurned by remember { mutableStateOf<List<Int>>(emptyList()) }
    val recentHits = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    val displayState = rememberNeutralBoardState(players)
    val lavaBoardOverlay = rememberLavaBoardOverlay(
        lavaNumbers = state.lavaNumbers,
        lastBurned = lastBurned,
        burnTick = burnTick,
    )

    fun playDamageSound(events: List<LavaEvent>) {
        if (!soundEnabled) return
        when {
            events.any { it is LavaEvent.LifeLost } -> sounds.play(SoundCue.LIFE_LOST)
            events.any { it is LavaEvent.LavaBurned } -> sounds.play(SoundCue.REMOVE)
        }
    }

    fun playSuccessSound(hit: DartHit, events: List<LavaEvent>) {
        if (!soundEnabled) return
        val lostLife = events.any { it is LavaEvent.LifeLost }
        val safeHit = !hit.isMiss && hit.number != null && !lostLife
        if (safeHit) sounds.play(SoundCue.ADD)
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        val reduction = LavaMode.advanceTurn(state)
        state = reduction.state
        lastTarget = null
        lastBurned = reduction.state.lastBurned
        recentHits.clear()
        flashTick++
        if (lastBurned.isNotEmpty()) burnTick++
        playDamageSound(reduction.events)
    }

    fun throwDart(hit: DartHit) {
        if (state.finished) return
        undoStack.add(state)
        val reduction = LavaMode.apply(state, hit)
        state = reduction.state
        lastTarget = hit.number?.takeIf { it in LavaState.NUMBERS }
        lastBurned = reduction.state.lastBurned
        flashTick++
        if (lastBurned.isNotEmpty()) burnTick++
        lastTarget?.let {
            recentHits.add(hit)
            while (recentHits.size > 3) recentHits.removeAt(0)
        }
        playSuccessSound(hit, reduction.events)
        playDamageSound(reduction.events)
        if (!state.finished &&
            reduction.events.any { it is LavaEvent.PlayerOut } &&
            autodarts?.mode == ScoringMode.CAMERA
        ) {
            CameraScoringHost.controller?.waitForTurnEnd()
        }
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            lastTarget = null
            lastBurned = emptyList()
            recentHits.clear()
            flashTick++
            burnTick++
        }
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = LavaMode.newGame(players, rules)
        undoStack.clear()
        lastTarget = null
        lastBurned = emptyList()
        recentHits.clear()
        flashTick++
        burnTick++
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { throwDart(it) },
        onVisitEnded = {
            if (!state.finished && state.dartsLeft != state.rules.dartsPerTurn) {
                val reduction = LavaMode.advanceTurn(state)
                state = reduction.state
                lastTarget = null
                lastBurned = reduction.state.lastBurned
                flashTick++
                if (lastBurned.isNotEmpty()) burnTick++
                playDamageSound(reduction.events)
            }
        },
    )

    val mirrorView = state.toMirrorView(lastTarget, recentHits.toList(), flashTick, lastBurned, burnTick)
    GameScreenFrame(
        game = GameType.LAVA,
        title = "BOARD IS LAVA",
        accent = DartsPalette.Lava,
        background = Brush.verticalGradient(
            listOf(
                DartsPalette.BackgroundDeep,
                DartsPalette.TintLava,
                DartsPalette.TintLavaDeep,
                DartsPalette.BackgroundDeep,
            ),
        ),
        autodarts = autodarts,
        boardStatus = boardStatus,
        modeLabel = "ROUND ${state.roundNumber}",
        finished = state.finished,
        exitMessage = "Return to setup? The current Lava game will be lost.",
        mirrorView = mirrorView,
        onExit = onExit,
        decorations = { LavaEmbers(Modifier.fillMaxSize()) },
        panel = {
            LavaScoreboard(state)
            LavaCurrentPlayerPanel(state)
        },
        board = {
            BoardCanvas(
                state = displayState,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = recentHits,
                enabled = autodarts == null,
                playScale = LAVA_PLAY_SCALE,
                accent = DartsPalette.Lava,
                onHit = { if (autodarts == null) throwDart(it) },
                modifier = Modifier.fillMaxSize(),
                boardOverlay = lavaBoardOverlay,
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.Lava,
                primaryColor = DartsPalette.Lava,
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
            LavaCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
            )
        },
    )
}

@Composable
private fun LavaScoreboard(state: LavaState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val lives = state.livesByPlayer[player.id] ?: 0
            val out = lives <= 0
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
                    Box(Modifier.size(7.dp).background(if (out) DartsPalette.Border else color, CircleShape))
                    Text(
                        text = player.name.replace("Player ", "P"),
                        color = if (out) DartsPalette.Border else if (active) DartsPalette.Text else DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 66.dp),
                    )
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 1.dp
                    val available = (maxWidth - gap * (state.rules.lives - 1))
                        .coerceAtLeast(1.dp)
                    val heartSize = (available / state.rules.lives)
                        .coerceAtMost(8.dp)
                        .coerceAtLeast(3.dp)
                    HeartsRow(
                        lives = lives,
                        max = state.rules.lives,
                        color = if (out) DartsPalette.Border else DartsPalette.Red,
                        heartSize = heartSize,
                        gap = gap,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                Text(
                    text = if (out) "OUT" else "LIVES",
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
private fun LavaCurrentPlayerPanel(state: LavaState) {
    val player = state.currentPlayer
    val color = playerColor(player.colorIndex)
    val lives = state.livesByPlayer[player.id] ?: 0
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
                    text = "${state.numbersLeft} NUMBERS LEFT",
                    color = DartsPalette.Text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.lavaNumbers.size}/20 LAVA",
                    color = DartsPalette.Lava,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        HeartsRow(
            lives = lives,
            max = state.rules.lives,
            color = DartsPalette.Red,
            heartSize = 26.dp,
            gap = 3.dp,
        )
        Text(
            text = "LIVES",
            color = DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
        )
        val progress = (state.lavaNumbers.size / 20f).coerceIn(0f, 1f)
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
                    .background(DartsPalette.Lava),
            )
        }
    }
}

@Composable
private fun LavaEmbers(modifier: Modifier = Modifier) {
    val embers = remember {
        val random = Random(31)
        List(46) {
            Ember(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = 0.6f + random.nextFloat() * 1.8f,
                alpha = 0.10f + random.nextFloat() * 0.40f,
                color = if (random.nextBoolean()) DartsPalette.Lava else DartsPalette.LavaBright,
            )
        }
    }
    Canvas(modifier) {
        val unit = 1.dp.toPx()
        embers.forEach { ember ->
            drawCircle(
                color = ember.color.copy(alpha = ember.alpha),
                radius = ember.radius * unit,
                center = Offset(ember.x * size.width, ember.y * size.height),
            )
        }
    }
}

private data class Ember(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val color: Color,
)
