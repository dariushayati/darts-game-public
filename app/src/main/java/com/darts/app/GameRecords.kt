package com.darts.app

import com.darts.core.AroundTheClockMode
import com.darts.core.AroundTheClockState
import com.darts.core.CheckoutState
import com.darts.core.CricketState
import com.darts.core.GameState
import com.darts.core.GunslingerState
import com.darts.core.InvadersState
import com.darts.core.LavaFinishReason
import com.darts.core.LavaState
import com.darts.core.MatchPlayerRecord
import com.darts.core.MatchRecord
import com.darts.core.Player
import com.darts.core.RingRunState
import com.darts.core.RobinHoodState
import com.darts.core.WhackAMoleState
import com.darts.core.X01State
import com.darts.core.lockedCount
import com.darts.core.ownedCount
import java.util.UUID

private fun players(
    players: List<Player>,
    score: (Player) -> Int,
    darts: (Player) -> Int,
    detail: (Player) -> String = { "" },
): List<MatchPlayerRecord> = players.map { player ->
    MatchPlayerRecord(
        id = player.id,
        name = player.name,
        colorIndex = player.colorIndex,
        score = score(player),
        darts = darts(player),
        detail = detail(player),
    )
}

fun shieldsMatchRecord(
    state: GameState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.SHIELDS.name,
    gameTitle = GameType.SHIELDS.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = if (state.rules.endOnBoardFull && state.boardFull()) {
        "Board complete"
    } else {
        "Dart limit reached"
    },
    players = players(
        players = state.players,
        score = { state.ownedCount(it.id) },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { "${state.lockedCount(it.id)} locked" },
    ),
)

fun invadersMatchRecord(
    state: InvadersState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.INVADERS.name,
    gameTitle = GameType.INVADERS.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = if (state.allCleared) "All aliens cleared" else "Dart limit reached",
    players = players(
        players = state.players,
        score = { state.scores[it.id] ?: 0 },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { "${state.killsByPlayer[it.id] ?: 0} kills" },
    ),
)

fun lavaMatchRecord(
    state: LavaState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.LAVA.name,
    gameTitle = GameType.LAVA.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = when (state.finishReason) {
        LavaFinishReason.LAST_STANDING -> "Last player standing"
        LavaFinishReason.OUT_OF_LIVES -> "Out of lives"
        LavaFinishReason.BOARD_CONSUMED -> "Board consumed"
        null -> null
    },
    players = players(
        players = state.players,
        score = { state.livesByPlayer[it.id] ?: 0 },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { if ((state.livesByPlayer[it.id] ?: 0) > 0) "Survived" else "Eliminated" },
    ),
)

fun ringRunMatchRecord(
    state: RingRunState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.RING_RUN.name,
    gameTitle = GameType.RING_RUN.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = "Run complete",
    players = players(
        players = state.players,
        score = { state.scores[it.id] ?: 0 },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
    ),
)

fun robinHoodMatchRecord(
    state: RobinHoodState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.ROBIN_HOOD.name,
    gameTitle = GameType.ROBIN_HOOD.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = "Dart limit reached",
    players = players(
        players = state.players,
        score = { state.scores[it.id] ?: 0 },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
    ),
)

fun whackAMoleMatchRecord(
    state: WhackAMoleState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.WHACK_A_MOLE.name,
    gameTitle = GameType.WHACK_A_MOLE.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = if (state.players.size == 1) {
        "Cleared ${state.moles} moles"
    } else {
        "First to ${state.moles} moles"
    },
    players = players(
        players = state.players,
        score = { state.whacked(it.id) },
        darts = { 0 },
        detail = { "${state.whacked(it.id)}/${state.moles} moles" },
    ),
)


fun cricketMatchRecord(
    state: CricketState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.CRICKET.name,
    gameTitle = GameType.CRICKET.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = "Closed out",
    players = players(
        players = state.players,
        score = { state.pointsFor(it.id) },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { "${state.closedCount(it.id)}/7 closed" },
    ),
)


fun gunslingerMatchRecord(
    state: GunslingerState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.GUNSLINGER.name,
    gameTitle = GameType.GUNSLINGER.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = "${state.rules.rounds} rounds complete",
    players = players(
        players = state.players,
        score = { state.pointsFor(it.id) },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { "${state.hits(it.id)} hits" },
    ),
)

fun x01MatchRecord(
    state: X01State,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.X01.name,
    gameTitle = "X01",
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = when {
        state.finished && state.rules.legsToWin == 1 -> "${state.rules.startingScore} · Checked out"
        state.finished -> "${state.rules.startingScore} · First to ${state.rules.legsToWin} legs"
        else -> null
    },
    players = players(
        players = state.players,
        score = { state.legsWon(it.id) },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { player ->
            if (state.scoreFor(player.id) == 0) "Checked out" else "${state.scoreFor(player.id)} left"
        },
    ),
)


fun aroundTheClockMatchRecord(
    state: AroundTheClockState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.AROUND_THE_CLOCK.name,
    gameTitle = GameType.AROUND_THE_CLOCK.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = if (state.players.size == 1) {
        "Completed 1–20 + bull"
    } else {
        "First around the clock"
    },
    players = players(
        players = state.players,
        score = { state.progressFor(it.id) },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { player ->
            if (state.progressFor(player.id) >= AroundTheClockMode.TARGETS.size) {
                "Finished"
            } else {
                "Next ${state.labelFor(player.id)}"
            }
        },
    ),
)


fun checkoutMatchRecord(
    state: CheckoutState,
    id: String = UUID.randomUUID().toString(),
    finishedAt: Long = System.currentTimeMillis(),
): MatchRecord = MatchRecord(
    id = id,
    game = GameType.CHECKOUT.name,
    gameTitle = GameType.CHECKOUT.title,
    finishedAt = finishedAt,
    winnerId = state.winnerId,
    endReason = "Completed ${state.total} checkouts",
    players = players(
        players = state.players,
        score = { state.completed },
        darts = { state.dartsByPlayer[it.id] ?: 0 },
        detail = { "${state.completed}/${state.total} checkouts" },
    ),
)
