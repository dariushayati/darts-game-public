package com.darts.core

enum class X01InRule { STRAIGHT, DOUBLE, MASTER }

enum class X01OutRule { STRAIGHT, DOUBLE, MASTER }

/** Rules for X01 / 501. */
data class X01Rules(
    val startingScore: Int = 501,
    val inRule: X01InRule = X01InRule.STRAIGHT,
    val outRule: X01OutRule = X01OutRule.DOUBLE,
    val legsToWin: Int = 1,
) {
    init {
        require(startingScore in STARTING_SCORES) {
            "startingScore must be one of $STARTING_SCORES"
        }
        require(legsToWin in 1..7) { "legsToWin must be 1..7" }
    }

    companion object {
        val STARTING_SCORES: List<Int> = listOf(501, 301, 121)
    }
}

data class X01State(
    val players: List<Player>,
    val rules: X01Rules = X01Rules(),
    val scores: Map<Int, Int> = players.associate { it.id to rules.startingScore },
    val opened: Map<Int, Boolean> = players.associate {
        it.id to (rules.inRule == X01InRule.STRAIGHT)
    },
    val legs: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val legStartPlayerIndex: Int = 0,
    val dartsLeft: Int = X01Mode.DARTS_PER_TURN,
    val turnStartScore: Int = rules.startingScore,
    val turnStartOpened: Boolean = rules.inRule == X01InRule.STRAIGHT,
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsThrown: Int = 0,
    val turnNumber: Int = 1,
    val legNumber: Int = 1,
    val lastLegWinnerId: Int? = null,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun scoreFor(playerId: Int): Int = scores[playerId] ?: rules.startingScore

    fun isOpened(playerId: Int): Boolean =
        opened[playerId] ?: (rules.inRule == X01InRule.STRAIGHT)

    fun legsWon(playerId: Int): Int = legs[playerId] ?: 0
}

sealed interface X01Event {
    data class DartScored(
        val playerId: Int,
        val hit: DartHit,
        val before: Int,
        val after: Int,
        val openedNow: Boolean,
    ) : X01Event

    data class DartMissed(val playerId: Int, val hit: DartHit) : X01Event

    data class NoDoubleIn(val playerId: Int, val hit: DartHit) : X01Event

    data class Bust(val playerId: Int, val score: Int) : X01Event

    data class Checkout(val playerId: Int, val legNumber: Int, val legsWon: Int) : X01Event

    data class LegWon(
        val playerId: Int,
        val legNumber: Int,
        val nextStartingPlayerId: Int,
    ) : X01Event

    data class MatchFinished(val winnerId: Int) : X01Event

    data class TurnChanged(
        val playerId: Int,
        val turnNumber: Int,
        val legNumber: Int,
    ) : X01Event
}

data class X01Reduction(val state: X01State, val events: List<X01Event>)

/**
 * Standard X01 darts, including 501, 301 and 121.
 *
 * Every dart subtracts its value. A player must reach exactly zero, and the final dart
 * must satisfy the configured out rule. Missing the out rule, going below zero, or
 * leaving one when a double or master finish is required is a bust: the score returns
 * to the start of that turn and the remaining darts are lost. Double-in can require the
 * first scoring dart of a turn to be a double; darts before it do not score.
 */
object X01Mode {
    const val DARTS_PER_TURN = 3

    fun validIn(hit: DartHit, rule: X01InRule): Boolean = when (rule) {
        X01InRule.STRAIGHT -> true
        X01InRule.DOUBLE -> isDouble(hit)
        X01InRule.MASTER -> isMaster(hit)
    }

    fun validOut(hit: DartHit, rule: X01OutRule): Boolean = when (rule) {
        X01OutRule.STRAIGHT -> true
        X01OutRule.DOUBLE -> isDouble(hit)
        X01OutRule.MASTER -> isMaster(hit)
    }

    fun isDouble(hit: DartHit): Boolean =
        hit.ring == Ring.DOUBLE || hit.ring == Ring.INNER_BULL

    fun isMaster(hit: DartHit): Boolean =
        hit.ring == Ring.DOUBLE || hit.ring == Ring.TREBLE || hit.ring == Ring.INNER_BULL

    /** Point value including the outer/inner bull, whose Autodarts hits carry multiplier 1. */
    fun valueOf(hit: DartHit): Int = when (hit.ring) {
        Ring.INNER_BULL -> 50
        Ring.OUTER_BULL -> 25
        else -> (hit.number ?: 0) * hit.multiplier
    }

    fun newGame(
        players: List<Player>,
        rules: X01Rules = X01Rules(),
    ): X01State {
        require(players.size in 1..6) { "X01 needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        val opened = players.associate {
            it.id to (rules.inRule == X01InRule.STRAIGHT)
        }
        return X01State(
            players = players,
            rules = rules,
            scores = players.associate { it.id to rules.startingScore },
            opened = opened,
            legs = players.associate { it.id to 0 },
            dartsByPlayer = players.associate { it.id to 0 },
            turnStartScore = rules.startingScore,
            turnStartOpened = rules.inRule == X01InRule.STRAIGHT,
        )
    }

