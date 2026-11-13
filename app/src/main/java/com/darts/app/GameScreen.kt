package com.darts.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.AutodartsState
import com.darts.core.AutodartsBoardStatus
import com.darts.core.MatchRecord
import com.darts.core.MirrorUiState
import com.darts.core.lockedCount
import com.darts.core.ownedCount
import com.darts.core.BoardGeometry
import com.darts.core.DartHit
import com.darts.core.GameEvent
import com.darts.core.GameRules
import com.darts.core.GameState
import com.darts.core.Player
import com.darts.core.Ring
import com.darts.core.ShieldsMode
import com.darts.core.lockedCount
import com.darts.core.ownedCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GameScreen(
    players: List<Player>,
    autodarts: AutodartsConfig? = null,
    rules: GameRules = GameRules(),
    soundEnabled: Boolean = true,
    onMatchFinished: (MatchRecord) -> Unit,
    onExit: () -> Unit,
) {
    var state by remember { mutableStateOf(ShieldsMode.newGame(players, rules)) }

    RecordMatchWhenFinished(state.finished, onMatchFinished) { shieldsMatchRecord(state) }
    val undoStack = remember { mutableStateListOf<GameState>() }
    var lastTarget by remember { mutableStateOf<Int?>(null) }
    var flashTick by remember { mutableIntStateOf(0) }
    val visitHits = remember { mutableStateListOf<String>() }
    val visitTargets = remember { mutableStateListOf<DartHit>() }
    var boardStatus by remember { mutableStateOf(AutodartsBoardStatus.OFFLINE) }
    val scope = rememberCoroutineScope()
    val sounds = rememberSoundEffects()
    var crackTarget by remember { mutableStateOf<Int?>(null) }
    var crackColor by remember { mutableStateOf<Color?>(null) }
    var crackColorIndex by remember { mutableStateOf<Int?>(null) }
    var crackCount by remember { mutableIntStateOf(0) }
    var crackTick by remember { mutableIntStateOf(0) }


    fun endVisit() {
        if (visitHits.isEmpty()) return
        visitHits.clear()
        visitTargets.clear()
    }

    fun reset() {
        recalibrateCameraIfNeeded(autodarts)
        state = ShieldsMode.newGame(players, rules)
        undoStack.clear()
        visitHits.clear()
        visitTargets.clear()
        lastTarget = null
        crackTarget = null
        crackColorIndex = null
        flashTick++
    }

    fun record(hit: DartHit) {
        val number = hit.number
        if (hit.isMiss || number == null) {
            visitHits.add("MISS")
        } else {
            visitHits.add(hitLabel(hit))
            visitTargets.add(hit)
        }
    }

    fun throwDart(hit: DartHit) {
        if (state.finished) return
        val before = state
        undoStack.add(state)
        val reduction = ShieldsMode.apply(state, hit)
        state = reduction.state
        if (soundEnabled) {
            soundCueFor(reduction.events)?.let(sounds::play)
        }
        lastTarget = reduction.events.mapNotNull { eventTarget(it) }.lastOrNull()
        flashTick++
        record(hit)
        val crack = reduction.events.firstNotNullOfOrNull { event ->
            when (event) {
                is GameEvent.ShieldRemoved -> event.target to event.ownerId
                is GameEvent.SectorNeutralised -> event.target to event.previousOwnerId
                else -> null
            }
        }
        if (crack != null) {
            crackTarget = crack.first
            val crackOwner = players.firstOrNull { it.id == crack.second }
            crackColor = crackOwner?.let { playerColor(it.colorIndex) }
            crackColorIndex = crackOwner?.colorIndex
            crackCount = before.sector(crack.first).shields
            crackTick++
        }
        if (reduction.events.any { it is GameEvent.TurnChanged }) endVisit()
    }

    fun endTurn() {
        if (state.finished) return
        if (autodarts?.mode == ScoringMode.CAMERA) CameraScoringHost.controller?.waitForTurnEnd()
        endVisit()
        state = ShieldsMode.advanceTurn(state).state
        lastTarget = null
        flashTick++
    }

    fun undo() {
        recalibrateCameraIfNeeded(autodarts)
        if (undoStack.isNotEmpty()) {
            state = undoStack.removeAt(undoStack.size - 1)
            visitHits.clear()
            visitTargets.clear()
            lastTarget = null
            crackTarget = null
            crackColorIndex = null
            flashTick++
        }
    }

    LaunchedAutodartsEffect(
        config = autodarts,
        onBoardStatus = { boardStatus = it },
        onDart = { throwDart(it) },
        onVisitEnded = {
            endVisit()
            if (!state.finished && state.dartsLeft != GameState.DARTS_PER_TURN) {
                state = ShieldsMode.advanceTurn(state).state
                lastTarget = null
                flashTick++
            }
        },
    )

    val budget = state.rules.dartsPerPlayer
    val playerDarts = state.dartsByPlayer[state.currentPlayer.id] ?: 0
    val modeLabel = if (budget > 0) "$playerDarts/$budget DARTS" else "ENDLESS"
    val mirrorUi = MirrorUiState(
        lastTarget = lastTarget,
        flashTick = flashTick,
        visitHits = visitHits.toList(),
        visitTargets = visitTargets.toList(),
        crackTarget = crackTarget,
        crackColorIndex = crackColorIndex,
        crackCount = crackCount,
        crackTick = crackTick,
        boardStatus = boardStatus.name,
    )

    GameScreenFrame(
        game = GameType.SHIELDS,
        title = "SHIELDS",
        accent = DartsPalette.BlueBright,
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
        modeLabel = modeLabel,
        finished = state.finished,
        exitMessage = "Return to the menu? The current game will be lost.",
        mirrorView = state.toMirrorView(lastTarget, visitTargets.toList(), flashTick),
        mirrorState = state,
        mirrorUi = mirrorUi,
        onExit = onExit,
        panel = {
            Scoreboard(state)
            HeroPanel(state)
            VisitBar(state, mirrorUi.visitHits)
        },
        board = {
            BoardCanvas(
                state = state,
                lastTarget = lastTarget,
                flashTick = flashTick,
                recentTargets = visitTargets,
                crackTarget = crackTarget,
                crackColor = crackColor,
                crackCount = crackCount,
                crackTick = crackTick,
                enabled = autodarts == null,
                onHit = { if (autodarts == null) throwDart(it) },
                modifier = Modifier.fillMaxSize(),
            )
        },
        actionBar = { onMenu ->
            GameActionBar(
                player = state.currentPlayer,
                accent = DartsPalette.BlueBright,
                primaryColor = DartsPalette.Blue,
                autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                canUndo = autodarts == null && undoStack.isNotEmpty(),
                onUndo = { undo() },
                onPrimary = {
                    if (autodarts != null) endTurn() else throwDart(DartHit(null, 0, Ring.MISS))
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
            GameCompleteOverlay(
                state = state,
                onPlayAgain = { reset() },
                onExit = onExit,
                showPlayAgain = true,
                exitLabel = "BACK TO SETUP",
            )
        },
    )
}

@Composable
internal fun ShieldsGameContent(
    state: GameState,
    ui: MirrorUiState,
    autodarts: AutodartsConfig?,
    modeLabel: String,
    interactive: Boolean,
    canUndo: Boolean,
    mirror: Boolean = false,
    sharing: Boolean = false,
    onShare: (() -> Unit)? = null,
    onHit: (DartHit) -> Unit = {},
    onPrimary: () -> Unit = {},
    onUndo: () -> Unit = {},
    onMenu: () -> Unit = {},
    onReset: (() -> Unit)? = null,
    onPlayAgain: () -> Unit = {},
    onExit: () -> Unit = {},
) {
    val boardStatus = runCatching { AutodartsBoardStatus.valueOf(ui.boardStatus) }
        .getOrDefault(AutodartsBoardStatus.OFFLINE)
    val lastTarget = ui.lastTarget
    val flashTick = ui.flashTick
    val visitTargets = ui.visitTargets
    val crackColor = ui.crackColorIndex?.let { playerColor(it) }
    val boardEnabled = interactive && autodarts == null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DartsPalette.BackgroundDeep,
                        DartsPalette.Background,
                        DartsPalette.TintNavy,
                        DartsPalette.BackgroundDeep,
                    ),
                ),
            ),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabletLandscape = LayoutPolicy.useTwoPane(
                LocalConfiguration.current.smallestScreenWidthDp,
                maxWidth.value,
                maxHeight.value,
            )
            if (tabletLandscape) {
                val panelWidth = LayoutPolicy.panelWidthDp(maxWidth.value).dp
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        boardStatus = boardStatus,
                        autodarts = autodarts,
                        modeLabel = modeLabel,
                        mirror = mirror,
                        sharing = sharing,
                        onShare = onShare,
                    )
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(Modifier.width(panelWidth).fillMaxHeight()) {
                            Scoreboard(state)
                            HeroPanel(state)
                            VisitBar(state, ui.visitHits)
                            Spacer(Modifier.weight(1f))
                            if (interactive) {
                                GameActionBar(
                                    player = state.currentPlayer,
                                    accent = DartsPalette.BlueBright,
                                    primaryColor = DartsPalette.Blue,
                                    autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                                    canUndo = canUndo,
                                    onUndo = onUndo,
                                    onPrimary = onPrimary,
                                    onMenu = onMenu,
                                    onReset = onReset,
                                )
                            }
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BoardCanvas(
                                state = state,
                                lastTarget = lastTarget,
                                flashTick = flashTick,
                                recentTargets = visitTargets,
                                crackTarget = ui.crackTarget,
                                crackColor = crackColor,
                                crackCount = ui.crackCount,
                                crackTick = ui.crackTick,
                                enabled = boardEnabled,
                                onHit = onHit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopBar(
                        boardStatus = boardStatus,
                        autodarts = autodarts,
                        modeLabel = modeLabel,
                        mirror = mirror,
                        sharing = sharing,
                        onShare = onShare,
                    )
                    Scoreboard(state)
                    HeroPanel(state)
                    VisitBar(state, ui.visitHits)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BoardCanvas(
                            state = state,
                            lastTarget = lastTarget,
                            flashTick = flashTick,
                            recentTargets = visitTargets,
                            crackTarget = ui.crackTarget,
                            crackColor = crackColor,
                            crackCount = ui.crackCount,
                            crackTick = ui.crackTick,
                            enabled = boardEnabled,
                            onHit = onHit,
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                        )
                    }
                    if (interactive) {
                        GameActionBar(
                            player = state.currentPlayer,
                            accent = DartsPalette.BlueBright,
                            primaryColor = DartsPalette.Blue,
                            autodarts = autodarts != null,
                camera = autodarts?.mode == ScoringMode.CAMERA,
                            canUndo = canUndo,
                            onUndo = onUndo,
                            onPrimary = onPrimary,
                            onMenu = onMenu,
                            onReset = onReset,
                        )
                    }
                }
            }
        }
        if (state.finished) {
            GameCompleteOverlay(
                state = state,
                onPlayAgain = onPlayAgain,
                onExit = onExit,
                showPlayAgain = interactive,
                exitLabel = if (mirror) "LEAVE MIRROR" else "BACK TO SETUP",
            )
        }
    }
}

