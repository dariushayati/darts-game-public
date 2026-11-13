package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AroundTheClockModeTest {

    private fun player(id: Int, name: String = "Player $id") =
        Player(id = id, name = name, colorIndex = id)

    private fun DartHit.matches(target: Int) = AroundTheClockMode.matches(this, target)

    @Test
    fun newGameStartsEveryoneOnOneWithThreeDarts() {
        val state = AroundTheClockMode.newGame(listOf(player(0), player(1)))

        assertEquals(1, state.target)
        assertEquals(3, state.dartsLeft)
        assertEquals(0, state.progressFor(0))
        assertEquals(0, state.progressFor(1))
        assertEquals("1", state.labelFor(0))
    }

    @Test
    fun anyRingOnTheCurrentNumberAdvancesOneStep() {
        var state = AroundTheClockMode.newGame(listOf(player(0)))

        state = AroundTheClockMode.apply(
            state,
            DartHit(1, 1, Ring.SINGLE, SingleBand.LARGE),
        ).state
        assertEquals(2, state.target)

        state = AroundTheClockMode.apply(
            state,
            DartHit(2, 3, Ring.TREBLE),
        ).state
        assertEquals(3, state.target)
    }

    @Test
    fun wrongNumberAndMissDoNotAdvance() {
        var state = AroundTheClockMode.newGame(listOf(player(0)))

        state = AroundTheClockMode.apply(state, DartHit(20, 1, Ring.SINGLE)).state
        state = AroundTheClockMode.apply(state, DartHit(null, 0, Ring.MISS)).state

        assertEquals(1, state.target)
        assertEquals(1, state.dartsLeft)
        assertEquals(2, state.dartsThrown)
    }

    @Test
    fun turnPassesAfterThreeDarts() {
        var state = AroundTheClockMode.newGame(listOf(player(0), player(1)))

        repeat(3) {
            state = AroundTheClockMode.apply(state, DartHit(null, 0, Ring.MISS)).state
        }

        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.dartsLeft)
        assertEquals(0, state.progressFor(0))
        assertEquals(0, state.progressFor(1))
        assertEquals(1, state.turnNumber)
    }

    @Test
    fun hittingTheBullBeforeTheFinalTargetDoesNotWin() {
        var state = AroundTheClockMode.newGame(listOf(player(0)))

        state = AroundTheClockMode.apply(state, DartHit(25, 1, Ring.INNER_BULL)).state

        assertFalse(state.finished)
        assertEquals(1, state.target)
    }

    @Test
    fun finishingOnTheBullWins() {
        var state = AroundTheClockMode.newGame(listOf(player(0), player(1)))
        state = state.copy(progress = mapOf(0 to 20, 1 to 8), dartsLeft = 2)

        val reduction = AroundTheClockMode.apply(state, DartHit(25, 2, Ring.INNER_BULL))

        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.winnerId)
        assertEquals(21, reduction.state.progressFor(0))
        assertNull(reduction.state.target)
        assertTrue(reduction.events.any { it is AroundTheClockEvent.GameFinished })
    }

    @Test
    fun advanceTurnMovesToTheNextPlayerAndResetsDarts() {
        var state = AroundTheClockMode.newGame(listOf(player(0), player(1)))
        state = AroundTheClockMode.apply(state, DartHit(1, 1, Ring.SINGLE)).state

        state = AroundTheClockMode.advanceTurn(state).state

        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.dartsLeft)
        assertEquals(1, state.progressFor(0))
    }

    @Test
    fun targetsRunOneThroughTwentyThenBull() {
        assertEquals(21, AroundTheClockMode.TARGETS.size)
        assertEquals(1, AroundTheClockMode.TARGETS.first())
        assertEquals(20, AroundTheClockMode.TARGETS[19])
        assertEquals(25, AroundTheClockMode.TARGETS.last())
        assertEquals("BULL", AroundTheClockMode.labelFor(25))
    }

    @Test
    fun bullTargetMatchesOuterAndInnerBull() {
        assertTrue(DartHit(25, 1, Ring.OUTER_BULL).matches(25))
        assertTrue(DartHit(25, 2, Ring.INNER_BULL).matches(25))
        assertFalse(DartHit(20, 1, Ring.SINGLE).matches(25))
    }
}
