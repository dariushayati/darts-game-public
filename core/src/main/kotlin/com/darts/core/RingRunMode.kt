package com.darts.core

/** Rules for Ring Run. Each round is one visit for every player. */
data class RingRunRules(
    val dartsPerTurn: Int = 3,
) {
    init {
        require(dartsPerTurn in 1..10) { "dartsPerTurn must be 1..10" }
    }
}

/**
 * The four scoring zones, from the widest opening to the bull finish.
 * Every number in the highlighted ring counts.
 */
enum class RingRunRound(
    val title: String,
    val shortTitle: String,
    val description: String,
    val points: Int,
) {
    DOUBLE_AND_LARGE_SINGLE(
        title = "DOUBLE + LARGE SINGLE",
        shortTitle = "DOUBLE + LARGE",
        description = "Any double or large single scores",
        points = 1,
    ),
    LARGE_SINGLE(
        title = "LARGE SINGLE · 10 NUMBERS",
        shortTitle = "PARTIAL LARGE SINGLE",
        description = "Only 12, 5, 20, 1, 18, 7, 19, 3, 17 and 2 count",
        points = 1,
    ),
    SMALL_SINGLE(
        title = "SMALL SINGLE",
        shortTitle = "SMALL SINGLE",
        description = "The target shrinks to any small single",
        points = 2,
    ),
    BULL(
        title = "BULL + OUTER BULL",
        shortTitle = "BULL",
        description = "Finish on the bull or outer bull",
        points = 3,
    ),
}

data class RingRunState(
    val players: List<Player>,
    val rules: RingRunRules = RingRunRules(),
    val roundIndex: Int = 0,
    val scores: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = rules.dartsPerTurn,
    val dartsThrown: Int = 0,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val round: RingRunRound get() = RingRunMode.ROUNDS[roundIndex]
    val roundNumber: Int get() = roundIndex + 1
}

sealed interface RingRunEvent {
    data class DartScored(
        val playerId: Int,
        val hit: DartHit,
        val points: Int,
    ) : RingRunEvent

    data class TurnChanged(
        val playerId: Int,
        val roundNumber: Int,
    ) : RingRunEvent

    data class RoundChanged(
        val roundNumber: Int,
    ) : RingRunEvent

    data class GameFinished(val winnerId: Int?) : RingRunEvent
}

data class RingRunReduction(
    val state: RingRunState,
    val events: List<RingRunEvent>,
)

/**
 * Ring Run is a four-round ring game for 1–6 players.
 *
 * Each round highlights a whole ring around the board. Any number in that ring
 * counts. Every player throws [RingRunRules.dartsPerTurn] darts before the next
 * round starts.
 *
 * | Round | Highlighted zone | Points |
 * | --- | --- | ---: |
 * | 1 | double + large single | 1 |
 * | 2 | large single | 1 |
 * | 3 | small single | 2 |
 * | 4 | inner/outer bull | 3 |
 *
 * Out-of-zone hits and misses score zero but still consume a dart. The highest
 * total after round four wins; ties are draws. Solo play reports the final score.
 */
object RingRunMode {
    val ROUNDS: List<RingRunRound> = RingRunRound.entries

    /** Round 2 only highlights these numbers. */
    val ROUND_TWO_NUMBERS: Set<Int> = setOf(12, 5, 20, 1, 18, 7, 19, 3, 17, 2)

    fun newGame(
        players: List<Player>,
        rules: RingRunRules = RingRunRules(),
    ): RingRunState {
        require(players.size in 1..6) { "Ring Run needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return RingRunState(
            players = players,
            rules = rules,
            roundIndex = 0,
            scores = players.associate { it.id to 0 },
            dartsByPlayer = players.associate { it.id to 0 },
            dartsLeft = rules.dartsPerTurn,
        )
    }

