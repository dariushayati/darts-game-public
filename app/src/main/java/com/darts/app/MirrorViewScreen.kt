package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.GameState
import com.darts.core.MirrorPlayerView
import com.darts.core.MirrorView
import com.darts.core.Player
import com.darts.core.RingRunMode

@Composable
fun MirrorViewContent(
    view: MirrorView,
    onExit: () -> Unit,
) {
    val accent = view.players.firstOrNull { it.active }?.colorIndex
        ?.let { playerColor(it) }
        ?: DartsPalette.BlueBright
    val boardState = rememberNeutralBoardState(
        view.players.map { Player(it.id, it.name, it.colorIndex) },
    )

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
            val twoPane = LayoutPolicy.useTwoPane(
                LocalConfiguration.current.smallestScreenWidthDp,
                maxWidth.value,
                maxHeight.value,
            )
            val panelWidth = LayoutPolicy.panelWidthDp(maxWidth.value).dp
            Column(Modifier.fillMaxSize()) {
                MirrorViewTopBar(title = view.title, accent = accent)
                if (twoPane) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .width(panelWidth)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PlayerStack(view.players)
                            Spacer(Modifier.height(4.dp))
                            StatusStrip(view, accent)
                        }
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().padding(14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            MirrorBoard(view, boardState)
                        }
                    }
                } else {
                    PlayerStrip(view.players)
                    StatusStrip(view, accent)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MirrorBoard(view, boardState)
                    }
                }
            }
        }
        if (view.finished) {
            MirrorFinishedOverlay(view, accent, onExit)
        }
    }
}

internal fun mirrorGameType(gameType: String): GameType? =
    GameType.entries.firstOrNull { it.name == gameType }

