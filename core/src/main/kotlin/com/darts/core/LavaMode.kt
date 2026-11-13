package com.darts.core

import kotlin.random.Random

/** Rules for The Board Is Lava. A round is one turn for every player still in the game. */
data class LavaRules(
    val lives: Int = 3,
    val dartsPerTurn: Int = 3,
    val lavaPerRound: Int = 3,
) {
    init {
        require(lives in 1..9) { "lives must be 1..9" }
        require(dartsPerTurn in 1..10) { "dartsPerTurn must be 1..10" }
        require(lavaPerRound in 1..20) { "lavaPerRound must be 1..20" }
    }
}

enum class LavaFinishReason { LAST_STANDING, OUT_OF_LIVES, BOARD_CONSUMED }

data class LavaState(
    val players: List<Player>,
    val rules: LavaRules,
    val lavaNumbers: Set<Int> = emptySet(),
    val livesByPlayer: Map<Int, Int> = players.associate { it.id to rules.lives },
    val eliminatedOrder: List<Int> = emptyList(),
    val dartsByPlayer: Map<Int, Int> = players.associate { it.id to 0 },
    val currentPlayerIndex: Int = 0,
    val dartsLeft: Int = rules.dartsPerTurn,
    val roundNumber: Int = 1,
    val lastBurned: List<Int> = emptyList(),
    val finished: Boolean = false,
    val winnerId: Int? = null,
    val finishReason: LavaFinishReason? = null,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
    val alivePlayers: List<Player> get() = players.filter { (livesByPlayer[it.id] ?: 0) > 0 }
    val numbersLeft: Int get() = NUMBERS.size - lavaNumbers.size
    val allLava: Boolean get() = lavaNumbers.size >= NUMBERS.size
    val dartsThrown: Int get() = dartsByPlayer.values.sum()

    companion object {
        val NUMBERS: List<Int> = (1..20).toList()
    }
}

sealed interface LavaEvent {
    data class DartUsed(val hit: DartHit, val lava: Boolean) : LavaEvent
    data class LifeLost(val playerId: Int, val livesLeft: Int) : LavaEvent
    data class PlayerOut(val playerId: Int) : LavaEvent
    data class LavaBurned(val numbers: List<Int>) : LavaEvent
    data class TurnChanged(val playerId: Int, val roundNumber: Int) : LavaEvent
    data class RoundChanged(val roundNumber: Int) : LavaEvent
    data class GameFinished(val winnerId: Int?) : LavaEvent
}

data class LavaReduction(val state: LavaState, val events: List<LavaEvent>)

object LavaMode {
    fun newGame(
        players: List<Player>,
        rules: LavaRules = LavaRules(),
        random: Random = Random.Default,
    ): LavaState {
        require(players.size in 1..6) { "Lava needs 1 to 6 players, got ${players.size}" }
        require(players.map { it.id }.toSet().size == players.size) { "player ids must be unique" }
        return LavaState(
            players = players,
            rules = rules,
            lavaNumbers = burn(rules.lavaPerRound, emptySet(), random).toSet(),
        )
    }

