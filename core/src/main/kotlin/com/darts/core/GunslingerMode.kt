package com.darts.core

import kotlin.random.Random

enum class GunslingerDifficulty {
    /** Neighbouring board numbers also score, at half the target's current value. */
    NORMAL,

    /** Only the called target number scores. */
    HARD,
}

/**
 * Rules for Gunslinger.
 *
 * Each turn waits for [beeps] countdown beeps, then reveals a random number 1–20.
 * Every scoring dart banks [basePoints] reduced by [pointsPerSecond] for every second
 * since the reveal, with [minimumPoints] as the floor. In [GunslingerDifficulty.NORMAL]
 * the two neighbouring board numbers score half; in [GunslingerDifficulty.HARD] only
 * the called number scores.
 */
data class GunslingerRules(
    val rounds: Int = 5,
    val beeps: Int = 3,
    val basePoints: Int = 1000,
    val pointsPerSecond: Int = 50,
    val minimumPoints: Int = 100,
    val difficulty: GunslingerDifficulty = GunslingerDifficulty.NORMAL,
) {
    init {
        require(rounds in 1..50) { "rounds must be 1..50" }
        require(beeps in 0..10) { "beeps must be 0..10" }
        require(basePoints > 0) { "basePoints must be positive" }
        require(pointsPerSecond >= 0) { "pointsPerSecond must be >= 0" }
        require(minimumPoints in 0..basePoints) { "minimumPoints must be 0..basePoints" }
    }
}

data class GunslingerState(
    val players: List<Player>,
    val rules: GunslingerRules = GunslingerRules(),
    val target: Int,
    val roundNumber: Int = 1,
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = GunslingerMode.DARTS_PER_TURN,
    val hits: Int = 0,
    val turnPoints: Int = 0,
    val scores: Map<Int, Int> = players.associate { it.id to 0 },
    val lastTurnScores: Map<Int, Int> = players.associate { it.id to 0 },
    val hitsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val missesByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun pointsFor(playerId: Int): Int = scores[playerId] ?: 0

    fun lastTurnPoints(playerId: Int): Int = lastTurnScores[playerId] ?: 0

    fun hits(playerId: Int): Int = hitsByPlayer[playerId] ?: 0

    fun misses(playerId: Int): Int = missesByPlayer[playerId] ?: 0

    fun potentialPoints(elapsedMs: Long): Int =
        GunslingerMode.pointsFor(elapsedMs, rules)

    companion object {
        val NUMBERS: List<Int> = (1..20).toList()
    }
}

sealed interface GunslingerEvent {
    data class DartThrown(
        val playerId: Int,
        val hit: DartHit,
        val wasTarget: Boolean,
        val elapsedMs: Long,
    ) : GunslingerEvent

    data class PointsBanked(
        val playerId: Int,
        val target: Int,
        val points: Int,
        val turnPoints: Int,
    ) : GunslingerEvent

    data class TurnScored(
        val playerId: Int,
        val points: Int,
        val elapsedMs: Long,
        val allHit: Boolean,
    ) : GunslingerEvent

    data class TargetReady(
        val playerId: Int,
        val target: Int,
        val roundNumber: Int,
    ) : GunslingerEvent

    data class GameFinished(val winnerId: Int?) : GunslingerEvent
}

data class GunslingerReduction(val state: GunslingerState, val events: List<GunslingerEvent>)

/**
 * Gunslinger is a 1–6 player reaction game. Every player gets [GunslingerRules.rounds]
 * turns. Before each turn the board waits for the configured number of beeps, then a
 * random number from 1 to 20 is revealed. Every scoring dart banks the current value:
 * [GunslingerRules.basePoints] falling by [GunslingerRules.pointsPerSecond] as the turn
 * runs. In Normal mode the neighbouring board numbers score half; in Hard mode only the
 * called number scores. Misses bank nothing. The highest total wins.
 */
object GunslingerMode {
    const val DARTS_PER_TURN = 3
    const val NEIGHBOUR_POINTS_DIVISOR = 2

    /** The two numbers physically adjacent to [target] on the board. */
    fun neighbours(target: Int): Set<Int> {
        val index = BoardGeometry.SECTOR_ORDER.indexOf(target)
        if (index < 0) return emptySet()
        val order = BoardGeometry.SECTOR_ORDER
        val count = BoardGeometry.SECTOR_COUNT
        return setOf(
            order[(index - 1 + count) % count],
            order[(index + 1) % count],
        )
    }

    fun pointsFor(elapsedMs: Long, rules: GunslingerRules = GunslingerRules()): Int {
        val elapsed = elapsedMs.coerceAtLeast(0L)
        val decay = (elapsed / 1000L) * rules.pointsPerSecond +
            ((elapsed % 1000L) * rules.pointsPerSecond) / 1000L
        return (rules.basePoints - decay)
            .coerceAtLeast(rules.minimumPoints.toLong())
            .toInt()
    }

