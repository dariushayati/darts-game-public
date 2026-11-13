package com.darts.core

/** Rules for Cricket / Tactics. The target list is fixed; only the visit length varies. */
data class CricketRules(
    val dartsPerTurn: Int = 3,
) {
    init {
        require(dartsPerTurn in 1..10) { "dartsPerTurn must be 1..10" }
    }
}

data class CricketState(
    val players: List<Player>,
    val rules: CricketRules = CricketRules(),
    val marks: Map<Int, Map<Int, Int>> = players.associate { player ->
        player.id to CricketMode.TARGETS.associateWith { 0 }
    },
    val points: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = rules.dartsPerTurn,
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsThrown: Int = 0,
    val turnNumber: Int = 1,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun marks(playerId: Int, target: Int): Int = marks[playerId]?.get(target) ?: 0

    fun pointsFor(playerId: Int): Int = points[playerId] ?: 0

    fun isClosed(playerId: Int, target: Int): Boolean =
        marks(playerId, target) >= CricketMode.MARKS_TO_CLOSE

    fun closedCount(playerId: Int): Int =
        CricketMode.TARGETS.count { isClosed(playerId, it) }

    fun allClosed(playerId: Int): Boolean =
        CricketMode.TARGETS.all { isClosed(playerId, it) }

    fun openTargets(playerId: Int): List<Int> =
        CricketMode.TARGETS.filter { !isClosed(playerId, it) }

    fun hasOpenOpponent(playerId: Int, target: Int): Boolean =
        players.any { it.id != playerId && !isClosed(it.id, target) }

    fun opponentPoints(playerId: Int): Int =
        players.filter { it.id != playerId }.maxOfOrNull { pointsFor(it.id) } ?: 0

    fun canWin(playerId: Int): Boolean =
        allClosed(playerId) && pointsFor(playerId) >= opponentPoints(playerId)
}

sealed interface CricketEvent {
    data class HitMissed(val playerId: Int) : CricketEvent

    data class MarksAdded(
        val playerId: Int,
        val target: Int,
        val marks: Int,
        val total: Int,
    ) : CricketEvent

    data class TargetClosed(val playerId: Int, val target: Int) : CricketEvent

    data class PointsScored(
        val playerId: Int,
        val target: Int,
        val points: Int,
        val total: Int,
    ) : CricketEvent

    data class TurnChanged(val playerId: Int, val turnNumber: Int) : CricketEvent

    data class GameFinished(val winnerId: Int) : CricketEvent
}

data class CricketReduction(val state: CricketState, val events: List<CricketEvent>)

/**
 * Cricket, also known as Tactics.
 *
 * The targets are 20 through 15 plus the bull. A single adds one mark, a double two,
 * a treble three; the outer bull adds one and the inner bull two. Three marks close a
 * target for that player. Once a player has closed a target, further hits on it score
 * its segment value while at least one opponent still has it open: 20 for a single 20,
 * 40 for a double, 60 for a treble; 25 for the outer bull and 50 for the inner bull.
 * A player wins when every target is closed and their points are equal to or higher
 * than every opponent. A player behind on points keeps scoring until they take the lead.
 */
object CricketMode {
    /** Standard Cricket targets, highest first with the bull last. */
    val TARGETS: List<Int> = listOf(20, 19, 18, 17, 16, 15, BoardGeometry.BULL)

    const val MARKS_TO_CLOSE = 3

    fun newGame(
        players: List<Player>,
        rules: CricketRules = CricketRules(),
    ): CricketState {
        require(players.size in 1..6) { "Cricket needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return CricketState(
            players = players,
            rules = rules,
            marks = players.associate { player ->
                player.id to TARGETS.associateWith { 0 }
            },
            points = players.associate { it.id to 0 },
            dartsByPlayer = players.associate { it.id to 0 },
            dartsLeft = rules.dartsPerTurn,
        )
    }

    /** Marks added by a hit: 1/2/3 for single/double/treble, 1/2 for outer/inner bull. */
    fun marksFor(hit: DartHit): Int {
        val number = hit.number ?: return 0
        if (hit.isMiss || number !in TARGETS) return 0
        return when (hit.ring) {
            Ring.DOUBLE -> 2
            Ring.TREBLE -> 3
            Ring.INNER_BULL -> 2
            Ring.OUTER_BULL, Ring.SINGLE -> 1
            else -> hit.multiplier.coerceIn(1, 3)
        }
    }