    fun apply(state: X01State, hit: DartHit): X01Reduction {
        if (state.finished) return X01Reduction(state, emptyList())

        val player = state.currentPlayer
        val events = mutableListOf<X01Event>()
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val thrown = state.copy(
            dartsByPlayer = dartsByPlayer,
            dartsThrown = state.dartsThrown + 1,
        )

        if (!state.isOpened(player.id)) {
            if (!hit.isMiss && validIn(hit, state.rules.inRule)) {
                val before = state.scoreFor(player.id)
                val after = before - valueOf(hit)
                events += X01Event.DartScored(player.id, hit, before, after, openedNow = true)
                val opened = state.opened + (player.id to true)
                if (after == 0) {
                    if (!validOut(hit, state.rules.outRule)) {
                        return bust(thrown, events)
                    }
                    return finishLeg(
                        thrown.copy(scores = state.scores + (player.id to 0), opened = opened),
                        events,
                    )
                }
                if (after < 0 || (after == 1 && state.rules.outRule != X01OutRule.STRAIGHT)) {
                    return bust(thrown, events)
                }
                val scored = thrown.copy(
                    scores = state.scores + (player.id to after),
                    opened = opened,
                    dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0),
                )
                return advanceIfNeeded(scored, events)
            }

            events += if (hit.isMiss) {
                X01Event.DartMissed(player.id, hit)
            } else {
                X01Event.NoDoubleIn(player.id, hit)
            }
            val consumed = thrown.copy(dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0))
            return advanceIfNeeded(consumed, events)
        }

        if (hit.isMiss) {
            events += X01Event.DartMissed(player.id, hit)
            val consumed = thrown.copy(dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0))
            return advanceIfNeeded(consumed, events)
        }

        val before = state.scoreFor(player.id)
        val after = before - valueOf(hit)
        if (after < 0 || (after == 1 && state.rules.outRule != X01OutRule.STRAIGHT)) {
            events += X01Event.DartScored(player.id, hit, before, after, openedNow = false)
            return bust(thrown, events)
        }
        if (after == 0) {
            events += X01Event.DartScored(player.id, hit, before, after, openedNow = false)
            if (!validOut(hit, state.rules.outRule)) {
                return bust(thrown, events)
            }
            return finishLeg(
                thrown.copy(
                    scores = state.scores + (player.id to 0),
                    dartsLeft = 0,
                ),
                events,
            )
        }

        events += X01Event.DartScored(player.id, hit, before, after, openedNow = false)
        val scored = thrown.copy(
            scores = state.scores + (player.id to after),
            dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0),
        )
        return advanceIfNeeded(scored, events)
    }

    /** Ends a visit early (Autodarts pulled darts); the next player starts a fresh turn. */
    fun advanceTurn(state: X01State): X01Reduction {
        if (state.finished) return X01Reduction(state, emptyList())
        return X01Reduction(passTurn(state, mutableListOf()), emptyList())
    }

    private fun advanceIfNeeded(
        state: X01State,
        events: MutableList<X01Event>,
    ): X01Reduction {
        if (state.dartsLeft > 0) return X01Reduction(state, events)
        return X01Reduction(passTurn(state, events), events)
    }

    private fun passTurn(state: X01State, events: MutableList<X01Event>): X01State {
        val next = (state.currentPlayerIndex + 1) % state.players.size
        val turnNumber = state.turnNumber + if (next == 0) 1 else 0
        val nextPlayer = state.players[next]
        events += X01Event.TurnChanged(nextPlayer.id, turnNumber, state.legNumber)
        return state.copy(
            currentPlayerIndex = next,
            dartsLeft = DARTS_PER_TURN,
            turnNumber = turnNumber,
            turnStartScore = state.scoreFor(nextPlayer.id),
            turnStartOpened = state.isOpened(nextPlayer.id),
        )
    }

    private fun bust(state: X01State, events: MutableList<X01Event>): X01Reduction {
        val player = state.currentPlayer
        val reverted = state.copy(
            scores = state.scores + (player.id to state.turnStartScore),
            opened = state.opened + (player.id to state.turnStartOpened),
            dartsLeft = 0,
        )
        events += X01Event.Bust(player.id, state.turnStartScore)
        return X01Reduction(passTurn(reverted, events), events)
    }

    private fun finishLeg(state: X01State, events: MutableList<X01Event>): X01Reduction {
        val player = state.currentPlayer
        val legs = state.legs + (player.id to (state.legsWon(player.id) + 1))
        val wonLegs = legs.getValue(player.id)
        events += X01Event.Checkout(player.id, state.legNumber, wonLegs)

        if (wonLegs >= state.rules.legsToWin) {
            events += X01Event.MatchFinished(player.id)
            return X01Reduction(
                state.copy(
                    scores = state.scores + (player.id to 0),
                    legs = legs,
                    dartsLeft = 0,
                    lastLegWinnerId = player.id,
                    finished = true,
                    winnerId = player.id,
                ),
                events,
            )
        }

        val nextStart = (state.legStartPlayerIndex + 1) % state.players.size
        val nextStartingPlayer = state.players[nextStart]
        events += X01Event.LegWon(player.id, state.legNumber, nextStartingPlayer.id)
        val opened = state.players.associate {
            it.id to (state.rules.inRule == X01InRule.STRAIGHT)
        }
        return X01Reduction(
            state.copy(
                scores = state.players.associate { it.id to state.rules.startingScore },
                opened = opened,
                legs = legs,
                currentPlayerIndex = nextStart,
                legStartPlayerIndex = nextStart,
                dartsLeft = DARTS_PER_TURN,
                turnStartScore = state.rules.startingScore,
                turnStartOpened = state.rules.inRule == X01InRule.STRAIGHT,
                turnNumber = 1,
                legNumber = state.legNumber + 1,
                lastLegWinnerId = player.id,
            ),
            events,
        )
    }
}
