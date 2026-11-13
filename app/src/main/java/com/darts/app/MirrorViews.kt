package com.darts.app

import com.darts.core.AlienKill
import com.darts.core.AroundTheClockMode
import com.darts.core.AroundTheClockState
import com.darts.core.BoardGeometry
import com.darts.core.CheckoutState
import com.darts.core.CricketMode
import com.darts.core.CricketState
import com.darts.core.DartHit
import com.darts.core.GunslingerDifficulty
import com.darts.core.GunslingerMode
import com.darts.core.GunslingerState
import com.darts.core.GameState
import com.darts.core.InvadersState
import com.darts.core.LavaFinishReason
import com.darts.core.LavaState
import com.darts.core.MirrorPlayerView
import com.darts.core.MirrorView
import com.darts.core.Player
import com.darts.core.RingRunMode
import com.darts.core.RingRunRound
import com.darts.core.RingRunState
import com.darts.core.RobinHoodState
import com.darts.core.WhackAMoleState
import com.darts.core.X01State
import com.darts.core.lockedCount
import com.darts.core.ownedCount

private fun mirrorPlayers(
    players: List<Player>,
    activeId: Int?,
    value: (Player) -> Int,
    score: (Player) -> String,
    detail: (Player) -> String,
): List<MirrorPlayerView> {
    val top = players.maxOfOrNull(value) ?: 0
    return players.map { player ->
        MirrorPlayerView(
            id = player.id,
            name = player.name,
            colorIndex = player.colorIndex,
            score = score(player),
            detail = detail(player),
            active = player.id == activeId,
            leading = top > 0 && value(player) == top,
        )
    }
}

private fun List<Player>.winnerName(winnerId: Int?): String? =
    winnerId?.let { id -> firstOrNull { it.id == id }?.name }

fun GameState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView = MirrorView(
    gameType = "SHIELDS",
    title = "SHIELDS",
    modeLabel = if (rules.dartsPerPlayer > 0) {
        "${dartsByPlayer[currentPlayer.id] ?: 0}/${rules.dartsPerPlayer} DARTS"
    } else {
        "ENDLESS"
    },
    statusLabel = if (finished) "FINISHED" else "TURN $turnNumber",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> ownedCount(player.id) },
        score = { player -> "${ownedCount(player.id)}" },
        detail = { player -> "${lockedCount(player.id)} locked" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(lastTarget),
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = when {
        !finished -> null
        rules.endOnBoardFull && boardFull() -> "Board complete"
        else -> "Dart limit reached"
    },
)

fun InvadersState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
    lastKills: List<AlienKill> = emptyList(),
): MirrorView = MirrorView(
    gameType = "INVADERS",
    title = "INVADERS",
    modeLabel = "TURN $turnNumber",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> scores[player.id] ?: 0 },
        score = { player -> "${scores[player.id] ?: 0}" },
        detail = { player -> "${killsByPlayer[player.id] ?: 0} kills" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(lastTarget),
    targetLabels = aliens.entries
        .filter { it.value > 0 }
        .sortedBy { it.key }
        .map { "${it.key}×${it.value}" },
    alienCounts = aliens,
    alienKills = lastKills,
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = when {
        !finished -> null
        allCleared -> "All aliens cleared"
        else -> "Dart limit reached"
    },
)

fun LavaState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
    lastBurned: List<Int> = emptyList(),
    burnTick: Int = 0,
): MirrorView = MirrorView(
    gameType = "LAVA",
    title = "BOARD IS LAVA",
    modeLabel = "ROUND $roundNumber",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT · $numbersLeft/20 LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> livesByPlayer[player.id] ?: 0 },
        score = { player -> "${livesByPlayer[player.id] ?: 0}" },
        detail = { player ->
            if ((livesByPlayer[player.id] ?: 0) <= 0) "OUT" else "lives"
        },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(lastTarget),
    dangerNumbers = lavaNumbers.sorted(),
    targetLabels = lavaNumbers.sorted().map { "$it" },
    lastBurned = lastBurned,
    burnTick = burnTick,
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = when (finishReason) {
        LavaFinishReason.LAST_STANDING -> "Last player standing"
        LavaFinishReason.OUT_OF_LIVES -> "Out of lives"
        LavaFinishReason.BOARD_CONSUMED -> "Board consumed"
        null -> null
    },
)

