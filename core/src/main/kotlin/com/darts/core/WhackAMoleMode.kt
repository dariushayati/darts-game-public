package com.darts.core

import kotlin.random.Random

/**
 * Rules for Whack a Mole. A random ordered list of [moles] numbers is generated;
 * every player must whack them in order. Each player throws [dartsPerTurn] darts
 * before the next player. The first to clear the whole list wins.
 *
 * In [hardMode] one or two of the numbers become ring-specific: they only count
 * when hit in the double or treble ring.
 */
data class WhackAMoleRules(
    val moles: Int = 8,
    val dartsPerTurn: Int = 3,
    val hardMode: Boolean = false,
) {
    init {
        require(moles in 1..20) { "moles must be 1..20" }
        require(dartsPerTurn in 1..10) { "dartsPerTurn must be 1..10" }
    }
}

/**
 * A single mole: a [number] plus an optional required [ring]. When [ring] is null
 * any hit on the number (single, double or treble) whacks it; when it is DOUBLE or
 * TREBLE the player must land in that exact ring.
 */
data class MoleTarget(
    val number: Int,
    val ring: Ring? = null,
) {
    /** True when a dart is on this mole's number; false for the bull or a miss. */
    val isNumber: Boolean get() = number != BoardGeometry.BULL

    /** Short UI label such as "20", "D20" or "T20". */
    val label: String
        get() = when (ring) {
            Ring.DOUBLE -> "D$number"
            Ring.TREBLE -> "T$number"
            else -> "$number"
        }

    /** True when the hit whacks this mole (correct number, and ring if required). */
    fun matches(hit: DartHit): Boolean {
        if (hit.isMiss) return false
        val hitNumber = hit.number ?: return false
        if (hitNumber != number) return false
        return ring == null || hit.ring == ring
    }
}

data class WhackAMoleState(
    val players: List<Player>,
    val rules: WhackAMoleRules = WhackAMoleRules(),
    val targets: List<MoleTarget> = emptyList(),
    val progress: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = rules.dartsPerTurn,
    val dartsThrown: Int = 0,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val moles: Int get() = rules.moles

    /** The mole the current player must hit next; null once they are done. */
    val target: MoleTarget? get() = targets.getOrNull(progress[currentPlayer.id] ?: 0)

    /** True when the current target is the final mole on the list. */
    val isLastMole: Boolean get() = target != null && (progress[currentPlayer.id] ?: 0) == targets.lastIndex

    fun whacked(playerId: Int): Int = progress[playerId] ?: 0
    fun left(playerId: Int): Int = (rules.moles - whacked(playerId)).coerceAtLeast(0)
}

sealed interface WhackAMoleEvent {
    data class DartThrown(val playerId: Int, val hit: DartHit, val whacked: Boolean) : WhackAMoleEvent
    data class MoleWhacked(val playerId: Int, val number: Int, val index: Int) : WhackAMoleEvent
    data class TurnChanged(val playerId: Int) : WhackAMoleEvent
    data class GameFinished(val winnerId: Int) : WhackAMoleEvent
}

data class WhackAMoleReduction(val state: WhackAMoleState, val events: List<WhackAMoleEvent>)

/**
 * Whack a Mole is a 1–6 player race. Every player works through the same randomly
 * generated, ordered list of moles and must hit them in sequence. Normally any hit
 * on the number counts; the bull never counts because it is not on the list. In
 * [WhackAMoleRules.hardMode] one or two moles are ring-specific and only count in
 * the double or treble band. Wrong numbers and misses cost a dart but carry no
 * penalty. Every player throws [WhackAMoleRules.dartsPerTurn] darts before the turn
 * passes on. The first player to clear the whole list wins.
 */
object WhackAMoleMode {
    /** The candidate numbers a mole can be; the bull is excluded. */
    val NUMBERS: List<Int> = (1..20).toList()

