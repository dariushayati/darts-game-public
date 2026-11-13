package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class X01ModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }

    private fun game(rules: X01Rules = X01Rules(), n: Int = 2): X01State =
        X01Mode.newGame(players(n), rules)

    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val outerBull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)
    private val innerBull = DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL)
    private val miss = DartHit(null, 0, Ring.MISS)

    private fun X01State.atScore(playerId: Int, score: Int, opened: Boolean = true): X01State =
        copy(
            scores = scores + (playerId to score),
            opened = this.opened + (playerId to opened),
            turnStartScore = score,
            turnStartOpened = opened,
        )

    @Test
    fun `new game starts at the selected score`() {
        val state = game(X01Rules(startingScore = 301))
        assertEquals(301, state.scoreFor(0))
        assertEquals(301, state.scoreFor(1))
        assertEquals(0, state.legsWon(0))
        assertEquals(3, state.dartsLeft)
        assertTrue(state.isOpened(0))
        assertEquals(501, game(X01Rules(startingScore = 501)).scoreFor(0))
        assertEquals(121, game(X01Rules(startingScore = 121)).scoreFor(0))
    }

    @Test
    fun `double in starts unopened and straight in starts opened`() {
        val doubleIn = game(X01Rules(startingScore = 121, inRule = X01InRule.DOUBLE))
        assertFalse(doubleIn.isOpened(0))
        val straightIn = game(X01Rules(startingScore = 121))
        assertTrue(straightIn.isOpened(0))
    }

    @Test
    fun `invalid rules are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            X01Rules(startingScore = 401)
        }
        assertThrows(IllegalArgumentException::class.java) {
            X01Rules(legsToWin = 0)
        }
    }

    @Test
    fun `darts subtract single double and treble values`() {
        val first = X01Mode.apply(game(), treble(20))
        assertEquals(441, first.state.scoreFor(0))
        val second = X01Mode.apply(first.state, double(19))
        assertEquals(403, second.state.scoreFor(0))
        val third = X01Mode.apply(second.state, single(20))
        assertEquals(383, third.state.scoreFor(0))
        assertEquals(3, third.state.dartsByPlayer[0])
        assertTrue(third.events.any { it is X01Event.DartScored && it.after == 383 })
    }

    @Test
    fun `a 180 visit leaves 321 and passes the turn after three darts`() {
        var state = game()
        repeat(3) { state = X01Mode.apply(state, treble(20)).state }
        assertEquals(321, state.scoreFor(0))
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.dartsLeft)
    }

    @Test
    fun `double out checkout wins the leg`() {
        val state = game(X01Rules(startingScore = 121))
            .atScore(0, 40)
        val result = X01Mode.apply(state, double(20))
        assertTrue(result.state.finished)
        assertEquals(0, result.state.winnerId)
        assertEquals(1, result.state.legsWon(0))
        assertEquals(0, result.state.scoreFor(0))
        assertTrue(result.events.any { it is X01Event.Checkout })
        assertTrue(result.events.any { it is X01Event.MatchFinished })
    }

    @Test
    fun `single finish is a bust when double out is on`() {
        val state = game(X01Rules(startingScore = 121))
            .atScore(0, 20)
        val result = X01Mode.apply(state, single(20))
        assertEquals(20, result.state.scoreFor(0))
        assertEquals(1, result.state.currentPlayerIndex)
        assertTrue(result.events.any { it is X01Event.Bust })
    }

    @Test
    fun `leaving one is a bust for double out but fine for straight out`() {
        val doubleOut = game(X01Rules(startingScore = 121))
            .atScore(0, 3)
        val bust = X01Mode.apply(doubleOut, single(2))
        assertEquals(3, bust.state.scoreFor(0))
        assertTrue(bust.events.any { it is X01Event.Bust })

        val straightOut = game(X01Rules(startingScore = 121, outRule = X01OutRule.STRAIGHT))
            .atScore(0, 3)
        val one = X01Mode.apply(straightOut, single(2))
        assertEquals(1, one.state.scoreFor(0))
        assertFalse(one.events.any { it is X01Event.Bust })
        val checkout = X01Mode.apply(one.state, single(1))
        assertTrue(checkout.state.finished)
        assertEquals(0, checkout.state.winnerId)
    }

    @Test
    fun `going below zero is a bust`() {
        val state = game(X01Rules(startingScore = 121))
            .atScore(0, 10)
        val result = X01Mode.apply(state, treble(20))
        assertEquals(10, result.state.scoreFor(0))
        assertEquals(1, result.state.currentPlayerIndex)
        assertTrue(result.events.any { it is X01Event.Bust })
    }

    @Test
    fun `double in ignores non doubles until a double opens the turn`() {
        val rules = X01Rules(startingScore = 121, inRule = X01InRule.DOUBLE)
        val missResult = X01Mode.apply(game(rules), single(20))
        assertEquals(121, missResult.state.scoreFor(0))
        assertFalse(missResult.state.isOpened(0))
        assertTrue(missResult.events.any { it is X01Event.NoDoubleIn })

        val opened = X01Mode.apply(missResult.state, double(20))
        assertEquals(81, opened.state.scoreFor(0))
        assertTrue(opened.state.isOpened(0))
        assertTrue(opened.events.any { it is X01Event.DartScored && it.openedNow })
    }

    @Test
    fun `master in opens on a treble`() {
        val rules = X01Rules(startingScore = 121, inRule = X01InRule.MASTER)
        val result = X01Mode.apply(game(rules), treble(20))
        assertEquals(61, result.state.scoreFor(0))
        assertTrue(result.state.isOpened(0))
    }

    @Test
    fun `inner bull counts as a double out but outer bull does not`() {
        val inner = X01Mode.apply(
            game(X01Rules(startingScore = 121)).atScore(0, 50),
            innerBull,
        )
        assertTrue(inner.state.finished)

        val outer = X01Mode.apply(
            game(X01Rules(startingScore = 121)).atScore(0, 25),
            outerBull,
        )
        assertFalse(outer.state.finished)
        assertEquals(25, outer.state.scoreFor(0))
        assertTrue(outer.events.any { it is X01Event.Bust })
    }

    @Test
    fun `master out allows a treble finish`() {
        val rules = X01Rules(startingScore = 121, outRule = X01OutRule.MASTER)
        val result = X01Mode.apply(
            game(rules).atScore(0, 60),
            treble(20),
        )
        assertTrue(result.state.finished)

        val doubleOnly = X01Mode.apply(
            game(X01Rules(startingScore = 121)).atScore(0, 60),
            treble(20),
        )
        assertFalse(doubleOnly.state.finished)
        assertEquals(60, doubleOnly.state.scoreFor(0))
    }

    @Test
    fun `a leg win resets scores and alternates the starting player`() {
        val rules = X01Rules(startingScore = 121, legsToWin = 2)
        val state = game(rules).atScore(0, 40)
        val result = X01Mode.apply(state, double(20))
        assertFalse(result.state.finished)
        assertEquals(1, result.state.legsWon(0))
        assertEquals(2, result.state.legNumber)
        assertEquals(1, result.state.currentPlayerIndex)
        assertEquals(1, result.state.legStartPlayerIndex)
        assertEquals(121, result.state.scoreFor(0))
        assertEquals(121, result.state.scoreFor(1))
        assertEquals(3, result.state.dartsLeft)
        assertEquals(1, result.state.turnNumber)
        assertTrue(result.events.any { it is X01Event.LegWon })
    }

    @Test
    fun `first to the required legs wins the match`() {
        val rules = X01Rules(startingScore = 121, legsToWin = 2)
        val state = game(rules)
            .copy(legs = mapOf(0 to 1, 1 to 0))
            .atScore(0, 40)
        val result = X01Mode.apply(state, double(20))
        assertTrue(result.state.finished)
        assertEquals(0, result.state.winnerId)
        assertEquals(2, result.state.legsWon(0))
    }

    @Test
    fun `bust reverts a double in that happened during the same turn`() {
        val rules = X01Rules(startingScore = 121, inRule = X01InRule.DOUBLE)
        val opened = X01Mode.apply(game(rules), double(20)).state
        assertEquals(81, opened.scoreFor(0))
        assertTrue(opened.isOpened(0))
        val bust = X01Mode.apply(opened, treble(20)).state
        assertEquals(21, bust.scoreFor(0))
        val final = X01Mode.apply(bust, single(20)).state
        assertEquals(121, final.scoreFor(0))
        assertFalse(final.isOpened(0))
        assertTrue(final.finished.not())
    }

    @Test
    fun `turn passes after three darts and advance turn ends a visit`() {
        var state = game(X01Rules(startingScore = 121))
        repeat(3) { state = X01Mode.apply(state, miss).state }
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(1, state.turnNumber)
        repeat(3) { state = X01Mode.apply(state, miss).state }
        assertEquals(0, state.currentPlayerIndex)
        assertEquals(2, state.turnNumber)

        val early = X01Mode.advanceTurn(X01Mode.apply(game(), miss).state)
        assertEquals(1, early.state.currentPlayerIndex)
        assertEquals(3, early.state.dartsLeft)
        assertEquals(501, early.state.turnStartScore)
    }

    @Test
    fun `bust ends the turn immediately and forfeits remaining darts`() {
        val state = game(X01Rules(startingScore = 121))
            .atScore(0, 3)
        val result = X01Mode.apply(state, single(2))
        assertEquals(1, result.state.currentPlayerIndex)
        assertEquals(3, result.state.dartsLeft)
        assertEquals(1, result.state.dartsThrown)
        assertTrue(result.events.any { it is X01Event.Bust })
    }

    @Test
    fun `bull values do not depend on the stored multiplier`() {
        assertEquals(50, X01Mode.valueOf(DartHit(BoardGeometry.BULL, 1, Ring.INNER_BULL)))
        assertEquals(25, X01Mode.valueOf(DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)))
        assertEquals(60, X01Mode.valueOf(treble(20)))
        assertEquals(40, X01Mode.valueOf(double(20)))
        assertEquals(20, X01Mode.valueOf(single(20)))
    }
}