fun RingRunState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
    lastPoints: Int? = null,
): MirrorView = MirrorView(
    gameType = "RING_RUN",
    title = "RING RUN",
    modeLabel = "ROUND $roundNumber/${RingRunMode.ROUNDS.size}",
    statusLabel = round.title,
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> scores[player.id] ?: 0 },
        score = { player -> "${scores[player.id] ?: 0}" },
        detail = { player -> "${dartsByPlayer[player.id] ?: 0} darts" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = when (round) {
        RingRunRound.LARGE_SINGLE -> RingRunMode.ROUND_TWO_NUMBERS.sorted()
        RingRunRound.BULL -> listOf(BoardGeometry.BULL)
        else -> listOfNotNull(lastTarget)
    },
    dangerNumbers = emptyList(),
    targetLabels = buildList {
        add(round.shortTitle)
        if (round == RingRunRound.LARGE_SINGLE) {
            add(RingRunMode.ROUND_TWO_NUMBERS.sorted().joinToString(" "))
        }
    },
    ringRoundIndex = roundIndex,
    lastPoints = lastPoints,
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "Four rounds complete" else null,
)

fun RobinHoodState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView = MirrorView(
    gameType = "ROBIN_HOOD",
    title = "ROBIN HOOD",
    modeLabel = "ROUND $roundNumber/${rules.rounds}",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> scores[player.id] ?: 0 },
        score = { player -> "${scores[player.id] ?: 0}" },
        detail = { player -> "${dartsByPlayer[player.id] ?: 0} darts" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(lastTarget),
    targetLabels = listOf("INNER BULL 8", "OUTER BULL 6", "SMALL/TREBLE 4", "LARGE 2", "DOUBLE 1"),
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "${rules.rounds} rounds complete" else null,
)

fun WhackAMoleState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
    whackTick: Int = 0,
): MirrorView = MirrorView(
    gameType = "WHACK_A_MOLE",
    title = "WHACK A MOLE",
    modeLabel = "${targets.size} TARGETS",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> progress[player.id] ?: 0 },
        score = { player -> "${progress[player.id] ?: 0}/${targets.size}" },
        detail = { player -> "${left(player.id)} left" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(lastTarget, target?.number).distinct(),
    targetLabels = targets.mapIndexed { index, mole -> "${index + 1}. ${mole.label}" },
    moleTarget = target,
    whackTick = whackTick,
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "First to clear the list" else null,
)


fun CricketState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView = MirrorView(
    gameType = "CRICKET",
    title = "CRICKET",
    modeLabel = "${currentPlayer.name.uppercase()} · TURN $turnNumber",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> pointsFor(player.id) },
        score = { player -> "${pointsFor(player.id)}" },
        detail = { player -> "${closedCount(player.id)}/7 closed" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = if (finished) emptyList() else openTargets(currentPlayer.id),
    cricketMarks = marks,
    targetLabels = CricketMode.TARGETS.map { target ->
        val marks = marks(currentPlayer.id, target)
        when {
            marks >= CricketMode.MARKS_TO_CLOSE -> "${if (target == 25) "BULL" else "$target"} X"
            marks > 0 -> "${if (target == 25) "BULL" else "$target"} $marks/3"
            else -> if (target == 25) "BULL" else "$target"
        }
    },
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "Closed out" else null,
)