    /** Hard mode plants between 1 and 2 ring-specific moles (capped by the list size). */
    private const val HARD_MIN = 1
    private const val HARD_MAX = 2

    fun generateTargets(
        moles: Int,
        hardMode: Boolean = false,
        random: Random = Random.Default,
    ): List<MoleTarget> {
        val numbers = NUMBERS.shuffled(random).take(moles)
        if (!hardMode) return numbers.map { MoleTarget(it) }
        val specialCount = minOf(moles, HARD_MIN + random.nextInt(HARD_MAX - HARD_MIN + 1))
        val special = numbers.indices.shuffled(random).take(specialCount).toSet()
        return numbers.mapIndexed { index, number ->
            if (index in special) {
                val ring = if (random.nextBoolean()) Ring.DOUBLE else Ring.TREBLE
                MoleTarget(number, ring)
            } else {
                MoleTarget(number)
            }
        }
    }

    fun newGame(
        players: List<Player>,
        rules: WhackAMoleRules = WhackAMoleRules(),
        random: Random = Random.Default,
    ): WhackAMoleState {
        require(players.size in 1..6) { "Whack a Mole needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return WhackAMoleState(
            players = players,
            rules = rules,
            targets = generateTargets(rules.moles, rules.hardMode, random),
            progress = players.associate { it.id to 0 },
            dartsLeft = rules.dartsPerTurn,
        )
    }

    fun apply(state: WhackAMoleState, hit: DartHit): WhackAMoleReduction {
        if (state.finished) return WhackAMoleReduction(state, emptyList())

        val player = state.currentPlayer
        val index = state.progress[player.id] ?: 0
        val target = state.targets.getOrNull(index)
        val whackedTarget = target?.takeIf { it.matches(hit) }
        val whacked = whackedTarget != null

        val events = mutableListOf<WhackAMoleEvent>(WhackAMoleEvent.DartThrown(player.id, hit, whacked))

        val newWhacked = index + if (whacked) 1 else 0
        val progress = state.progress + (player.id to newWhacked)
        if (whackedTarget != null) {
            events += WhackAMoleEvent.MoleWhacked(player.id, whackedTarget.number, index)
        }

        val completed = whacked && newWhacked >= state.rules.moles
        if (completed) {
            events += WhackAMoleEvent.GameFinished(player.id)
            return WhackAMoleReduction(
                state.copy(
                    progress = progress,
                    dartsLeft = 0,
                    dartsThrown = state.dartsThrown + 1,
                    finished = true,
                    winnerId = player.id,
                ),
                events,
            )
        }

        var dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        var currentPlayerIndex = state.currentPlayerIndex
        if (dartsLeft <= 0) {
            currentPlayerIndex = (state.currentPlayerIndex + 1) % state.players.size
            dartsLeft = state.rules.dartsPerTurn
            events += WhackAMoleEvent.TurnChanged(state.players[currentPlayerIndex].id)
        }

        return WhackAMoleReduction(
            state.copy(
                progress = progress,
                currentPlayerIndex = currentPlayerIndex,
                dartsLeft = dartsLeft,
                dartsThrown = state.dartsThrown + 1,
            ),
            events,
        )
    }

    /** Ends an Autodarts visit early; the next player starts a fresh turn. */
    fun advanceTurn(state: WhackAMoleState): WhackAMoleReduction {
        if (state.finished) return WhackAMoleReduction(state, emptyList())
        val next = (state.currentPlayerIndex + 1) % state.players.size
        return WhackAMoleReduction(
            state.copy(
                currentPlayerIndex = next,
                dartsLeft = state.rules.dartsPerTurn,
            ),
            listOf(WhackAMoleEvent.TurnChanged(state.players[next].id)),
        )
    }

    /** True when [hit] whacks the current player's next mole. */
    fun isWhack(state: WhackAMoleState, hit: DartHit): Boolean =
        state.target?.matches(hit) == true
}
