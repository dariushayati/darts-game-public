package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RobinHoodModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(rounds: Int = 5, players: List<Player> = players()) =
        RobinHoodMode.newGame(players, RobinHoodRules(rounds))

    private fun large(n: Int = 20) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun small(n: Int = 20) = DartHit(n, 1, Ring.SINGLE, SingleBand.SMALL)
    private fun double(n: Int = 20) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int = 20) = DartHit(n, 3, Ring.TREBLE)
    private val outerBull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)
    private val innerBull = DartHit(BoardGeometry.BULL, 1, Ring.INNER_BULL)
    private val miss = DartHit(null, 0, Ring.MISS)

    @Test
    fun `new game starts with three darts and the configured rounds`() {
        val s = RobinHoodMode.newGame(players(1), RobinHoodRules(rounds = 7))
        assertEquals(3, s.dartsLeft)
        assertEquals(1, s.roundNumber)
        assertEquals(7, s.rules.rounds)
        assertEquals(0, s.scores[0])
        assertFalse(s.finished)

        assertEquals(6, RobinHoodMode.newGame(players(6)).players.size)
        assertThrows(IllegalArgumentException::class.java) { RobinHoodMode.newGame(players(0)) }
        assertThrows(IllegalArgumentException::class.java) { RobinHoodMode.newGame(players(7)) }
        assertThrows(IllegalArgumentException::class.java) { RobinHoodRules(0) }
        assertThrows(IllegalArgumentException::class.java) { RobinHoodRules(21) }
    }

    @Test
    fun `scoring rises toward the bull`() {
        assertEquals(1, RobinHoodMode.pointsFor(double()))
        assertEquals(2, RobinHoodMode.pointsFor(large()))
        assertEquals(4, RobinHoodMode.pointsFor(treble()))
        assertEquals(4, RobinHoodMode.pointsFor(small()))
        assertEquals(6, RobinHoodMode.pointsFor(outerBull))
        assertEquals(8, RobinHoodMode.pointsFor(innerBull))
        assertEquals(0, RobinHoodMode.pointsFor(miss))
    }

    @Test
    fun `the number hit does not change the points`() {
        listOf(1, 7, 20).forEach { number ->
            assertEquals(1, RobinHoodMode.pointsFor(double(number)))
            assertEquals(2, RobinHoodMode.pointsFor(large(number)))
            assertEquals(4, RobinHoodMode.pointsFor(treble(number)))
            assertEquals(4, RobinHoodMode.pointsFor(small(number)))
        }
    }

    @Test
    fun `a dart adds points and consumes one dart`() {
        val state = game(players = players(1))
        val reduction = RobinHoodMode.apply(state, innerBull)
        assertEquals(8, reduction.state.scores[0])
        assertEquals(2, reduction.state.dartsLeft)
        assertEquals(1, reduction.state.dartsByPlayer[0])
        assertTrue(reduction.events.any { it is RobinHoodEvent.DartScored && it.points == 8 })
    }

    @Test
    fun `misses and doubles still consume darts`() {
        val state = game(players = players(1))
        val first = RobinHoodMode.apply(state, miss)
        val second = RobinHoodMode.apply(first.state, double())
        assertEquals(1, second.state.scores[0])
        assertEquals(2, second.state.dartsThrown)
    }

    @Test
    fun `rounds advance after every player has thrown`() {
        var state = game(rounds = 2, players = players(2))
        repeat(3) { state = RobinHoodMode.apply(state, innerBull).state }
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(1, state.roundNumber)
        assertEquals(24, state.scores[0])

        repeat(3) { state = RobinHoodMode.apply(state, outerBull).state }
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(2, state.roundNumber)
        assertEquals(18, state.scores[1])
        assertFalse(state.finished)
    }

    @Test
    fun `the configured final round ends the game`() {
        var state = game(rounds = 1, players = players(1))
        repeat(2) { state = RobinHoodMode.apply(state, double()).state }
        assertFalse(state.finished)
        state = RobinHoodMode.apply(state, innerBull).state
        assertTrue(state.finished)
        assertEquals(10, state.scores[0])
        assertEquals(0, state.dartsLeft)
        assertEquals(0, state.winnerId)
    }

    @Test
    fun `editing rounds changes the game length`() {
        var short = game(rounds = 1, players = players(1))
        repeat(3) { short = RobinHoodMode.apply(short, miss).state }
        assertTrue(short.finished)

        var long = game(rounds = 2, players = players(1))
        repeat(3) { long = RobinHoodMode.apply(long, miss).state }
        assertFalse(long.finished)
        assertEquals(2, long.roundNumber)
        repeat(3) { long = RobinHoodMode.apply(long, miss).state }
        assertTrue(long.finished)
    }

    @Test
    fun `early autodarts visit advances the player and then the round`() {
        var state = game(rounds = 3, players = players(2))
        state = RobinHoodMode.advanceTurn(state).state
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(1, state.roundNumber)
        assertEquals(3, state.dartsLeft)

        state = RobinHoodMode.advanceTurn(state).state
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(2, state.roundNumber)
    }

    @Test
    fun `winner is the highest score and ties are draws`() {
        val p = players(2)
        assertEquals(0, RobinHoodMode.winner(mapOf(0 to 20, 1 to 10), p))
        assertEquals(1, RobinHoodMode.winner(mapOf(0 to 10, 1 to 20), p))
        assertNull(RobinHoodMode.winner(mapOf(0 to 20, 1 to 20), p))
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game(players = players(1)).copy(finished = true)
        val reduction = RobinHoodMode.apply(finished, innerBull)
        assertEquals(finished, reduction.state)
        assertTrue(reduction.events.isEmpty())
    }
}