fun GunslingerState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
    live: Boolean,
    potentialPoints: Int,
    waitingForTakeout: Boolean = false,
): MirrorView = MirrorView(
    gameType = "GUNSLINGER",
    title = "GUNSLINGER",
    modeLabel = "${rules.difficulty.name} · ROUND $roundNumber/${rules.rounds}",
    statusLabel = when {
        finished -> "FINISHED"
        live -> "TARGET LIVE"
        waitingForTakeout -> "TAKE OUT DARTS"
        else -> "GET READY"
    },
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> pointsFor(player.id) },
        score = { player -> "${pointsFor(player.id)}" },
        detail = { player ->
            val last = lastTurnPoints(player.id)
            if (last > 0) "${hits(player.id)} hits · +$last" else "${hits(player.id)} hits"
        },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = if (live && !finished) listOf(target) else emptyList(),
    secondaryHighlightNumbers = if (
        live && !finished && rules.difficulty == GunslingerDifficulty.NORMAL
    ) {
        GunslingerMode.neighbours(target).toList()
    } else {
        emptyList()
    },
    targetLabels = buildList {
        add(
            when {
                live -> "TARGET $target"
                waitingForTakeout -> "TAKE OUT DARTS"
                else -> "GET READY"
            },
        )
        add("HITS $hits/${GunslingerMode.DARTS_PER_TURN}")
        add(if (live) "NEXT $potentialPoints" else "MAX ${rules.basePoints}")
        add("BANKED $turnPoints")
        add(
            if (rules.difficulty == GunslingerDifficulty.NORMAL) "NEIGHBOURS 50%"
            else "HARD MODE",
        )
    },
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "${rules.rounds} rounds complete" else null,
)

fun X01State.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView {
    val best = players.minOfOrNull { scoreFor(it.id) } ?: rules.startingScore
    return MirrorView(
        gameType = "X01",
        title = "X01",
        modeLabel = "${rules.startingScore} · ${rules.outRule.name} OUT · LEG $legNumber",
        statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS · ${currentPlayer.name.uppercase()}",
        players = players.map { player ->
            MirrorPlayerView(
                id = player.id,
                name = player.name,
                colorIndex = player.colorIndex,
                score = "${scoreFor(player.id)}",
                detail = buildString {
                    append("${legsWon(player.id)} legs")
                    if (!isOpened(player.id)) append(" · not in")
                },
                active = player.id == currentPlayer.id,
                leading = !finished && scoreFor(player.id) == best,
            )
        },
        currentPlayerId = currentPlayer.id,
        lastHit = recentHits.lastOrNull(),
        recentHits = recentHits,
        flashTick = flashTick,
        highlightNumbers = listOfNotNull(lastTarget),
        targetLabels = listOf(
            "LEG $legNumber",
            "${rules.inRule.name} IN",
            "${rules.outRule.name} OUT",
            "FIRST TO ${rules.legsToWin} ${if (rules.legsToWin == 1) "LEG" else "LEGS"}",
        ),
        finished = finished,
        winnerName = players.winnerName(winnerId),
        endReason = if (finished) "Checked out" else null,
    )
}


fun AroundTheClockState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView = MirrorView(
    gameType = "AROUND_THE_CLOCK",
    title = "AROUND THE CLOCK",
    modeLabel = if (finished) "FINISHED" else "TARGET ${labelFor(currentPlayer.id)}",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { player -> progressFor(player.id) },
        score = { player -> "${progressFor(player.id)}" },
        detail = { player ->
            if (progressFor(player.id) >= AroundTheClockMode.TARGETS.size) {
                "DONE"
            } else {
                "NEXT ${labelFor(player.id)}"
            }
        },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(target, lastTarget).distinct(),
    targetLabels = AroundTheClockMode.TARGETS.map { AroundTheClockMode.labelFor(it) },
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "First around the clock" else null,
)


fun CheckoutState.toMirrorView(
    lastTarget: Int?,
    recentHits: List<DartHit>,
    flashTick: Int,
): MirrorView = MirrorView(
    gameType = "CHECKOUT",
    title = "CHECKOUT",
    modeLabel = if (finished) "FINISHED" else "${rules.difficulty.name} · ${index + 1}/$total",
    statusLabel = if (finished) "FINISHED" else "$dartsLeft DARTS LEFT",
    players = mirrorPlayers(
        players = players,
        activeId = currentPlayer.id,
        value = { completed },
        score = { "$completed/$total" },
        detail = { if (finished) "DONE" else "NEXT ${targetLabel}" },
    ),
    currentPlayerId = currentPlayer.id,
    lastHit = recentHits.lastOrNull(),
    recentHits = recentHits,
    flashTick = flashTick,
    highlightNumbers = listOfNotNull(expectedDart?.number, lastTarget).distinct(),
    targetLabels = currentPath?.darts?.map { it.label }.orEmpty(),
    checkoutTarget = expectedDart,
    finished = finished,
    winnerName = players.winnerName(winnerId),
    endReason = if (finished) "Completed $total checkouts" else null,
)
