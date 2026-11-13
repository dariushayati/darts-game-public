package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RingRunModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(dartsPerTurn: Int = 3, players: List<Player> = players()) =
        RingRunMode.newGame(players, RingRunRules(dartsPerTurn))

    private fun large(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun small(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.SMALL)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val outerBull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)
    private val innerBull = DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL)
    private val miss = DartHit(null, 0, Ring.MISS)

    @Test
    fun `new game supports one to six players and starts on round one`() {
        val s = RingRunMode.newGame(players(1), RingRunRules())
        assertEquals(1, s.players.size)
        assertEquals(0, s.roundIndex)
        assertEquals(1, s.roundNumber)
        assertEquals(RingRunRound.DOUBLE_AND_LARGE_SINGLE, s.round)
        assertEquals(0, s.scores[0])
        assertFalse(s.finished)

        assertEquals(6, RingRunMode.newGame(players(6)).players.size)
        assertThrows(IllegalArgumentException::class.java) { RingRunMode.newGame(players(0)) }
        assertThrows(IllegalArgumentException::class.java) { RingRunMode.newGame(players(7)) }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) { RingRunMode.newGame(dupes) }
    }

    @Test
    fun `round one scores any double or large single`() {
        assertEquals(1, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, double(20)))
        assertEquals(1, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, double(1)))
        assertEquals(1, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, large(20)))
        assertEquals(1, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, large(7)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, small(20)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, treble(20)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, outerBull))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.DOUBLE_AND_LARGE_SINGLE, miss))
    }

    @Test
    fun `round two scores only the requested large single numbers`() {
        listOf(12, 5, 20, 1, 18, 7, 19, 3, 17, 2).forEach { number ->
            assertEquals(1, RingRunMode.pointsFor(RingRunRound.LARGE_SINGLE, large(number)))
        }
        listOf(4, 6, 8, 9, 10, 11, 13, 14, 15, 16).forEach { number ->
            assertEquals(0, RingRunMode.pointsFor(RingRunRound.LARGE_SINGLE, large(number)))
        }
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.LARGE_SINGLE, double(12)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.LARGE_SINGLE, small(12)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.LARGE_SINGLE, treble(12)))
    }

    @Test
    fun `round three scores any small single for two points`() {
        assertEquals(2, RingRunMode.pointsFor(RingRunRound.SMALL_SINGLE, small(7)))
        assertEquals(2, RingRunMode.pointsFor(RingRunRound.SMALL_SINGLE, small(18)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.SMALL_SINGLE, large(7)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.SMALL_SINGLE, double(7)))
    }

    @Test
    fun `the final round scores both bull areas`() {
        assertEquals(3, RingRunMode.pointsFor(RingRunRound.BULL, outerBull))
        assertEquals(3, RingRunMode.pointsFor(RingRunRound.BULL, innerBull))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.BULL, large(20)))
        assertEquals(0, RingRunMode.pointsFor(RingRunRound.BULL, miss))
    }

    @Test
    fun `a dart adds its ring points and consumes one dart`() {
        val state = game(dartsPerTurn = 3, players = players(1)).copy(roundIndex = 2)
        val reduction = RingRunMode.apply(state, small(20))
        assertEquals(2, reduction.state.scores[0])
        assertEquals(1, reduction.state.dartsByPlayer[0])
        assertEquals(2, reduction.state.dartsLeft)
        assertTrue(reduction.events.any { it is RingRunEvent.DartScored && it.points == 2 })
    }

    @Test
    fun `misses and out of zone hits consume darts without scoring`() {
        val state = game(dartsPerTurn = 3, players = players(1))
        val missed = RingRunMode.apply(state, miss)
        val wrong = RingRunMode.apply(missed.state, treble(20))
        assertEquals(0, wrong.state.scores[0])
        assertEquals(2, wrong.state.dartsThrown)
    }

    @Test
    fun `rounds advance after every player has thrown`() {
        var state = game(dartsPerTurn = 1, players = players(2))
        state = RingRunMode.apply(state, large(20)).state
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(0, state.roundIndex)
        assertEquals(1, state.scores[0])

        state = RingRunMode.apply(state, miss).state
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(1, state.roundIndex)
        assertEquals(2, state.roundNumber)
        assertEquals(RingRunRound.LARGE_SINGLE, state.round)
        assertEquals(0, state.scores[1])
    }

    @Test
    fun `the fourth round is the bull and ends the game`() {
        var state = game(dartsPerTurn = 1, players = players(1)).copy(
            roundIndex = 3,
            scores = mapOf(0 to 12),
        )
        state = RingRunMode.apply(state, outerBull).state
        assertTrue(state.finished)
        assertEquals(15, state.scores[0])
        assertEquals(0, state.dartsLeft)
        assertEquals(0, state.winnerId)
    }

    @Test
    fun `solo play finishes after four rounds`() {
        var state = game(dartsPerTurn = 1, players = players(1))
        repeat(4) { state = RingRunMode.apply(state, miss).state }
        assertTrue(state.finished)
        assertEquals(4, state.dartsThrown)
        assertEquals(0, state.winnerId)
    }

    @Test
    fun `early autodarts visit advances the player and then the round`() {
        var state = game(dartsPerTurn = 3, players = players(2))
        state = RingRunMode.advanceTurn(state).state
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(0, state.roundIndex)
        assertEquals(3, state.dartsLeft)

        state = RingRunMode.advanceTurn(state).state
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(1, state.roundIndex)
        assertEquals(RingRunRound.LARGE_SINGLE, state.round)
    }

    @Test
    fun `winner is the highest score and ties are draws`() {
        val p = players(2)
        assertEquals(0, RingRunMode.winner(mapOf(0 to 12, 1 to 8), p))
        assertEquals(1, RingRunMode.winner(mapOf(0 to 8, 1 to 12), p))
        assertNull(RingRunMode.winner(mapOf(0 to 12, 1 to 12), p))
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game(players = players(1)).copy(finished = true)
        val reduction = RingRunMode.apply(finished, innerBull)
        assertEquals(finished, reduction.state)
        assertTrue(reduction.events.isEmpty())
    }
}