    /** Points a scoring hit is worth, including 25 for the outer bull and 50 for the inner. */
    fun valueFor(hit: DartHit): Int {
        val number = hit.number ?: return 0
        if (hit.isMiss || number !in TARGETS) return 0
        return when (hit.ring) {
            Ring.DOUBLE -> number * 2
            Ring.TREBLE -> number * 3
            Ring.INNER_BULL -> 50
            Ring.OUTER_BULL -> 25
            Ring.SINGLE -> number
            else -> number * hit.multiplier.coerceAtLeast(1)
        }
    }

    fun apply(state: CricketState, hit: DartHit): CricketReduction {
        if (state.finished) return CricketReduction(state, emptyList())

        val events = mutableListOf<CricketEvent>()
        val player = state.currentPlayer
        var newMarks = state.marks
        var newPoints = state.points
        val newDartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))

        val target = hit.number?.takeIf { it in TARGETS }
        val hitMarks = marksFor(hit)
        if (target == null || hitMarks == 0) {
            events += CricketEvent.HitMissed(player.id)
        } else {
            val currentMarks = state.marks(player.id, target)
            if (currentMarks < MARKS_TO_CLOSE) {
                val total = (currentMarks + hitMarks).coerceAtMost(MARKS_TO_CLOSE)
                newMarks = newMarks + (player.id to (newMarks[player.id].orEmpty() + (target to total)))
                events += CricketEvent.MarksAdded(player.id, target, total - currentMarks, total)
                if (total >= MARKS_TO_CLOSE) {
                    events += CricketEvent.TargetClosed(player.id, target)
                }
            } else if (state.hasOpenOpponent(player.id, target)) {
                val gained = valueFor(hit)
                if (gained > 0) {
                    val total = state.pointsFor(player.id) + gained
                    newPoints = newPoints + (player.id to total)
                    events += CricketEvent.PointsScored(player.id, target, gained, total)
                }
            }
        }

        val allClosed = TARGETS.all { target ->
            (newMarks[player.id]?.get(target) ?: 0) >= MARKS_TO_CLOSE
        }
        val opponentBest = state.players
            .filter { it.id != player.id }
            .maxOfOrNull { newPoints[it.id] ?: 0 } ?: 0
        val won = allClosed && (newPoints[player.id] ?: 0) >= opponentBest
        if (won) {
            events += CricketEvent.GameFinished(player.id)
            return CricketReduction(
                state.copy(
                    marks = newMarks,
                    points = newPoints,
                    dartsLeft = 0,
                    dartsByPlayer = newDartsByPlayer,
                    dartsThrown = state.dartsThrown + 1,
                    finished = true,
                    winnerId = player.id,
                ),
                events,
            )
        }

        var dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        var nextPlayer = state.currentPlayerIndex
        var turnNumber = state.turnNumber
        if (dartsLeft <= 0) {
            nextPlayer = (nextPlayer + 1) % state.players.size
            if (nextPlayer == 0) turnNumber += 1
            dartsLeft = state.rules.dartsPerTurn
            events += CricketEvent.TurnChanged(state.players[nextPlayer].id, turnNumber)
        }

        return CricketReduction(
            state.copy(
                marks = newMarks,
                points = newPoints,
                currentPlayerIndex = nextPlayer,
                dartsLeft = dartsLeft,
                dartsByPlayer = newDartsByPlayer,
                dartsThrown = state.dartsThrown + 1,
                turnNumber = turnNumber,
            ),
            events,
        )
    }

    /** Ends an Autodarts visit early; the next player starts a fresh turn. */
    fun advanceTurn(state: CricketState): CricketReduction {
        if (state.finished) return CricketReduction(state, emptyList())
        val next = (state.currentPlayerIndex + 1) % state.players.size
        val turnNumber = state.turnNumber + if (next == 0) 1 else 0
        return CricketReduction(
            state.copy(
                currentPlayerIndex = next,
                dartsLeft = state.rules.dartsPerTurn,
                turnNumber = turnNumber,
            ),
            listOf(CricketEvent.TurnChanged(state.players[next].id, turnNumber)),
        )
    }
}
