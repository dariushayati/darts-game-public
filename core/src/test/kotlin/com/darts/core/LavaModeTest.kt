package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LavaModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(
        lives: Int = 3,
        dartsPerTurn: Int = 3,
        lavaPerRound: Int = 3,
        players: List<Player> = players(),
    ) = LavaMode.newGame(players, LavaRules(lives, dartsPerTurn, lavaPerRound))
        .copy(lavaNumbers = emptySet())
    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE)
    private val miss = DartHit(null, 0, Ring.MISS)
    private val bull = DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL)

    private fun applyAll(state: LavaState, vararg hits: DartHit): LavaState =
        applyAll(state, Random(7), *hits)

    private fun applyAll(state: LavaState, random: Random, vararg hits: DartHit): LavaState =
        hits.fold(state) { s, hit -> LavaMode.apply(s, hit, random).state }

    @Test
    fun `new game supports one to six players and starts the first round with lava`() {
        val s = LavaMode.newGame(
            players(1),
            LavaRules(lives = 5, lavaPerRound = 4),
            Random(7),
        )
        assertEquals(1, s.players.size)
        assertEquals(5, s.livesByPlayer[0])
        assertEquals(4, s.lavaNumbers.size)
        assertEquals(16, s.numbersLeft)
        assertEquals(1, s.roundNumber)
        assertTrue(s.lavaNumbers.all { it in 1..20 })
        assertFalse(s.finished)
        assertThrows(IllegalArgumentException::class.java) { LavaMode.newGame(players(0)) }
        assertThrows(IllegalArgumentException::class.java) { LavaMode.newGame(players(7)) }
    }

    @Test
    fun `normal hits and bulls do not cost lives`() {
        val state = applyAll(game(players = players(1)), single(20), bull)
        assertEquals(3, state.livesByPlayer[0])
        assertEquals(2, state.dartsByPlayer[0])
    }

    @Test
    fun `a miss costs a life`() {
        val reduction = LavaMode.apply(game(players = players(1)), miss)
        assertEquals(2, reduction.state.livesByPlayer[0])
        assertEquals(1, reduction.state.dartsByPlayer[0])
        assertFalse(reduction.state.finished)
        assertTrue(reduction.events.any { it is LavaEvent.LifeLost })
    }

    @Test
    fun `hitting lava costs one life and can eliminate a player`() {
        val state = game(lives = 1).copy(lavaNumbers = setOf(20))
        val reduction = LavaMode.apply(state, single(20))
        assertEquals(0, reduction.state.livesByPlayer[0])
        assertEquals(listOf(0), reduction.state.eliminatedOrder)
        assertTrue(reduction.state.finished)
        assertEquals(1, reduction.state.winnerId)
        assertTrue(reduction.events.any { it is LavaEvent.LifeLost })
        assertTrue(reduction.events.any { it is LavaEvent.PlayerOut })
    }

    @Test
    fun `the last player standing wins`() {
        val state = game(lives = 1).copy(
            livesByPlayer = mapOf(0 to 1, 1 to 0),
            eliminatedOrder = listOf(1),
        )
        val reduction = LavaMode.apply(state, bull)
        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.winnerId)
        assertEquals(listOf(1), reduction.state.eliminatedOrder)
    }

    @Test
    fun `solo player runs out of lives without a winner`() {
        val state = game(lives = 1, players = players(1)).copy(lavaNumbers = setOf(20))
        val reduction = LavaMode.apply(state, single(20))
        assertTrue(reduction.state.finished)
        assertNull(reduction.state.winnerId)
        assertEquals(0, reduction.state.livesByPlayer[0])
    }

    @Test
    fun `a round burns random numbers after every live player has thrown`() {
        var state = game(dartsPerTurn = 3, lavaPerRound = 3)
        state = applyAll(state, single(20), bull, bull)
        assertEquals(0, state.lavaNumbers.size)
        assertEquals(1, state.roundNumber)

        state = applyAll(state, single(19), bull, bull)
        assertEquals(3, state.lavaNumbers.size)
        assertEquals(2, state.roundNumber)
        assertEquals(3, state.lastBurned.size)
    }

    @Test
    fun `one-player turn burns every round`() {
        var state = game(dartsPerTurn = 3, lavaPerRound = 2, players = players(1))
        state = applyAll(state, single(20), bull, bull)
        assertEquals(2, state.lavaNumbers.size)
        assertEquals(2, state.roundNumber)
    }

    @Test
    fun `eliminated players are skipped in the turn order`() {
        val p = players(3)
        var state = game(lives = 1, dartsPerTurn = 1, players = p).copy(
            livesByPlayer = mapOf(0 to 1, 1 to 0, 2 to 1),
            eliminatedOrder = listOf(1),
        )
        state = applyAll(state, single(20))
        assertEquals(2, state.currentPlayerIndex)
        state = applyAll(state, single(19))
        assertEquals(0, state.currentPlayerIndex)
    }

    @Test
    fun `elimination skips the rest of the lost turn`() {
        val p = players(2)
        val state = game(lives = 1, dartsPerTurn = 3, players = p).copy(lavaNumbers = setOf(20))
        val reduction = LavaMode.apply(state, single(20))
        // Player 0 is out, so the game ends immediately with player 1 as the winner.
        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.dartsLeft)
        assertEquals(1, reduction.state.winnerId)
    }

    @Test
    fun `a fully lava board ends the run while the bull still costs no life`() {
        val state = game(lives = 1, players = players(1)).copy(lavaNumbers = (1..20).toSet())
        val reduction = LavaMode.apply(state, bull)
        assertEquals(1, reduction.state.livesByPlayer[0])
        assertTrue(reduction.state.finished)
        assertEquals(LavaFinishReason.BOARD_CONSUMED, reduction.state.finishReason)
        assertNull(reduction.state.winnerId)
    }

    @Test
    fun `early autodarts visit advances the turn and burns at the round boundary`() {
        var state = game(lives = 3, dartsPerTurn = 3, lavaPerRound = 2, players = players(1))
        state = LavaMode.apply(state, single(20)).state
        state = LavaMode.advanceTurn(state, Random(4)).state
        assertEquals(2, state.lavaNumbers.size)
        assertEquals(2, state.roundNumber)
        assertEquals(3, state.livesByPlayer[0])
    }

    @Test
    fun `burning never duplicates and is capped by remaining numbers`() {
        val state = game(lives = 1, dartsPerTurn = 1, lavaPerRound = 20, players = players(1))
            .copy(lavaNumbers = setOf(1, 2, 3, 4, 5))
        val state2 = applyAll(state, bull)
        assertEquals(20, state2.lavaNumbers.size)
        assertTrue(state2.finished)
        assertEquals(LavaFinishReason.BOARD_CONSUMED, state2.finishReason)
    }

    @Test
    fun `a fully lava board awards the most lives`() {
        val state = game(
            lives = 3,
            dartsPerTurn = 1,
            lavaPerRound = 20,
            players = players(2),
        ).copy(
            currentPlayerIndex = 1,
            lavaNumbers = (1..19).toSet(),
            livesByPlayer = mapOf(0 to 1, 1 to 3),
        )
        val reduction = LavaMode.apply(state, bull)
        assertTrue(reduction.state.finished)
        assertTrue(reduction.state.allLava)
        assertEquals(1, reduction.state.winnerId)
        assertEquals(LavaFinishReason.BOARD_CONSUMED, reduction.state.finishReason)
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game(players = players(1)).copy(finished = true)
        val reduction = LavaMode.apply(finished, single(20))
        assertEquals(finished, reduction.state)
        assertTrue(reduction.events.isEmpty())
    }
}
