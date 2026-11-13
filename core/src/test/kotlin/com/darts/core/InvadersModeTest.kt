package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InvadersModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(dartsPerPlayer: Int = 15, players: List<Player> = players()) =
        InvadersMode.newGame(players, InvadersRules(dartsPerPlayer = dartsPerPlayer))
    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val miss = DartHit(null, 0, Ring.MISS)
    private val bull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)

    private fun applyAll(state: InvadersState, vararg hits: DartHit): InvadersState =
        hits.fold(state) { s, hit -> InvadersMode.apply(s, hit).state }

    @Test
    fun `new game starts with three aliens on every number and two players`() {
        val s = game()
        assertEquals(20, s.aliens.size)
        assertTrue(s.aliens.values.all { it == 3 })
        assertEquals(60, s.aliensLeft)
        assertEquals(2, s.players.size)
        assertEquals(0, s.scores[0])
        assertEquals(0, s.scores[1])
        assertEquals(3, s.dartsLeft)
        assertEquals(0, s.currentPlayerIndex)
        assertFalse(s.finished)
    }

    @Test
    fun `supports one to six players with unique ids`() {
        assertEquals(1, InvadersMode.newGame(players(1)).players.size)
        assertThrows(IllegalArgumentException::class.java) { InvadersMode.newGame(players(0)) }
        assertThrows(IllegalArgumentException::class.java) { InvadersMode.newGame(players(7)) }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) { InvadersMode.newGame(dupes) }
    }

    @Test
    fun `one player finishes after their own dart budget`() {
        var state = InvadersMode.newGame(players(1), InvadersRules(dartsPerPlayer = 3))
        repeat(3) { state = InvadersMode.apply(state, miss).state }
        assertTrue(state.finished)
        assertEquals(0, state.winnerId)
    }

    @Test
    fun `first second and third aliens are worth 1 3 and 5`() {
        var state = applyAll(game(), single(20))
        assertEquals(1, state.scores[0])
        assertEquals(2, state.aliens[20])

        state = applyAll(state, single(20))
        assertEquals(4, state.scores[0])
        assertEquals(1, state.aliens[20])

        state = applyAll(state, single(20))
        assertEquals(9, state.scores[0])
        assertEquals(0, state.aliens[20])
        assertEquals(3, state.killsByPlayer[0])
    }

    @Test
    fun `a double kills two aliens and a treble kills three`() {
        val doubleState = applyAll(game(), double(20))
        assertEquals(4, doubleState.scores[0])
        assertEquals(1, doubleState.aliens[20])
        assertEquals(2, doubleState.killsByPlayer[0])

        val trebleState = applyAll(game(), treble(20))
        assertEquals(9, trebleState.scores[0])
        assertEquals(0, trebleState.aliens[20])
        assertEquals(3, trebleState.killsByPlayer[0])
    }

    @Test
    fun `extra kills are wasted when fewer aliens remain`() {
        var state = applyAll(game(), double(20))
        state = applyAll(state, double(20))
        assertEquals(9, state.scores[0])
        assertEquals(0, state.aliens[20])

        state = applyAll(state, treble(20))
        assertEquals(9, state.scores[0])
    }

    @Test
    fun `shared aliens let the next player collect the higher-value aliens`() {
        var state = applyAll(game(), single(20))
        assertEquals(1, state.scores[0])

        // P0's turn ends after three darts; P1 now doubles the same number.
        state = applyAll(state, single(19), single(18), double(20))
        assertEquals(3, state.scores[0])
        assertEquals(8, state.scores[1])
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(0, state.aliens[20])
    }

    @Test
    fun `players take turns after the configured darts`() {
        val state = applyAll(game(dartsPerPlayer = 6), single(20), single(19), single(18))
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(1, state.turnNumber)
        assertEquals(3, state.dartsLeft)
        assertEquals(3, state.dartsByPlayer[0])
        assertEquals(0, state.dartsByPlayer[1])
    }

    @Test
    fun `early autodarts visit advances the turn without consuming more darts`() {
        var state = applyAll(game(), single(20))
        state = InvadersMode.advanceTurn(state).state
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.dartsLeft)
        assertEquals(1, state.dartsByPlayer[0])
    }

    @Test
    fun `misses and bulls consume darts without scoring`() {
        var state = applyAll(game(), miss, bull)
        assertEquals(0, state.scores[0])
        assertEquals(2, state.dartsByPlayer[0])
        assertEquals(60, state.aliensLeft)
    }

    @Test
    fun `game ends after every player has used their dart budget`() {
        val rules = InvadersRules(dartsPerPlayer = 3)
        var state = InvadersMode.newGame(players(), rules)
        repeat(6) { state = InvadersMode.apply(state, miss).state }
        assertTrue(state.finished)
        assertEquals(3, state.dartsByPlayer[0])
        assertEquals(3, state.dartsByPlayer[1])
        assertNull(state.winnerId)
    }

    @Test
    fun `all aliens cleared ends the game early and picks the score leader`() {
        val state = game().copy(
            aliens = InvadersState.NUMBERS.associateWith { number -> if (number == 20) 1 else 0 },
            scores = mapOf(0 to 10, 1 to 5),
        )
        val reduction = InvadersMode.apply(state, single(20))
        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.winnerId)
        assertTrue(reduction.state.allCleared)
        assertTrue(reduction.events.any { it is InvadersEvent.GameFinished })
    }

    @Test
    fun `winner is the highest score with kills as a tiebreak`() {
        val p = players(2)
        assertEquals(0, InvadersMode.winner(mapOf(0 to 12, 1 to 8), mapOf(0 to 2, 1 to 3), p))
        assertEquals(1, InvadersMode.winner(mapOf(0 to 12, 1 to 12), mapOf(0 to 2, 1 to 3), p))
        assertNull(InvadersMode.winner(mapOf(0 to 12, 1 to 12), mapOf(0 to 3, 1 to 3), p))
    }

    @Test
    fun `perfect clear scores 180 across the players`() {
        val rules = InvadersRules(dartsPerPlayer = 30, dartsPerTurn = 10)
        var state = InvadersMode.newGame(players(), rules)
        repeat(10) { state = InvadersMode.apply(state, treble(it + 1)).state }
        repeat(10) { state = InvadersMode.apply(state, treble(it + 11)).state }
        assertEquals(180, state.scores.values.sum())
        assertEquals(60, state.killsByPlayer.values.sum())
        assertTrue(state.finished)
        assertTrue(state.allCleared)
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game().copy(finished = true)
        val reduction = InvadersMode.apply(finished, treble(20))
        assertEquals(finished, reduction.state)
        assertTrue(reduction.events.isEmpty())
    }
}
