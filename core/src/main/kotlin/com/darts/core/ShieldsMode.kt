package com.darts.core

data class Player(
    val id: Int,
    val name: String,
    val colorIndex: Int,
)

/**
 * Tunable rules for a Shields match. [dartsPerPlayer] of 0 means no dart limit.
 * The level values are shields added by each ring; every dart costs one go from
 * [dartsPerPlayer], so doubles and trebles are bonuses. Every turn is three darts.
 */
data class GameRules(
    val dartsPerPlayer: Int = 15,
    val endOnBoardFull: Boolean = true,
    val singleLevels: Int = 1,
    val doubleLevels: Int = 2,
    val tripleLevels: Int = 3,
) {
    /** A treble adds [tripleLevels] and locks the sector at that level. */
    val shieldsToLock: Int get() = tripleLevels

    init {
        require(dartsPerPlayer >= 0) { "dartsPerPlayer must be >= 0" }
        require(dartsPerPlayer % 3 == 0) { "dartsPerPlayer must be a multiple of 3" }
        require(singleLevels in 1..3) { "singleLevels must be 1..3" }
        require(doubleLevels in 1..4) { "doubleLevels must be 1..4" }
        require(tripleLevels in 2..6) { "tripleLevels must be 2..6" }
    }
}

/**
 * One claimable target: a number 1..20 or the bull (25).
 * [locked] uses the default threshold; the active rules may differ (see [lockedAt]).
 */
data class SectorState(
    val ownerId: Int? = null,
    val shields: Int = 0,
) {
    fun lockedAt(threshold: Int): Boolean = ownerId != null && shields >= threshold
    val locked: Boolean get() = lockedAt(LOCK_SHIELDS)

    companion object {
        const val LOCK_SHIELDS = 3
    }
}

/** Events emitted by a reduction. The UI animates from these, not from state diffs. */
sealed interface GameEvent {
    data class ShieldAdded(val target: Int, val playerId: Int, val shields: Int) : GameEvent
    data class ShieldRemoved(val target: Int, val ownerId: Int, val shields: Int) : GameEvent
    data class SectorLocked(val target: Int, val playerId: Int) : GameEvent
    data class SectorNeutralised(val target: Int, val previousOwnerId: Int) : GameEvent
    data class BlockedByLock(val target: Int) : GameEvent
    data class HitMissed(val playerId: Int) : GameEvent
    data class TurnChanged(val playerId: Int, val turnNumber: Int) : GameEvent
    data class GameFinished(val winnerId: Int?) : GameEvent
}

data class GameState(
    val players: List<Player>,
    val sectors: Map<Int, SectorState>,
    val rules: GameRules = GameRules(),
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = DARTS_PER_TURN,
    val dartsThrown: Int = 0,
    val turnNumber: Int = 1,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    companion object {
        const val DARTS_PER_TURN = 3
    }

    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun sector(target: Int): SectorState = sectors[target] ?: SectorState()

    fun isLocked(target: Int): Boolean {
        val state = sector(target)
        return state.ownerId != null && state.shields >= rules.shieldsToLock
    }

    fun boardFull(): Boolean =
        sectors.values.all { it.ownerId != null && it.shields >= rules.shieldsToLock }
}

data class Reduction(val state: GameState, val events: List<GameEvent>)

fun GameState.lockedCount(playerId: Int): Int =
    sectors.values.count { it.ownerId == playerId && it.shields >= rules.shieldsToLock }

fun GameState.ownedCount(playerId: Int): Int =
    sectors.values.count { it.ownerId == playerId }

/** Every claimed number is one point, whether it has one shield or is locked with three. */
fun GameState.score(playerId: Int): Int = ownedCount(playerId)

/**
 * Shields mode.
 *
 * Hit a neutral target: claim it with `multiplier` shields (capped at the lock threshold).
 * Hit your own: add `multiplier` shields, capped, then lock. Hit an opponent's: remove
 * `multiplier` shields, floored at zero; at zero it goes neutral. Locked targets are permanent.
 *
 * The bull is one target: inner and outer both add one shield. Every dart costs one go,
 * whether it is a single, double or treble, so multipliers are a bonus: a double adds two
 * shields and a treble adds three for the same single go. The match
 * ends when the configured dart budget is spent (if any) and/or when every target is locked
 * (if [GameRules.endOnBoardFull]). Each claimed number is one point regardless of its shield
 * count; the winner has the most points, with locked sectors then shield totals as tiebreaks.
 */
object ShieldsMode {
    fun newGame(players: List<Player>, rules: GameRules = GameRules()): GameState {
        require(players.size in 1..6) { "Shields needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return GameState(
            players = players,
            sectors = BoardGeometry.TARGETS.associateWith { SectorState() },
            rules = rules,
            dartsByPlayer = players.associate { it.id to 0 },
            dartsLeft = GameState.DARTS_PER_TURN,
        )
    }