    fun apply(
        state: RingRunState,
        hit: DartHit,
    ): RingRunReduction {
        if (state.finished) return RingRunReduction(state, emptyList())

        val player = state.currentPlayer
        val points = pointsFor(state.round, hit)
        val scores = state.scores + (player.id to ((state.scores[player.id] ?: 0) + points))
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val events = mutableListOf<RingRunEvent>(
            RingRunEvent.DartScored(player.id, hit, points),
        )

        var dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        var currentPlayerIndex = state.currentPlayerIndex
        var roundIndex = state.roundIndex
        var finished = false

        if (dartsLeft <= 0) {
            val next = (state.currentPlayerIndex + 1) % state.players.size
            if (next == 0) {
                if (roundIndex >= ROUNDS.lastIndex) {
                    finished = true
                } else {
                    roundIndex += 1
                    events += RingRunEvent.RoundChanged(roundIndex + 1)
                }
            }
            currentPlayerIndex = next
            dartsLeft = if (finished) 0 else state.rules.dartsPerTurn
            events += RingRunEvent.TurnChanged(
                playerId = state.players[currentPlayerIndex].id,
                roundNumber = roundIndex + 1,
            )
        }

        val winnerId = if (finished) winner(scores, state.players) else null
        if (finished) events += RingRunEvent.GameFinished(winnerId)

        return RingRunReduction(
            state = state.copy(
                roundIndex = roundIndex,
                scores = scores,
                dartsByPlayer = dartsByPlayer,
                currentPlayerIndex = currentPlayerIndex,
                dartsLeft = dartsLeft,
                dartsThrown = state.dartsThrown + 1,
                finished = finished,
                winnerId = winnerId,
            ),
            events = events,
        )
    }

    /** Ends an Autodarts visit early; the next player starts a fresh turn. */
    fun advanceTurn(state: RingRunState): RingRunReduction {
        if (state.finished) return RingRunReduction(state, emptyList())

        val next = (state.currentPlayerIndex + 1) % state.players.size
        var roundIndex = state.roundIndex
        var finished = false
        val events = mutableListOf<RingRunEvent>()

        if (next == 0) {
            if (roundIndex >= ROUNDS.lastIndex) {
                finished = true
            } else {
                roundIndex += 1
                events += RingRunEvent.RoundChanged(roundIndex + 1)
            }
        }

        val winnerId = if (finished) winner(state.scores, state.players) else null
        if (finished) events += RingRunEvent.GameFinished(winnerId)
        events += RingRunEvent.TurnChanged(
            playerId = state.players[next].id,
            roundNumber = roundIndex + 1,
        )

        return RingRunReduction(
            state = state.copy(
                roundIndex = roundIndex,
                currentPlayerIndex = next,
                dartsLeft = if (finished) 0 else state.rules.dartsPerTurn,
                finished = finished,
                winnerId = winnerId,
            ),
            events = events,
        )
    }

    fun pointsFor(round: RingRunRound, hit: DartHit): Int {
        if (hit.isMiss || hit.number == null) return 0
        return when (round) {
            RingRunRound.DOUBLE_AND_LARGE_SINGLE -> {
                val valid = hit.ring == Ring.DOUBLE ||
                    (hit.ring == Ring.SINGLE && hit.singleBand == SingleBand.LARGE)
                if (valid) round.points else 0
            }

            RingRunRound.LARGE_SINGLE -> {
                val valid = hit.number in ROUND_TWO_NUMBERS &&
                    hit.ring == Ring.SINGLE &&
                    hit.singleBand == SingleBand.LARGE
                if (valid) round.points else 0
            }

            RingRunRound.SMALL_SINGLE -> {
                val valid = hit.ring == Ring.SINGLE && hit.singleBand == SingleBand.SMALL
                if (valid) round.points else 0
            }

            RingRunRound.BULL -> {
                val valid = hit.number == BoardGeometry.BULL &&
                    (hit.ring == Ring.INNER_BULL || hit.ring == Ring.OUTER_BULL)
                if (valid) round.points else 0
            }
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