    fun newGame(
        players: List<Player>,
        rules: GunslingerRules = GunslingerRules(),
        random: Random = Random.Default,
    ): GunslingerState {
        require(players.size in 1..6) { "Gunslinger needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return GunslingerState(
            players = players,
            rules = rules,
            target = randomTarget(random),
            dartsLeft = DARTS_PER_TURN,
        )
    }

    /** Throws one dart. [elapsedMs] is measured from the target reveal. */
    fun apply(
        state: GunslingerState,
        hit: DartHit,
        elapsedMs: Long,
        random: Random = Random.Default,
    ): GunslingerReduction {
        if (state.finished) return GunslingerReduction(state, emptyList())

        val player = state.currentPlayer
        val number = hit.number
        val targetHit = !hit.isMiss && number == state.target
        val neighbourHit = !targetHit && !hit.isMiss && number != null &&
            state.rules.difficulty == GunslingerDifficulty.NORMAL &&
            number in neighbours(state.target)
        val scoringHit = targetHit || neighbourHit
        val hits = state.hits + if (scoringHit) 1 else 0
        val events = mutableListOf<GunslingerEvent>(
            GunslingerEvent.DartThrown(player.id, hit, targetHit, elapsedMs),
        )
        var turnPoints = state.turnPoints
        if (scoringHit) {
            val value = pointsFor(elapsedMs, state.rules)
            val gained = if (targetHit) value else value / NEIGHBOUR_POINTS_DIVISOR
            turnPoints += gained
            events += GunslingerEvent.PointsBanked(player.id, state.target, gained, turnPoints)
        }

        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val hitsByPlayer = state.hitsByPlayer +
            (player.id to ((state.hitsByPlayer[player.id] ?: 0) + if (scoringHit) 1 else 0))
        val missesByPlayer = state.missesByPlayer +
            (player.id to ((state.missesByPlayer[player.id] ?: 0) + if (scoringHit) 0 else 1))

        val dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        val thrown = state.copy(
            dartsLeft = dartsLeft,
            hits = hits,
            turnPoints = turnPoints,
            dartsByPlayer = dartsByPlayer,
            hitsByPlayer = hitsByPlayer,
            missesByPlayer = missesByPlayer,
        )
        if (dartsLeft > 0) {
            return GunslingerReduction(thrown, events)
        }

        return completeTurn(thrown, elapsedMs, random, events)
    }

    /**
     * Ends a visit early (Autodarts pulled darts). A turn without all three hits scores
     * zero and passes to the next player.
     */
    fun finishTurn(
        state: GunslingerState,
        elapsedMs: Long,
        random: Random = Random.Default,
    ): GunslingerReduction {
        if (state.finished) return GunslingerReduction(state, emptyList())
        return completeTurn(state, elapsedMs, random, mutableListOf())
    }

    private fun completeTurn(
        state: GunslingerState,
        elapsedMs: Long,
        random: Random,
        events: MutableList<GunslingerEvent>,
    ): GunslingerReduction {
        val player = state.currentPlayer
        val allHit = state.hits >= DARTS_PER_TURN
        val points = state.turnPoints
        val scores = state.scores + (player.id to (state.pointsFor(player.id) + points))
        val lastTurnScores = state.lastTurnScores + (player.id to points)
        events += GunslingerEvent.TurnScored(player.id, points, elapsedMs, allHit)

        var nextPlayer = (state.currentPlayerIndex + 1) % state.players.size
        var roundNumber = state.roundNumber
        if (nextPlayer == 0) roundNumber += 1
        val finished = roundNumber > state.rules.rounds

        if (finished) {
            val winnerId = winner(scores, state.players)
            events += GunslingerEvent.GameFinished(winnerId)
            return GunslingerReduction(
                state.copy(
                    scores = scores,
                    lastTurnScores = lastTurnScores,
                    dartsLeft = 0,
                    roundNumber = state.rules.rounds,
                    finished = true,
                    winnerId = winnerId,
                ),
                events,
            )
        }

        val target = randomTarget(random)
        events += GunslingerEvent.TargetReady(state.players[nextPlayer].id, target, roundNumber)
        return GunslingerReduction(
            state.copy(
                target = target,
                roundNumber = roundNumber,
                currentPlayerIndex = nextPlayer,
                dartsLeft = DARTS_PER_TURN,
                hits = 0,
                turnPoints = 0,
                scores = scores,
                lastTurnScores = lastTurnScores,
            ),
            events,
        )
    }

    fun winner(scores: Map<Int, Int>, players: List<Player>): Int? {
        val best = players.maxOf { scores[it.id] ?: 0 }
        val leaders = players.filter { (scores[it.id] ?: 0) == best }
        return if (leaders.size == 1) leaders.first().id else null
    }

    private fun randomTarget(random: Random): Int = GunslingerState.NUMBERS.random(random)
}
