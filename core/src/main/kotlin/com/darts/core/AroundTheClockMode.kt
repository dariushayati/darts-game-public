package com.darts.core

/**
 * Around the Clock is a 1–6 player training race. Players hit 1 through 20 in order,
 * then finish on the bull. Any single, double or treble on the current number counts
 * as one step; the bull counts when it is the final target. Every turn is three darts.
 */
data class AroundTheClockState(
    val players: List<Player>,
    val progress: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = AroundTheClockMode.DARTS_PER_TURN,
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val dartsThrown: Int = 0,
    val turnNumber: Int = 1,
    val finished: Boolean = false,
    val winnerId: Int? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]

    fun progressFor(playerId: Int): Int = progress[playerId] ?: 0

    fun targetFor(playerId: Int): Int? = AroundTheClockMode.targetAt(progressFor(playerId))

    /** The number the current player needs next, or null once they have finished. */
    val target: Int? get() = targetFor(currentPlayer.id)

    fun labelFor(playerId: Int): String =
        AroundTheClockMode.labelFor(targetFor(playerId))

    val completedTargets: Int
        get() = progressFor(currentPlayer.id)
}

sealed interface AroundTheClockEvent {
    data class DartThrown(
        val playerId: Int,
        val hit: DartHit,
        val targetHit: Boolean,
    ) : AroundTheClockEvent

    data class TargetHit(
        val playerId: Int,
        val target: Int,
        val completed: Int,
    ) : AroundTheClockEvent

    data class TurnChanged(val playerId: Int) : AroundTheClockEvent

    data class GameFinished(val winnerId: Int) : AroundTheClockEvent
}

data class AroundTheClockReduction(
    val state: AroundTheClockState,
    val events: List<AroundTheClockEvent>,
)

object AroundTheClockMode {
    const val DARTS_PER_TURN = 3

    /** 1 through 20, then the bull. */
    val TARGETS: List<Int> = (1..20).toList() + BoardGeometry.BULL

    fun targetAt(index: Int): Int? = TARGETS.getOrNull(index)

    fun labelFor(target: Int?): String = when (target) {
        BoardGeometry.BULL -> "BULL"
        null -> "DONE"
        else -> target.toString()
    }

    fun matches(hit: DartHit, target: Int): Boolean {
        if (hit.isMiss) return false
        return if (target == BoardGeometry.BULL) {
            hit.number == BoardGeometry.BULL
        } else {
            hit.number == target
        }
    }

    fun newGame(players: List<Player>): AroundTheClockState {
        require(players.size in 1..6) {
            "Around the Clock needs 1 to 6 players, got ${players.size}"
        }
        require(players.map { it.id }.toSet().size == players.size) {
            "player ids must be unique"
        }
        return AroundTheClockState(
            players = players,
            progress = players.associate { it.id to 0 },
            dartsByPlayer = players.associate { it.id to 0 },
        )
    }

    fun apply(state: AroundTheClockState, hit: DartHit): AroundTheClockReduction {
        if (state.finished) return AroundTheClockReduction(state, emptyList())

        val player = state.currentPlayer
        val target = state.target
        val targetHit = target != null && matches(hit, target)
        val completed = state.progressFor(player.id) + if (targetHit) 1 else 0
        val progress = state.progress + (player.id to completed)
        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        val events = mutableListOf<AroundTheClockEvent>(
            AroundTheClockEvent.DartThrown(player.id, hit, targetHit),
        )
        if (targetHit && target != null) {
            events += AroundTheClockEvent.TargetHit(player.id, target, completed)
        }

        val thrown = state.copy(
            progress = progress,
            dartsByPlayer = dartsByPlayer,
            dartsThrown = state.dartsThrown + 1,
        )

        if (targetHit && completed >= TARGETS.size) {
            events += AroundTheClockEvent.GameFinished(player.id)
            return AroundTheClockReduction(
                thrown.copy(
                    dartsLeft = 0,
                    finished = true,
                    winnerId = player.id,
                ),
                events,
            )
        }

        val dartsLeft = (state.dartsLeft - 1).coerceAtLeast(0)
        if (dartsLeft > 0) {
            return AroundTheClockReduction(thrown.copy(dartsLeft = dartsLeft), events)
        }
        return AroundTheClockReduction(passTurn(thrown, events), events)
    }

    /** Ends an Autodarts visit early; the next player starts a fresh turn. */
    fun advanceTurn(state: AroundTheClockState): AroundTheClockReduction {
        if (state.finished) return AroundTheClockReduction(state, emptyList())
        val events = mutableListOf<AroundTheClockEvent>()
        return AroundTheClockReduction(passTurn(state, events), events)
    }

    private fun passTurn(
        state: AroundTheClockState,
        events: MutableList<AroundTheClockEvent>,
    ): AroundTheClockState {
        val next = (state.currentPlayerIndex + 1) % state.players.size
        val turnNumber = state.turnNumber + if (next == 0) 1 else 0
        events += AroundTheClockEvent.TurnChanged(state.players[next].id)
        return state.copy(
            currentPlayerIndex = next,
            dartsLeft = DARTS_PER_TURN,
            turnNumber = turnNumber,
        )
    }
}