@Composable
private fun MirrorBoard(view: MirrorView, boardState: GameState) {
    when (mirrorGameType(view.gameType)) {
        GameType.SHIELDS -> MirrorBoardCanvas(
            view = view,
            boardState = boardState,
            accent = DartsPalette.Green,
        )

        GameType.INVADERS -> {
            Box(Modifier.fillMaxSize()) {
                MirrorBoardCanvas(
                    view = view,
                    boardState = boardState,
                    playScale = INVADERS_PLAY_SCALE,
                    accent = DartsPalette.Violet,
                )
                InvadersAliensOverlay(
                    aliens = view.alienCounts,
                    lastKills = view.alienKills,
                    killTick = view.flashTick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        GameType.LAVA -> {
            val overlay = rememberLavaBoardOverlay(
                lavaNumbers = view.dangerNumbers.toSet(),
                lastBurned = view.lastBurned,
                burnTick = view.burnTick,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = LAVA_PLAY_SCALE,
                accent = DartsPalette.Lava,
                boardOverlay = overlay,
            )
        }

        GameType.RING_RUN -> {
            val round = RingRunMode.ROUNDS.getOrElse(view.ringRoundIndex) { RingRunMode.ROUNDS.first() }
            val overlay = rememberRingRunBoardOverlay(
                round = round,
                hitTick = view.flashTick,
                hitPoints = view.lastPoints,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = RING_RUN_PLAY_SCALE,
                accent = DartsPalette.Yellow,
                boardOverlay = overlay,
            )
        }

        GameType.ROBIN_HOOD -> MirrorBoardCanvas(
            view = view,
            boardState = boardState,
            accent = DartsPalette.Amber,
            zonePalette = RobinHoodZonePalette,
        )

        GameType.WHACK_A_MOLE -> {
            val overlay = rememberWhackAMoleBoardOverlay(
                target = view.moleTarget,
                whackTick = view.whackTick,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = WHACK_A_MOLE_PLAY_SCALE,
                accent = DartsPalette.Mole,
                boardOverlay = overlay,
            )
        }

        GameType.CRICKET -> {
            val overlay = rememberCricketBoardOverlay(
                players = boardState.players,
                marks = view.cricketMarks,
                lastTarget = view.lastHit?.number,
                hitTick = view.flashTick,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = CRICKET_PLAY_SCALE,
                accent = DartsPalette.DartCyan,
                boardOverlay = overlay,
            )
        }

        GameType.X01 -> MirrorBoardCanvas(
            view = view,
            boardState = boardState,
            accent = DartsPalette.Blue,
        )

        GameType.GUNSLINGER -> {
            val overlay = rememberGunslingerBoardOverlay(
                target = view.highlightNumbers.firstOrNull(),
                lastTarget = view.lastHit?.number,
                hitTick = view.flashTick,
                neighbourTargets = view.secondaryHighlightNumbers.toSet(),
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = GUNSLINGER_PLAY_SCALE,
                accent = DartsPalette.Red,
                boardOverlay = overlay,
            )
        }

        GameType.AROUND_THE_CLOCK -> {
            val overlay = rememberAroundTheClockBoardOverlay(
                target = view.highlightNumbers.firstOrNull(),
                hitTick = view.flashTick,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = AROUND_THE_CLOCK_PLAY_SCALE,
                accent = DartsPalette.DartCyan,
                boardOverlay = overlay,
            )
        }

        GameType.CHECKOUT -> {
            val overlay = rememberCheckoutBoardOverlay(
                target = view.checkoutTarget,
                hitTick = view.flashTick,
            )
            MirrorBoardCanvas(
                view = view,
                boardState = boardState,
                playScale = CHECKOUT_PLAY_SCALE,
                accent = DartsPalette.Amber,
                boardOverlay = overlay,
            )
        }

        null -> MirrorBoardCanvas(
            view = view,
            boardState = boardState,
            accent = DartsPalette.BlueBright,
        )
    }
}

@Composable
private fun MirrorBoardCanvas(
    view: MirrorView,
    boardState: GameState,
    playScale: Float = 0.74f,
    accent: Color = DartsPalette.Green,
    zonePalette: BoardZonePalette? = null,
    boardOverlay: (androidx.compose.ui.graphics.drawscope.DrawScope.(Offset, Float) -> Unit)? = null,
) {
    BoardCanvas(
        state = boardState,
        lastTarget = view.lastHit?.number,
        flashTick = view.flashTick,
        recentTargets = view.recentHits,
        enabled = false,
        onHit = {},
        playScale = playScale,
        accent = accent,
        zonePalette = zonePalette,
        boardOverlay = boardOverlay,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun MirrorViewTopBar(title: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(DartsPalette.Green.copy(alpha = 0.14f))
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
    }
}

@Composable
private fun PlayerStrip(players: List<MirrorPlayerView>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        players.forEach { player ->
            PlayerCard(player, Modifier.width(116.dp))
        }
    }
}

@Composable
private fun PlayerStack(players: List<MirrorPlayerView>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        players.forEach { player ->
            PlayerCard(player, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PlayerCard(player: MirrorPlayerView, modifier: Modifier = Modifier) {
    val color = playerColor(player.colorIndex)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (player.active) color.copy(alpha = 0.16f) else DartsPalette.Surface)
            .border(
                width = if (player.active) 1.5.dp else 1.dp,
                color = if (player.active) color.copy(alpha = 0.9f) else DartsPalette.Border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(vertical = 7.dp, horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = player.name.uppercase(),
            color = if (player.active) color else DartsPalette.Muted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = player.score,
            color = DartsPalette.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        if (player.detail.isNotBlank()) {
            Text(
                text = player.detail.uppercase(),
                color = DartsPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (player.leading) {
            Text(
                text = "LEAD",
                color = color,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun StatusStrip(view: MirrorView, accent: Color) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = view.modeLabel,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = view.statusLabel,
                color = DartsPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            )
        }
        val danger = view.dangerNumbers.isNotEmpty()
        val labels = if (danger) view.dangerNumbers.map { it.toString() } else view.targetLabels
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEach { label ->
                    MirrorChip(label, if (danger) DartsPalette.Red else accent)
                }
            }
        }
        if (view.recentHits.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "RECENT",
                    color = DartsPalette.Muted,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                view.recentHits.forEach { hit ->
                    MirrorChip(hitLabel(hit), DartsPalette.Muted)
                }
            }
        }
    }
}

@Composable
private fun MirrorChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
private fun MirrorFinishedOverlay(view: MirrorView, accent: Color, onExit: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 380.dp)
                .padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DartsPalette.Surface)
                .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "GAME OVER",
                color = DartsPalette.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                text = view.winnerName?.uppercase() ?: "DRAW",
                color = accent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
            )
            if (!view.endReason.isNullOrBlank()) {
                Text(
                    text = view.endReason!!,
                    color = DartsPalette.Muted,
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = onExit,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DartsPalette.Blue,
                    contentColor = DartsPalette.onColor(DartsPalette.Blue),
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("BACK TO JOIN", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            }
        }
    }
}