    fun apply(state: GameState, hit: DartHit): Reduction {
        if (state.finished) return Reduction(state, emptyList())

        val events = mutableListOf<GameEvent>()
        val player = state.currentPlayer
        val sectors = state.sectors.toMutableMap()
        val lock = state.rules.shieldsToLock
        // One go per dart; the multiplier only decides how many shields it adds.
        val cost = 1

        if (hit.isMiss || hit.number == null) {
            events += GameEvent.HitMissed(player.id)
        } else {
            val target = hit.number
            val multiplier = when (hit.ring) {
                Ring.DOUBLE -> state.rules.doubleLevels
                Ring.TREBLE -> state.rules.tripleLevels
                Ring.INNER_BULL, Ring.OUTER_BULL -> 1
                else -> state.rules.singleLevels
            }.coerceAtLeast(1)
            val current = sectors[target] ?: SectorState()
            when {
                current.lockedAt(lock) -> events += GameEvent.BlockedByLock(target)

                current.ownerId == null -> {
                    val shields = multiplier.coerceAtMost(lock)
                    sectors[target] = SectorState(player.id, shields)
                    events += GameEvent.ShieldAdded(target, player.id, shields)
                    if (shields >= lock) events += GameEvent.SectorLocked(target, player.id)
                }

                current.ownerId == player.id -> {
                    val shields = (current.shields + multiplier).coerceAtMost(lock)
                    sectors[target] = current.copy(shields = shields)
                    events += GameEvent.ShieldAdded(target, player.id, shields)
                    if (shields >= lock && !current.lockedAt(lock)) {
                        events += GameEvent.SectorLocked(target, player.id)
                    }
                }

                else -> {
                    val owner = checkNotNull(current.ownerId)
                    val remaining = (current.shields - multiplier).coerceAtLeast(0)
                    if (remaining == 0) {
                        sectors[target] = SectorState()
                        events += GameEvent.SectorNeutralised(target, owner)
                    } else {
                        sectors[target] = current.copy(shields = remaining)
                        events += GameEvent.ShieldRemoved(target, owner, remaining)
                    }
                }
            }
        }

        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + cost))
        val dartsThrown = state.dartsThrown + cost
        var dartsLeft = (state.dartsLeft - cost).coerceAtLeast(0)
        var nextPlayer = state.currentPlayerIndex
        var turnNumber = state.turnNumber
        if (dartsLeft <= 0) {
            nextPlayer = (state.currentPlayerIndex + 1) % state.players.size
            if (nextPlayer == 0) turnNumber += 1
            dartsLeft = GameState.DARTS_PER_TURN
            events += GameEvent.TurnChanged(state.players[nextPlayer].id, turnNumber)
        }

        val newSectors = sectors.toMap()
        val boardFull = newSectors.values.all { it.ownerId != null && it.shields >= lock }
        val allPlayersDone = state.rules.dartsPerPlayer > 0 &&
            state.players.all { (dartsByPlayer[it.id] ?: 0) >= state.rules.dartsPerPlayer }
        val finished = (state.rules.endOnBoardFull && boardFull) || allPlayersDone
        val winnerId = if (finished) winner(newSectors, state.players, lock) else null
        if (finished) events += GameEvent.GameFinished(winnerId)

        val next = state.copy(
            sectors = newSectors,
            dartsByPlayer = dartsByPlayer,
            currentPlayerIndex = nextPlayer,
            dartsLeft = dartsLeft,
            dartsThrown = dartsThrown,
            turnNumber = turnNumber,
            finished = finished,
            winnerId = winnerId,
        )
        return Reduction(next, events)
    }

    /** Ends the current visit without applying a dart (used when Autodarts ends a turn early). */
    fun advanceTurn(state: GameState): Reduction {
        if (state.finished) return Reduction(state, emptyList())
        val next = (state.currentPlayerIndex + 1) % state.players.size
        val turnNumber = if (next == 0) state.turnNumber + 1 else state.turnNumber
        val events = listOf(GameEvent.TurnChanged(state.players[next].id, turnNumber))
        return Reduction(
            state.copy(
                currentPlayerIndex = next,
                dartsLeft = GameState.DARTS_PER_TURN,
                turnNumber = turnNumber,
            ),
            events,
        )
    }

    fun winner(
        sectors: Map<Int, SectorState>,
        players: List<Player>,
        shieldsToLock: Int = SectorState.LOCK_SHIELDS,
    ): Int? {
        val stats = players.map { player ->
            val owned = sectors.values.filter { it.ownerId == player.id }
            Triple(
                owned.count { it.shields > 0 },
                owned.count { it.shields >= shieldsToLock },
                owned.sumOf { it.shields },
            )
        }
        val comparator = compareBy<Triple<Int, Int, Int>>({ it.first }, { it.second }, { it.third })
        val best = stats.maxWith(comparator)
        val ties = stats.count { comparator.compare(it, best) == 0 }
        return if (ties > 1) null else players[stats.indexOf(best)].id
    }
}
