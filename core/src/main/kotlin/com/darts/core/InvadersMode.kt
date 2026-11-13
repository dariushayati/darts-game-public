package com.darts.core

/** Rules for multiplayer Invaders. Dart budgets count darts, not kills. */
data class InvadersRules(
    val dartsPerPlayer: Int = 15,
    val dartsPerTurn: Int = 3,
) {
    init {
        require(dartsPerTurn in 1..10) { "dartsPerTurn must be 1..10" }
        require(dartsPerPlayer > 0) { "dartsPerPlayer must be positive" }
        require(dartsPerPlayer % 3 == 0) { "dartsPerPlayer must be a multiple of 3" }
    }
}

data class AlienKill(
    val number: Int,
    /** 0 = first/inner alien, 1 = second/middle, 2 = third/outer. */
    val index: Int,
    val points: Int,
)

data class InvadersState(
    val players: List<Player>,
    val rules: InvadersRules,
    val aliens: Map<Int, Int> = NUMBERS.associateWith { ALIENS_PER_NUMBER },
    val scores: Map<Int, Int> = players.associate { it.id to 0 },
    val killsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = rules.dartsPerTurn,
    val turnNumber: Int = 1,
    val lastKills: List<AlienKill> = emptyList(),
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val aliensLeft: Int get() = aliens.values.sum()
    val allCleared: Boolean get() = aliens.values.all { it == 0 }
    val dartsThrown: Int get() = dartsByPlayer.values.sum()

    companion object {
        val NUMBERS: List<Int> = (1..20).toList()
        const val ALIENS_PER_NUMBER = 3
        const val ALL_ALIENS = 60
    }
}

sealed interface InvadersEvent {
    data class AlienKilled(
        val number: Int,
        val index: Int,
        val points: Int,
        val playerId: Int,
    ) : InvadersEvent

    data class NumberCleared(val number: Int) : InvadersEvent
    data class TurnChanged(val playerId: Int, val turnNumber: Int) : InvadersEvent
    data class DartUsed(val hit: DartHit) : InvadersEvent
    data class GameFinished(val winnerId: Int?, val allCleared: Boolean) : InvadersEvent
}

data class InvadersReduction(val state: InvadersState, val events: List<InvadersEvent>)

object InvadersMode {
    private val POINTS = intArrayOf(1, 3, 5)

    fun newGame(players: List<Player>, rules: InvadersRules = InvadersRules()): InvadersState {
        require(players.size in 1..6) { "Invaders needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return InvadersState(players = players, rules = rules)
    }

    fun apply(state: InvadersState, hit: DartHit): InvadersReduction {
        if (state.finished) return InvadersReduction(state, emptyList())

        val events = mutableListOf<InvadersEvent>()
        val player = state.currentPlayer
        val number = hit.number
        val killsWanted = when {
            number == null || number == BoardGeometry.BULL -> 0
            hit.ring == Ring.DOUBLE -> 2
            hit.ring == Ring.TREBLE -> 3
            hit.ring == Ring.SINGLE -> 1
            else -> 0
        }
        val remaining = number?.let { state.aliens[it] } ?: 0
        val kills = killsWanted.coerceAtMost(remaining)
        val killed = if (number != null && kills > 0) {
            val firstIndex = InvadersState.ALIENS_PER_NUMBER - remaining
            (0 until kills).map { offset ->
                val index = firstIndex + offset
                val points = POINTS[index]
                events += InvadersEvent.AlienKilled(number, index, points, player.id)
                AlienKill(number, index, points)
            }
        } else {
            emptyList()
        }

        val newAliens = if (killed.isEmpty()) {
            state.aliens
        } else {
            state.aliens + (number!! to remaining - kills)
        }
        val points = killed.sumOf { it.points }
        val scores = state.scores + (player.id to ((state.scores[player.id] ?: 0) + points))
        val killsByPlayer = state.killsByPlayer +
            (player.id to ((state.killsByPlayer[player.id] ?: 0) + killed.size))
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))

        var dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        var nextPlayer = state.currentPlayerIndex
        var turnNumber = state.turnNumber
        if (dartsLeft <= 0) {
            nextPlayer = (nextPlayer + 1) % state.players.size
            if (nextPlayer == 0) turnNumber += 1
            dartsLeft = state.rules.dartsPerTurn
            events += InvadersEvent.TurnChanged(state.players[nextPlayer].id, turnNumber)
        }

        if (number != null && newAliens[number] == 0 && remaining > 0) {
            events += InvadersEvent.NumberCleared(number)
        }
        events += InvadersEvent.DartUsed(hit)

        val allCleared = newAliens.values.all { it == 0 }
        val allDone = state.players.all {
            (dartsByPlayer[it.id] ?: 0) >= state.rules.dartsPerPlayer
        }
        val finished = allCleared || allDone
        val winnerId = if (finished) winner(scores, killsByPlayer, state.players) else null
        if (finished) events += InvadersEvent.GameFinished(winnerId, allCleared)

        val next = state.copy(
            aliens = newAliens,
            scores = scores,
            killsByPlayer = killsByPlayer,
            dartsByPlayer = dartsByPlayer,
            currentPlayerIndex = nextPlayer,
            dartsLeft = dartsLeft,
            turnNumber = turnNumber,
            lastKills = killed,
            finished = finished,
            winnerId = winnerId,
        )
        return InvadersReduction(next, events)
    }

    /** Ends a visit early (Autodarts); the next player starts a fresh turn. */
    fun advanceTurn(state: InvadersState): InvadersReduction {
        if (state.finished) return InvadersReduction(state, emptyList())
        val events = mutableListOf<InvadersEvent>()
        val nextPlayer = (state.currentPlayerIndex + 1) % state.players.size
        val turnNumber = state.turnNumber + if (nextPlayer == 0) 1 else 0
        val allDone = state.players.all {
            (state.dartsByPlayer[it.id] ?: 0) >= state.rules.dartsPerPlayer
        }
        val winnerId = if (allDone) winner(state.scores, state.killsByPlayer, state.players) else null
        events += InvadersEvent.TurnChanged(state.players[nextPlayer].id, turnNumber)
        if (allDone) events += InvadersEvent.GameFinished(winnerId, state.allCleared)
        val next = state.copy(
            currentPlayerIndex = nextPlayer,
            dartsLeft = state.rules.dartsPerTurn,
            turnNumber = turnNumber,
            finished = allDone,
            winnerId = winnerId,
        )
        return InvadersReduction(next, events)
    }

    fun winner(
        scores: Map<Int, Int>,
        killsByPlayer: Map<Int, Int>,
        players: List<Player>,
    ): Int? {
        val maxScore = players.maxOf { scores[it.id] ?: 0 }
        val leaders = players.filter { (scores[it.id] ?: 0) == maxScore }
        if (leaders.size == 1) return leaders.first().id
        val maxKills = leaders.maxOf { killsByPlayer[it.id] ?: 0 }
        val killLeaders = leaders.filter { (killsByPlayer[it.id] ?: 0) == maxKills }
        return if (killLeaders.size == 1) killLeaders.first().id else null
    }
}