@Composable
private fun TopBar(
    boardStatus: AutodartsBoardStatus,
    autodarts: AutodartsConfig?,
    modeLabel: String,
    mirror: Boolean,
    sharing: Boolean,
    onShare: (() -> Unit)?,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp),
    ) {
        if (onShare != null) {
            val shareColor = if (sharing) DartsPalette.Green else DartsPalette.Muted
            val shareBackground = if (sharing) {
                DartsPalette.Green.copy(alpha = 0.16f)
            } else {
                DartsPalette.ChipOff
            }
            val shareBorder = if (sharing) {
                DartsPalette.Green.copy(alpha = 0.7f)
            } else {
                DartsPalette.Border
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(50))
                    .background(shareBackground)
                    .border(1.dp, shareBorder, RoundedCornerShape(50))
                    .clickable(onClick = onShare)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "SHARE",
                    color = shareColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .background(DartsPalette.Pill, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 7.dp),
        ) {
            Text(
                text = modeLabel,
                color = DartsPalette.Text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        if (mirror) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(DartsPalette.Green.copy(alpha = 0.14f), RoundedCornerShape(50))
                    .border(1.dp, DartsPalette.Green.copy(alpha = 0.7f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    text = "MIRROR",
                    color = DartsPalette.Green,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
        } else {
            Box(Modifier.align(Alignment.CenterEnd)) {
                AutodartsStatusIndicator(
                    autodarts = autodarts,
                    boardStatus = boardStatus,
                    accent = DartsPalette.Blue,
                )
            }
        }
    }
}

@Composable
private fun Scoreboard(state: GameState) {
    val maxScore = state.players.maxOf { state.ownedCount(it.id) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.players.forEach { player ->
            val active = player.id == state.currentPlayer.id
            val score = state.ownedCount(player.id)
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
private fun HeroPanel(state: GameState) {
    val current = state.currentPlayer
    val color = playerColor(current.colorIndex)
    val locked = state.lockedCount(current.id)
    val points = state.ownedCount(current.id)

    Box(modifier = Modifier.fillMaxWidth().height(202.dp)) {
        ShardBackdrop()
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(6.dp).background(DartsPalette.Muted, CircleShape))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, CircleShape)
                        .border(2.dp, DartsPalette.NamePlate, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = current.name.take(1).uppercase(),
                        color = DartsPalette.NamePlate,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Box(
                    modifier = Modifier
                        .background(DartsPalette.NamePlate, RoundedCornerShape(5.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = current.name.uppercase(),
                        color = DartsPalette.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 190.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = points.toString(),
                        color = DartsPalette.Text,
                        fontSize = 90.sp,
                        lineHeight = 90.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-4).sp,
                    )
                    Text(
                        text = "POINTS",
                        color = DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .background(DartsPalette.PanelDeep, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = locked.toString(),
                            color = DartsPalette.Text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Text(
                        text = "LOCKED",
                        color = DartsPalette.Muted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                }
            }
            Text(
                text = "LOCKED $locked · ${state.dartsLeft} DARTS",
                color = DartsPalette.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

@Composable
internal fun ShardBackdrop() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(DartsPalette.ShardTop, DartsPalette.ShardBottom)))
        val shard = DartsPalette.Text.copy(alpha = 0.035f)
        for (i in 0 until 9) {
            val x = size.width * (0.08f + i * 0.11f)
            drawLine(
                color = shard,
                start = Offset(x, 0f),
                end = Offset(size.width * 0.52f, size.height),
                strokeWidth = size.width * 0.055f,
            )
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, DartsPalette.GlowNavy.copy(alpha = 0.55f))))
    }
}

@Composable
private fun VisitBar(state: GameState, visitHits: List<String>) {
    val slots = List(3) { index ->
        visitHits.getOrNull(index) ?: if (index == visitHits.size) "➤" else "—"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .background(DartsPalette.Panel, RoundedCornerShape(16.dp))
            .border(2.dp, DartsPalette.Red, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEachIndexed { index, label ->
            Text(
                text = label,
                color = DartsPalette.Text,
                fontSize = if (label == "➤" || label == "—") 18.sp else 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            if (index < 2) {
                Box(Modifier.width(1.dp).height(24.dp).background(DartsPalette.Text.copy(alpha = 0.12f)))
            }
        }
        Box(Modifier.width(1.dp).height(24.dp).background(DartsPalette.Text.copy(alpha = 0.12f)))
        val status = if (visitHits.lastOrNull() == "MISS") "MISS" else "${state.dartsLeft} LEFT"
        Text(
            text = status,
            color = DartsPalette.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.weight(1.2f),
            textAlign = TextAlign.Center,
        )
    }
}

internal fun hitLabel(hit: DartHit): String {
    val number = hit.number ?: return "MISS"
    if (number == BoardGeometry.BULL) return "BULL"
    return when (hit.multiplier) {
        3 -> "T$number"
        2 -> "D$number"
        else -> "S$number"
    }
}

private fun eventTarget(event: GameEvent): Int? = when (event) {
    is GameEvent.ShieldAdded -> event.target
    is GameEvent.ShieldRemoved -> event.target
    is GameEvent.SectorLocked -> event.target
    is GameEvent.SectorNeutralised -> event.target
    is GameEvent.BlockedByLock -> event.target
    else -> null
}

internal fun fetchAutodarts(config: AutodartsConfig): AutodartsState? =
    AutodartsClient(config.url).fetchState()
