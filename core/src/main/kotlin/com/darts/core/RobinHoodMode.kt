package com.darts.core

/** Rules for Robin Hood. Each round is one three-dart visit for every player. */
data class RobinHoodRules(
    val rounds: Int = 5,
) {
    init {
        require(rounds in 1..20) { "rounds must be 1..20" }
    }
}

data class RobinHoodState(
    val players: List<Player>,
    val rules: RobinHoodRules = RobinHoodRules(),
    val scores: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = DARTS_PER_ROUND,
    val roundNumber: Int = 1,
    val dartsThrown: Int = 0,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    companion object {
        const val DARTS_PER_ROUND = 3
    }
}

sealed interface RobinHoodEvent {
    data class DartScored(
        val playerId: Int,
        val hit: DartHit,
        val points: Int,
    ) : RobinHoodEvent

    data class TurnChanged(
        val playerId: Int,
        val roundNumber: Int,
    ) : RobinHoodEvent

    data class RoundChanged(val roundNumber: Int) : RobinHoodEvent
    data class GameFinished(val winnerId: Int?) : RobinHoodEvent
}

data class RobinHoodReduction(
    val state: RobinHoodState,
    val events: List<RobinHoodEvent>,
)

/**
 * Robin Hood rewards accuracy toward the bull. The number hit does not matter;
 * only the ring does. Each player throws three darts per round, and the number
 * of rounds is configurable.
 *
 * | Zone | Points |
 * | --- | ---: |
 * | Double | 1 |
 * | Large single | 2 |
 * | Treble | 4 |
 * | Small single | 4 |
 * | Outer bull | 6 |
 * | Inner bull | 8 |
 *
 * Highest total after the configured number of rounds wins. Ties are draws.
 */
object RobinHoodMode {
    fun newGame(
        players: List<Player>,
        rules: RobinHoodRules = RobinHoodRules(),
    ): RobinHoodState {
        require(players.size in 1..6) { "Robin Hood needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return RobinHoodState(
            players = players,
            rules = rules,
            scores = players.associate { it.id to 0 },
            dartsByPlayer = players.associate { it.id to 0 },
            dartsLeft = RobinHoodState.DARTS_PER_ROUND,
        )
    }

    fun apply(state: RobinHoodState, hit: DartHit): RobinHoodReduction {
        if (state.finished) return RobinHoodReduction(state, emptyList())

        val player = state.currentPlayer
        val points = pointsFor(hit)
        val scores = state.scores + (player.id to ((state.scores[player.id] ?: 0) + points))
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val events = mutableListOf<RobinHoodEvent>(
            RobinHoodEvent.DartScored(player.id, hit, points),
        )

        var dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        var currentPlayerIndex = state.currentPlayerIndex
        var roundNumber = state.roundNumber
        var finished = false

        if (dartsLeft <= 0) {
            val next = (state.currentPlayerIndex + 1) % state.players.size
            if (next == 0) {
                if (roundNumber >= state.rules.rounds) {
                    finished = true
                } else {
                    roundNumber += 1
                    events += RobinHoodEvent.RoundChanged(roundNumber)
                }
            }
            currentPlayerIndex = next
            dartsLeft = if (finished) 0 else RobinHoodState.DARTS_PER_ROUND
            events += RobinHoodEvent.TurnChanged(
                playerId = state.players[currentPlayerIndex].id,
                roundNumber = roundNumber,
            )
        }

        val winnerId = if (finished) winner(scores, state.players) else null
        if (finished) events += RobinHoodEvent.GameFinished(winnerId)

        return RobinHoodReduction(
            state = state.copy(
                scores = scores,
                dartsByPlayer = dartsByPlayer,
                currentPlayerIndex = currentPlayerIndex,
                dartsLeft = dartsLeft,
                roundNumber = roundNumber,
                dartsThrown = state.dartsThrown + 1,
                finished = finished,
                winnerId = winnerId,
            ),
            events = events,
        )
    }

    /** Ends an Autodarts visit early; the next player starts a fresh round turn. */
    fun advanceTurn(state: RobinHoodState): RobinHoodReduction {
        if (state.finished) return RobinHoodReduction(state, emptyList())

        val next = (state.currentPlayerIndex + 1) % state.players.size
        var roundNumber = state.roundNumber
        var finished = false
        val events = mutableListOf<RobinHoodEvent>()

        if (next == 0) {
            if (roundNumber >= state.rules.rounds) {
                finished = true
            } else {
                roundNumber += 1
                events += RobinHoodEvent.RoundChanged(roundNumber)
            }
        }

        val winnerId = if (finished) winner(state.scores, state.players) else null
        if (finished) events += RobinHoodEvent.GameFinished(winnerId)
        events += RobinHoodEvent.TurnChanged(
            playerId = state.players[next].id,
            roundNumber = roundNumber,
        )

        return RobinHoodReduction(
            state = state.copy(
                currentPlayerIndex = next,
                dartsLeft = if (finished) 0 else RobinHoodState.DARTS_PER_ROUND,
                roundNumber = roundNumber,
                finished = finished,
                winnerId = winnerId,
            ),
            events = events,
        )
    }

    fun pointsFor(hit: DartHit): Int {
        if (hit.isMiss || hit.number == null) return 0
        return when {
            hit.ring == Ring.INNER_BULL -> 8
            hit.ring == Ring.OUTER_BULL -> 6
            hit.ring == Ring.TREBLE -> 4
            hit.ring == Ring.SINGLE && hit.singleBand == SingleBand.SMALL -> 4
            hit.ring == Ring.SINGLE -> 2
            hit.ring == Ring.DOUBLE -> 1
            else -> 0
        }
    }

    fun winner(scores: Map<Int, Int>, players: List<Player>): Int? {
        if (players.isEmpty()) return null
        if (players.size == 1) return players.first().id
        val best = players.maxOf { scores[it.id] ?: 0 }
        val leaders = players.filter { (scores[it.id] ?: 0) == best }
        return leaders.singleOrNull()?.id
    }
}
