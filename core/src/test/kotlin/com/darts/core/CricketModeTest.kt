package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CricketModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }

    private fun game(playerCount: Int = 2, dartsPerTurn: Int = 3): CricketState =
        CricketMode.newGame(players(playerCount), CricketRules(dartsPerTurn))

    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val outerBull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)
    private val innerBull = DartHit(BoardGeometry.BULL, 1, Ring.INNER_BULL)
    private val miss = DartHit(null, 0, Ring.MISS)

    private fun marks(state: CricketState, target: Int) = state.marks(0, target)

    private fun closeAll(state: CricketState, playerId: Int = 0): CricketState =
        state.copy(
            marks = state.marks + (playerId to CricketMode.TARGETS.associateWith { 3 }),
        )

    @Test
    fun `new game supports one to six unique players`() {
        assertEquals(1, CricketMode.newGame(players(1)).players.size)
        assertEquals(6, CricketMode.newGame(players(6)).players.size)
        assertThrows(IllegalArgumentException::class.java) {
            CricketMode.newGame(players(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CricketMode.newGame(players(7))
        }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) {
            CricketMode.newGame(dupes)
        }
    }

    @Test
    fun `targets are twenty to fifteen plus the bull`() {
        assertEquals(listOf(20, 19, 18, 17, 16, 15, 25), CricketMode.TARGETS)
        val state = game()
        assertEquals(2, state.marks.size)
        assertEquals(7, state.marks.getValue(0).size)
        assertTrue(CricketMode.TARGETS.all { state.marks(0, it) == 0 })
        assertEquals(0, state.pointsFor(0))
        assertEquals(3, state.dartsLeft)
    }

    @Test
    fun `single double and treble add one two and three marks`() {
        assertEquals(1, CricketMode.apply(game(), single(20)).state.marks(0, 20))
        assertEquals(2, CricketMode.apply(game(), double(19)).state.marks(0, 19))
        assertEquals(3, CricketMode.apply(game(), treble(18)).state.marks(0, 18))
    }

    @Test
    fun `marks cap at three to close a target`() {
        val one = CricketMode.apply(game(), single(20)).state
        val three = CricketMode.apply(one, double(20)).state
        assertEquals(3, marks(three, 20))
        assertTrue(three.isClosed(0, 20))
        assertEquals(1, three.closedCount(0))
    }

    @Test
    fun `closing a target does not score`() {
        val closed = CricketMode.apply(CricketMode.apply(game(), double(20)).state, single(20)).state
        // Two marks then a single closes it; the closing visit scores nothing.
        assertEquals(3, closed.marks(0, 20))
        assertEquals(0, closed.pointsFor(0))
        // The next hit on the closed target scores because the opponent is still open.
        val scored = CricketMode.apply(closed, single(20)).state
        assertEquals(20, scored.pointsFor(0))
    }

    @Test
    fun `closed target scores while an opponent has it open`() {
        val state = closeAll(game(), playerId = 0).copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 },
                1 to CricketMode.TARGETS.associateWith { 0 },
            ),
        )
        val scored = CricketMode.apply(state, treble(20))
        assertEquals(60, scored.state.pointsFor(0))
        assertTrue(scored.events.any { it is CricketEvent.PointsScored && it.points == 60 })
    }

    @Test
    fun `closed target does not score once every opponent closed it`() {
        val state = game().copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 },
                1 to CricketMode.TARGETS.associateWith { 3 },
            ),
        )
        val after = CricketMode.apply(state, single(20))
        assertEquals(0, after.state.pointsFor(0))
        assertFalse(after.events.any { it is CricketEvent.PointsScored })
    }

    @Test
    fun `multiplayer scores while any opponent is still open`() {
        val state = game(playerCount = 3).copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 },
                1 to CricketMode.TARGETS.associateWith { 3 },
                2 to CricketMode.TARGETS.associateWith { 0 },
            ),
        )
        val after = CricketMode.apply(state, single(20))
        assertEquals(20, after.state.pointsFor(0))
    }

    @Test
    fun `bull marks and points use the two bull areas`() {
        val outer = CricketMode.apply(game(), outerBull).state
        assertEquals(1, outer.marks(0, BoardGeometry.BULL))

        val inner = CricketMode.apply(game(), innerBull).state
        assertEquals(2, inner.marks(0, BoardGeometry.BULL))

        val closedOuter = game().copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 },
                1 to CricketMode.TARGETS.associateWith { 0 },
            ),
        )
        assertEquals(25, CricketMode.apply(closedOuter, outerBull).state.pointsFor(0))
        assertEquals(50, CricketMode.apply(closedOuter, innerBull).state.pointsFor(0))
    }

    @Test
    fun `miss and off target hits consume a dart`() {
        val missed = CricketMode.apply(game(), miss)
        assertEquals(2, missed.state.dartsLeft)
        assertEquals(1, missed.state.dartsThrown)

        val wrong = CricketMode.apply(game(), single(14))
        assertEquals(2, wrong.state.dartsLeft)
        assertEquals(0, wrong.state.marks(0, 20))
    }

    @Test
    fun `turn passes after the visit and wraps to the first player`() {
        val state = game(playerCount = 2, dartsPerTurn = 1)
        val first = CricketMode.apply(state, miss)
        assertEquals(1, first.state.currentPlayerIndex)
        assertEquals(1, first.state.turnNumber)
        val second = CricketMode.apply(first.state, miss)
        assertEquals(0, second.state.currentPlayerIndex)
        assertEquals(2, second.state.turnNumber)
    }

    @Test
    fun `advance turn resets the visit`() {
        val state = game(playerCount = 3)
        val after = CricketMode.advanceTurn(state)
        assertEquals(1, after.state.currentPlayerIndex)
        assertEquals(3, after.state.dartsLeft)
        assertTrue(after.events.any { it is CricketEvent.TurnChanged })
    }

    @Test
    fun `solo player wins by closing every target`() {
        val state = game(playerCount = 1).copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 }.toMutableMap().also { it[20] = 2 },
            ),
        )
        val won = CricketMode.apply(state, single(20))
        assertTrue(won.state.finished)
        assertEquals(0, won.state.winnerId)
        assertEquals(0, won.state.dartsLeft)
        assertTrue(won.events.any { it is CricketEvent.GameFinished })
    }

    @Test
    fun `behind on points cannot win until taking the lead`() {
        val state = game(playerCount = 2).copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 }.toMutableMap().also { it[20] = 2 },
                1 to CricketMode.TARGETS.associateWith { 3 }.toMutableMap().also { it[20] = 0 },
            ),
            points = mapOf(0 to 50, 1 to 100),
        )
        val closed = CricketMode.apply(state, single(20))
        assertFalse(closed.state.finished)
        assertNull(closed.state.winnerId)
        assertEquals(3, closed.state.marks(0, 20))
        assertEquals(50, closed.state.pointsFor(0))

        val caughtUp = CricketMode.apply(closed.state, treble(20))
        assertTrue(caughtUp.state.finished)
        assertEquals(0, caughtUp.state.winnerId)
        assertEquals(110, caughtUp.state.pointsFor(0))
    }

    @Test
    fun `closing the last target while ahead wins immediately`() {
        val state = game(playerCount = 2).copy(
            marks = mapOf(
                0 to CricketMode.TARGETS.associateWith { 3 }.toMutableMap().also { it[20] = 2 },
                1 to CricketMode.TARGETS.associateWith { 0 },
            ),
            points = mapOf(0 to 60, 1 to 0),
        )
        val won = CricketMode.apply(state, single(20))
        assertTrue(won.state.finished)
        assertEquals(0, won.state.winnerId)
    }

    @Test
    fun `finished games ignore further darts`() {
        val finished = game(playerCount = 1).copy(finished = true, winnerId = 0)
        val after = CricketMode.apply(finished, single(20))
        assertEquals(finished, after.state)
        assertTrue(after.events.isEmpty())
    }

    @Test
    fun `open targets reflect marks`() {
        val one = CricketMode.apply(game(), treble(20)).state
        assertFalse(one.openTargets(0).contains(20))
        assertTrue(one.openTargets(0).contains(19))
        assertEquals(6, one.openTargets(0).size)
    }
}
