package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GunslingerModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }

    private fun game(
        playerCount: Int = 2,
        rounds: Int = 5,
        beeps: Int = 3,
        difficulty: GunslingerDifficulty = GunslingerDifficulty.HARD,
        seed: Int = 7,
    ): GunslingerState = GunslingerMode.newGame(
        players(playerCount),
        GunslingerRules(rounds = rounds, beeps = beeps, difficulty = difficulty),
        Random(seed),
    )

    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val miss = DartHit(null, 0, Ring.MISS)

    @Test
    fun `rules validate rounds beeps and score bounds`() {
        assertEquals(5, GunslingerRules().rounds)
        assertEquals(GunslingerDifficulty.NORMAL, GunslingerRules().difficulty)
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(rounds = 0) }
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(rounds = 51) }
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(beeps = -1) }
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(beeps = 11) }
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(basePoints = 0) }
        assertThrows(IllegalArgumentException::class.java) { GunslingerRules(pointsPerSecond = -1) }
        assertThrows(IllegalArgumentException::class.java) {
            GunslingerRules(basePoints = 100, minimumPoints = 200)
        }
    }

    @Test
    fun `new game supports one to six unique players and a valid target`() {
        assertEquals(1, GunslingerMode.newGame(players(1)).players.size)
        assertEquals(6, GunslingerMode.newGame(players(6)).players.size)
        assertThrows(IllegalArgumentException::class.java) {
            GunslingerMode.newGame(players(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            GunslingerMode.newGame(players(7))
        }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) {
            GunslingerMode.newGame(dupes)
        }
        val state = game(seed = 1)
        assertTrue(state.target in 1..20)
        assertEquals(3, state.dartsLeft)
        assertEquals(0, state.hits)
        assertEquals(0, state.pointsFor(0))
    }

    @Test
    fun `points decrease over time and stop at the floor`() {
        val rules = GunslingerRules(basePoints = 1000, pointsPerSecond = 50, minimumPoints = 100)
        assertEquals(1000, GunslingerMode.pointsFor(0, rules))
        assertEquals(950, GunslingerMode.pointsFor(1000, rules))
        assertEquals(750, GunslingerMode.pointsFor(5000, rules))
        assertEquals(100, GunslingerMode.pointsFor(25_000, rules))
        assertEquals(100, GunslingerMode.pointsFor(Long.MAX_VALUE / 2, rules))
    }

    @Test
    fun `board neighbours are the adjacent sectors`() {
        assertEquals(setOf(5, 1), GunslingerMode.neighbours(20))
        assertEquals(setOf(20, 18), GunslingerMode.neighbours(1))
        assertEquals(setOf(2, 3), GunslingerMode.neighbours(17))
        assertTrue(GunslingerMode.neighbours(BoardGeometry.BULL).isEmpty())
    }

    @Test
    fun `normal mode scores neighbours at half value`() {
        val state = game(playerCount = 1, rounds = 1, difficulty = GunslingerDifficulty.NORMAL)
            .copy(target = 20)
        val neighbour = GunslingerMode.neighbours(20).first()
        val after = GunslingerMode.apply(state, single(neighbour), 1000)
        assertEquals(475, after.state.turnPoints)
        assertEquals(1, after.state.hits)
        assertEquals(0, after.state.misses(0))
    }

    @Test
    fun `hard mode ignores neighbours`() {
        val state = game(playerCount = 1, rounds = 1, difficulty = GunslingerDifficulty.HARD)
            .copy(target = 20)
        val neighbour = GunslingerMode.neighbours(20).first()
        val after = GunslingerMode.apply(state, single(neighbour), 1000)
        assertEquals(0, after.state.turnPoints)
        assertEquals(0, after.state.hits)
        assertEquals(1, after.state.misses(0))
    }

    @Test
    fun `any ring on the target number counts as a hit`() {
        val state = game()
        val target = state.target
        assertEquals(1, GunslingerMode.apply(state, single(target), 1000).state.hits)
        assertEquals(1, GunslingerMode.apply(state, double(target), 1000).state.hits)
        assertEquals(1, GunslingerMode.apply(state, treble(target), 1000).state.hits)
    }

    @Test
    fun `wrong numbers and misses consume darts`() {
        val state = game()
        val wrong = GunslingerState.NUMBERS.first { it != state.target }
        val afterWrong = GunslingerMode.apply(state, single(wrong), 1000).state
        assertEquals(2, afterWrong.dartsLeft)
        assertEquals(0, afterWrong.hits)
        assertEquals(1, afterWrong.misses(0))

        val afterMiss = GunslingerMode.apply(state, miss, 1000).state
        assertEquals(2, afterMiss.dartsLeft)
        assertEquals(1, afterMiss.misses(0))
    }

    @Test
    fun `every target hit banks its own decaying value`() {
        val state = game(playerCount = 1, rounds = 1).copy(target = 20)
        val one = GunslingerMode.apply(state, single(20), 1000)
        assertEquals(950, one.state.turnPoints)
        assertEquals(1, one.state.hits)
        val two = GunslingerMode.apply(one.state, double(20), 2000)
        assertEquals(1850, two.state.turnPoints)
        assertEquals(2, two.state.hits)
        val done = GunslingerMode.apply(two.state, treble(20), 3000)
        assertEquals(2700, done.state.pointsFor(0))
        assertEquals(2700, done.state.lastTurnPoints(0))
        assertEquals(3, done.state.hits(0))
        assertEquals(1, done.events.count { it is GunslingerEvent.PointsBanked })
        assertTrue(done.events.any { it is GunslingerEvent.TurnScored && it.allHit })
        assertTrue(done.state.finished)
    }

    @Test
    fun `partial hits keep the points they banked`() {
        val state = game(playerCount = 1, rounds = 1).copy(target = 20)
        val one = GunslingerMode.apply(state, single(20), 1000).state
        val two = GunslingerMode.apply(one, single(20), 2000).state
        val done = GunslingerMode.apply(two, miss, 3000)
        assertEquals(1850, done.state.pointsFor(0))
        assertEquals(1850, done.state.lastTurnPoints(0))
        assertEquals(2, done.state.hits)
        assertEquals(1, done.state.misses(0))
        assertTrue(done.events.any { it is GunslingerEvent.TurnScored && !it.allHit })
        assertTrue(done.state.finished)
    }

    @Test
    fun `a turn with no target hits scores zero`() {
        val state = game(playerCount = 1, rounds = 1).copy(target = 20)
        var current = state
        repeat(3) { current = GunslingerMode.apply(current, miss, 1000).state }
        assertEquals(0, current.pointsFor(0))
        assertEquals(0, current.lastTurnPoints(0))
        assertTrue(current.finished)
    }

    @Test
    fun `turn passes after three darts and a fresh target is generated`() {
        val state = game(playerCount = 2, rounds = 2).copy(target = 20)
        var current = state
        repeat(3) { current = GunslingerMode.apply(current, miss, 1000).state }
        assertEquals(1, current.currentPlayerIndex)
        assertEquals(3, current.dartsLeft)
        assertEquals(0, current.hits)
        assertTrue(current.target in 1..20)
        assertTrue(current.roundNumber == 1)
    }

    @Test
    fun `round increments after every player has thrown`() {
        val state = game(playerCount = 2, rounds = 3).copy(target = 20)
        var current = state
        repeat(3) { current = GunslingerMode.apply(current, miss, 1000).state }
        assertEquals(1, current.currentPlayerIndex)
        assertEquals(1, current.roundNumber)
        repeat(3) { current = GunslingerMode.apply(current, miss, 1000).state }
        assertEquals(0, current.currentPlayerIndex)
        assertEquals(2, current.roundNumber)
        assertFalse(current.finished)
    }

    @Test
    fun `game finishes after the configured rounds`() {
        val state = game(playerCount = 1, rounds = 1).copy(target = 20)
        var reduction = GunslingerReduction(state, emptyList())
        repeat(3) { reduction = GunslingerMode.apply(reduction.state, miss, 1000) }
        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.winnerId)
        assertTrue(reduction.events.any { it is GunslingerEvent.GameFinished })
    }

    @Test
    fun `highest total wins and ties are draws`() {
        val players = players(2)
        assertEquals(1, GunslingerMode.winner(mapOf(0 to 100, 1 to 200), players))
        assertNull(GunslingerMode.winner(mapOf(0 to 200, 1 to 200), players))
        assertNull(GunslingerMode.winner(mapOf(0 to 0, 1 to 0), players))
    }

    @Test
    fun `early visit end keeps banked points and advances`() {
        val state = game(playerCount = 2, rounds = 2).copy(target = 20)
        val afterHit = GunslingerMode.apply(state, single(20), 1000).state
        val done = GunslingerMode.finishTurn(afterHit, 1200)
        assertEquals(950, done.state.pointsFor(0))
        assertEquals(950, done.state.lastTurnPoints(0))
        assertEquals(1, done.state.currentPlayerIndex)
        assertEquals(3, done.state.dartsLeft)
        assertEquals(0, done.state.hits)
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game(playerCount = 1, rounds = 1).copy(finished = true, winnerId = 0)
        val after = GunslingerMode.apply(finished, miss, 1000)
        assertEquals(finished, after.state)
        assertTrue(after.events.isEmpty())
    }
}
