package com.darts.core

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WhackAMoleModeTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }

    // Deterministic so the mole order is predictable in assertions.
    private fun game(
        playerCount: Int = 2,
        moles: Int = 8,
        dartsPerTurn: Int = 3,
        hardMode: Boolean = false,
        seed: Int = 7,
    ): WhackAMoleState = WhackAMoleMode.newGame(
        players(playerCount),
        WhackAMoleRules(moles, dartsPerTurn, hardMode),
        Random(seed),
    )

    private fun number(s: WhackAMoleState, index: Int): Int = s.targets[index].number

    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE, SingleBand.LARGE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val bull = DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)
    private val miss = DartHit(null, 0, Ring.MISS)

    @Test
    fun `new game supports one to six unique players`() {
        assertEquals(1, WhackAMoleMode.newGame(players(1), random = Random(1)).players.size)
        assertEquals(6, WhackAMoleMode.newGame(players(6), random = Random(1)).players.size)
        assertThrows(IllegalArgumentException::class.java) {
            WhackAMoleMode.newGame(players(0), random = Random(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WhackAMoleMode.newGame(players(7), random = Random(1))
        }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) {
            WhackAMoleMode.newGame(dupes, random = Random(1))
        }
    }

    @Test
    fun `default list has eight distinct numbers within one to twenty`() {
        val s = WhackAMoleMode.newGame(players(2))
        val numbers = s.targets.map { it.number }
        assertEquals(8, s.targets.size)
        assertEquals(s.rules.moles, s.targets.size)
        assertEquals(8, numbers.toSet().size)
        assertTrue(numbers.all { it in WhackAMoleMode.NUMBERS })
        assertTrue(s.targets.all { it.ring == null })
        assertEquals(0, s.whacked(0))
        assertEquals(3, s.dartsLeft)
        assertFalse(s.finished)
        assertNull(s.winnerId)
    }

    @Test
    fun `generate targets respects size and stays distinct`() {
        val targets = WhackAMoleMode.generateTargets(5, random = Random(42))
        assertEquals(5, targets.size)
        assertEquals(5, targets.map { it.number }.toSet().size)
        assertTrue(targets.all { it.number in 1..20 })
    }

    @Test
    fun `hitting the current number in any ring whacks it`() {
        val s = game()
        val first = number(s, 0)
        assertEquals(1, WhackAMoleMode.apply(s, single(first)).state.whacked(0))
        assertEquals(1, WhackAMoleMode.apply(s, double(first)).state.whacked(0))
        assertEquals(1, WhackAMoleMode.apply(s, treble(first)).state.whacked(0))
    }

    @Test
    fun `wrong numbers misses and the bull consume a dart but do not advance`() {
        val s = game()
        val first = number(s, 0)
        val wrong = WhackAMoleMode.NUMBERS.first { it != first }
        val afterWrong = WhackAMoleMode.apply(s, single(wrong))
        assertEquals(0, afterWrong.state.whacked(0))
        assertEquals(2, afterWrong.state.dartsLeft)

        val afterMiss = WhackAMoleMode.apply(s, miss)
        assertEquals(0, afterMiss.state.whacked(0))
        assertEquals(2, afterMiss.state.dartsLeft)

        val afterBull = WhackAMoleMode.apply(s, bull)
        assertEquals(0, afterBull.state.whacked(0))
        assertFalse(afterBull.state.finished)
    }

    @Test
    fun `must hit them in order`() {
        val s = game()
        val after = WhackAMoleMode.apply(s, single(number(s, 1)))
        assertEquals(0, after.state.whacked(0))
        val one = WhackAMoleMode.apply(s, single(number(s, 0))).state
        val two = WhackAMoleMode.apply(one, single(number(s, 1))).state
        assertEquals(2, two.whacked(0))
    }

    @Test
    fun `turn passes after darts per turn then resets`() {
        val s = game(playerCount = 2, dartsPerTurn = 1)
        val after = WhackAMoleMode.apply(s, miss)
        assertEquals(1, after.state.currentPlayerIndex)
        assertEquals(1, after.state.dartsLeft)
        assertTrue(after.events.any { it is WhackAMoleEvent.TurnChanged })
    }

    @Test
    fun `completing the list ends the game and crowns that player`() {
        val s = game(playerCount = 2, moles = 2)
        val one = WhackAMoleMode.apply(s, single(number(s, 0))).state
        val done = WhackAMoleMode.apply(one, single(number(s, 1)))
        assertTrue(done.state.finished)
        assertEquals(0, done.state.winnerId)
        assertEquals(2, done.state.whacked(0))
        assertEquals(0, done.state.dartsLeft)
        assertTrue(done.events.any { it is WhackAMoleEvent.GameFinished })
    }

    @Test
    fun `first to finish wins even with turns remaining`() {
        val s = game(playerCount = 2, moles = 3, dartsPerTurn = 3)
        var state = s
        for (i in 0 until 3) {
            state = WhackAMoleMode.apply(state, single(number(s, i))).state
        }
        assertTrue(state.finished)
        assertEquals(0, state.winnerId)
        assertEquals(0, state.whacked(1))
    }

    @Test
    fun `solo play completes on clearing the list`() {
        val s = game(playerCount = 1, moles = 4)
        var state = s
        for (i in 0 until 4) {
            state = WhackAMoleMode.apply(state, single(number(s, i))).state
        }
        assertTrue(state.finished)
        assertEquals(0, state.winnerId)
    }

    @Test
    fun `isWhack matches the current target only`() {
        val s = game()
        assertTrue(WhackAMoleMode.isWhack(s, single(number(s, 0))))
        assertFalse(WhackAMoleMode.isWhack(s, single(number(s, 1))))
        assertFalse(WhackAMoleMode.isWhack(s, miss))
    }

    @Test
    fun `early autodarts visit advances the turn`() {
        val s = game(playerCount = 3, dartsPerTurn = 3)
        val after = WhackAMoleMode.advanceTurn(s)
        assertEquals(1, after.state.currentPlayerIndex)
        assertEquals(3, after.state.dartsLeft)
        assertTrue(after.events.any { it is WhackAMoleEvent.TurnChanged })
    }

    @Test
    fun `finished games ignore further darts`() {
        val s = game(playerCount = 1, moles = 1)
        val done = WhackAMoleMode.apply(s, single(number(s, 0))).state
        assertTrue(done.finished)
        val again = WhackAMoleMode.apply(done, miss)
        assertEquals(done, again.state)
        assertTrue(again.events.isEmpty())
    }

    @Test
    fun `hard mode plants one or two ring specific moles`() {
        // Across several seeds, hard mode always yields 1..2 specials, never more.
        for (seed in 1..40) {
            val s = game(moles = 8, hardMode = true, seed = seed)
            val specials = s.targets.filter { it.ring != null }
            assertTrue("expected at least one special (seed=$seed)", specials.isNotEmpty())
            assertTrue("too many specials (seed=$seed)", specials.size <= 2)
            assertTrue(
                "special rings must be double or treble (seed=$seed)",
                specials.all { it.ring == Ring.DOUBLE || it.ring == Ring.TREBLE },
            )
        }
    }

    @Test
    fun `hard mode single mole still allowed`() {
        val s = game(moles = 1, hardMode = true, seed = 3)
        assertEquals(1, s.targets.size)
        assertEquals(1, s.targets.filter { it.ring != null }.size)
    }

    @Test
    fun `ring specific mole requires the exact ring`() {
        val base = game(playerCount = 1, moles = 2)
        val s = base.copy(targets = listOf(MoleTarget(20, Ring.TREBLE), MoleTarget(5)))
        // Single on the treble-only 20 does not whack.
        val singleHit = WhackAMoleMode.apply(s, single(20))
        assertEquals(0, singleHit.state.whacked(0))
        // Double on it also does not.
        val doubleHit = WhackAMoleMode.apply(s, double(20))
        assertEquals(0, doubleHit.state.whacked(0))
        // Only the treble counts.
        val trebleHit = WhackAMoleMode.apply(s, treble(20))
        assertEquals(1, trebleHit.state.whacked(0))
        assertFalse(trebleHit.state.finished)
    }

    @Test
    fun `non special mole accepts any ring`() {
        val base = game(playerCount = 1, moles = 2)
        val s = base.copy(targets = listOf(MoleTarget(20), MoleTarget(5, Ring.DOUBLE)))
        assertEquals(1, WhackAMoleMode.apply(s, double(20)).state.whacked(0))
        assertEquals(1, WhackAMoleMode.apply(s, treble(20)).state.whacked(0))
    }

    @Test
    fun `mole target label reflects required ring`() {
        assertEquals("20", MoleTarget(20).label)
        assertEquals("D20", MoleTarget(20, Ring.DOUBLE).label)
        assertEquals("T20", MoleTarget(20, Ring.TREBLE).label)
    }

    @Test
    fun `clearing a hard list needs the treble finish`() {
        val base = game(playerCount = 1, moles = 2)
        val s = base.copy(targets = listOf(MoleTarget(7), MoleTarget(15, Ring.DOUBLE)))
        val one = WhackAMoleMode.apply(s, single(7)).state
        val wrongRing = WhackAMoleMode.apply(one, treble(15))
        assertFalse(wrongRing.state.finished)
        val done = WhackAMoleMode.apply(one, double(15))
        assertTrue(done.state.finished)
        assertEquals(0, done.state.winnerId)
    }
}