    fun apply(
        state: LavaState,
        hit: DartHit,
        random: Random = Random.Default,
    ): LavaReduction {
        if (state.finished) return LavaReduction(state, emptyList())

        val events = mutableListOf<LavaEvent>()
        val player = state.currentPlayer
        val number = hit.number
        val isLava = number != null && number in state.lavaNumbers
        val isMiss = hit.isMiss || number == null
        events += LavaEvent.DartUsed(hit, isLava)

        val dartsByPlayer = state.dartsByPlayer +
            (player.id to ((state.dartsByPlayer[player.id] ?: 0) + 1))
        var lives = state.livesByPlayer
        var eliminatedOrder = state.eliminatedOrder
        var dartsLeft = state.dartsLeft

        if (isLava || isMiss) {
            val left = ((lives[player.id] ?: 0) - 1).coerceAtLeast(0)
            lives = lives + (player.id to left)
            events += LavaEvent.LifeLost(player.id, left)
            if (left == 0) {
                eliminatedOrder = eliminatedOrder + player.id
                events += LavaEvent.PlayerOut(player.id)
                // An eliminated player cannot keep throwing this turn.
                dartsLeft = 0
            }
        }

        val alive = state.players.filter { (lives[it.id] ?: 0) > 0 }
        val gameOver = if (state.players.size == 1) {
            alive.isEmpty()
        } else {
            alive.size <= 1
        }
        if (gameOver) {
            val winnerId = if (state.players.size > 1 && alive.size == 1) alive.first().id else null
            val finishReason = if (state.players.size > 1 && alive.size == 1) {
                LavaFinishReason.LAST_STANDING
            } else {
                LavaFinishReason.OUT_OF_LIVES
            }
            events += LavaEvent.GameFinished(winnerId)
            return LavaReduction(
                state.copy(
                    livesByPlayer = lives,
                    eliminatedOrder = eliminatedOrder,
                    dartsByPlayer = dartsByPlayer,
                    dartsLeft = 0,
                    lastBurned = emptyList(),
                    finished = true,
                    winnerId = winnerId,
                    finishReason = finishReason,
                ),
                events,
            )
        }

        var currentPlayerIndex = state.currentPlayerIndex
        var roundNumber = state.roundNumber
        var lavaNumbers = state.lavaNumbers
        var lastBurned = emptyList<Int>()

        dartsLeft = (dartsLeft - 1).coerceAtLeast(0)
        if (dartsLeft <= 0) {
            val next = nextAliveIndex(state.players, lives, currentPlayerIndex)
            val wrapped = next <= currentPlayerIndex
            currentPlayerIndex = next
            dartsLeft = state.rules.dartsPerTurn
            if (wrapped) {
                roundNumber += 1
                val burned = burn(state.rules.lavaPerRound, lavaNumbers, random)
                if (burned.isNotEmpty()) {
                    lavaNumbers = lavaNumbers + burned
                    lastBurned = burned
                    events += LavaEvent.LavaBurned(burned)
                }
                events += LavaEvent.RoundChanged(roundNumber)
            }
            events += LavaEvent.TurnChanged(state.players[currentPlayerIndex].id, roundNumber)
        }

        if (lavaNumbers.size >= LavaState.NUMBERS.size) {
            val winnerId = mostLivesWinner(state.players, lives)
            events += LavaEvent.GameFinished(winnerId)
            return LavaReduction(
                state.copy(
                    lavaNumbers = lavaNumbers,
                    livesByPlayer = lives,
                    eliminatedOrder = eliminatedOrder,
                    dartsByPlayer = dartsByPlayer,
                    currentPlayerIndex = currentPlayerIndex,
                    dartsLeft = 0,
                    roundNumber = roundNumber,
                    lastBurned = lastBurned,
                    finished = true,
                    winnerId = winnerId,
                    finishReason = LavaFinishReason.BOARD_CONSUMED,
                ),
                events,
            )
        }

        val next = state.copy(
            lavaNumbers = lavaNumbers,
            livesByPlayer = lives,
            eliminatedOrder = eliminatedOrder,
            dartsByPlayer = dartsByPlayer,
            currentPlayerIndex = currentPlayerIndex,
            dartsLeft = dartsLeft,
            roundNumber = roundNumber,
            lastBurned = lastBurned,
        )
        return LavaReduction(next, events)
    }

    /** Ends an Autodarts visit early; the next live player starts a fresh turn. */
    fun advanceTurn(
        state: LavaState,
        random: Random = Random.Default,
    ): LavaReduction {
        if (state.finished) return LavaReduction(state, emptyList())

        val events = mutableListOf<LavaEvent>()
        val next = nextAliveIndex(state.players, state.livesByPlayer, state.currentPlayerIndex)
        val wrapped = next <= state.currentPlayerIndex
        var roundNumber = state.roundNumber
        var lavaNumbers = state.lavaNumbers
        var lastBurned = emptyList<Int>()

        if (wrapped) {
            roundNumber += 1
            val burned = burn(state.rules.lavaPerRound, lavaNumbers, random)
            if (burned.isNotEmpty()) {
                lavaNumbers = lavaNumbers + burned
                lastBurned = burned
                events += LavaEvent.LavaBurned(burned)
            }
            events += LavaEvent.RoundChanged(roundNumber)
        }
        events += LavaEvent.TurnChanged(state.players[next].id, roundNumber)

        val boardConsumed = lavaNumbers.size >= LavaState.NUMBERS.size
        val winnerId = if (boardConsumed) {
            mostLivesWinner(state.players, state.livesByPlayer)
        } else {
            null
        }
        if (boardConsumed) events += LavaEvent.GameFinished(winnerId)

        return LavaReduction(
            state.copy(
                lavaNumbers = lavaNumbers,
                currentPlayerIndex = next,
                dartsLeft = if (boardConsumed) 0 else state.rules.dartsPerTurn,
                roundNumber = roundNumber,
                lastBurned = lastBurned,
                finished = boardConsumed,
                winnerId = winnerId,
                finishReason = if (boardConsumed) LavaFinishReason.BOARD_CONSUMED else null,
            ),
            events,
        )
    }

    private fun mostLivesWinner(players: List<Player>, lives: Map<Int, Int>): Int? {
        if (players.size <= 1) return null
        val alive = players.filter { (lives[it.id] ?: 0) > 0 }
        if (alive.isEmpty()) return null
        val maxLives = alive.maxOf { lives[it.id] ?: 0 }
        val leaders = alive.filter { (lives[it.id] ?: 0) == maxLives }
        return leaders.singleOrNull()?.id
    }

    private fun nextAliveIndex(
        players: List<Player>,
        lives: Map<Int, Int>,
        current: Int,
    ): Int {
        for (offset in 1..players.size) {
            val index = (current + offset) % players.size
            if ((lives[players[index].id] ?: 0) > 0) return index
        }
        return current
    }

    private fun burn(
        count: Int,
        lavaNumbers: Set<Int>,
        random: Random,
    ): List<Int> {
        val remaining = LavaState.NUMBERS.filter { it !in lavaNumbers }
        return remaining
            .shuffled(random)
            .take(minOf(count, remaining.size))
            .sorted()
    }
}
