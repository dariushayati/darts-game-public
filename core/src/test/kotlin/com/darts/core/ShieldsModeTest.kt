package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldsModeTest {

    private val miss = DartHit(null, 0, Ring.MISS)

    private fun players(n: Int) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(n: Int = 2) =
        ShieldsMode.newGame(players(n), GameRules(dartsPerPlayer = 300))

    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)

    private fun applyAll(state: GameState, vararg hits: DartHit): GameState =
        hits.fold(state) { s, hit -> ShieldsMode.apply(s, hit).state }

    /** Plays exactly one full turn, padding with misses. */
    private fun fullTurn(state: GameState, vararg hits: DartHit): GameState {
        var s = state
        val start = s.currentPlayerIndex
        var index = 0
        var guard = 0
        while (s.currentPlayerIndex == start && !s.finished && guard++ < 12) {
            s = ShieldsMode.apply(s, hits.getOrElse(index++) { miss }).state
        }
        return s
    }

    @Test
    fun `new game starts with every target neutral`() {
        val s = game()
        assertEquals(21, s.sectors.size)
        assertTrue(s.sectors.values.all { it.ownerId == null && it.shields == 0 && !it.locked })
        assertEquals(0, s.currentPlayerIndex)
        assertEquals(GameState.DARTS_PER_TURN, s.dartsLeft)
        assertEquals(1, s.turnNumber)
        assertFalse(s.finished)
    }

    @Test
    fun `supports one to six players with unique ids`() {
        assertEquals(1, ShieldsMode.newGame(players(1)).players.size)
        assertThrows(IllegalArgumentException::class.java) { ShieldsMode.newGame(players(0)) }
        assertThrows(IllegalArgumentException::class.java) { ShieldsMode.newGame(players(7)) }
        val dupes = listOf(Player(0, "a", 0), Player(0, "b", 1))
        assertThrows(IllegalArgumentException::class.java) { ShieldsMode.newGame(dupes) }
    }

    @Test
    fun `one player finishes after their own dart budget`() {
        val finished = applyAll(
            ShieldsMode.newGame(players(1), GameRules(dartsPerPlayer = 3)),
            miss, miss, miss,
        )
        assertTrue(finished.finished)
        assertEquals(0, finished.winnerId)
    }

    @Test
    fun `single claims a neutral target with one shield`() {
        val s = applyAll(game(), single(20))
        assertEquals(0, s.sector(20).ownerId)
        assertEquals(1, s.sector(20).shields)
        assertFalse(s.sector(20).locked)
        assertEquals(GameState.DARTS_PER_TURN - 1, s.dartsLeft)
    }

    @Test
    fun `double claims a neutral target with two shields`() {
        val s = applyAll(game(), double(20))
        assertEquals(0, s.sector(20).ownerId)
        assertEquals(2, s.sector(20).shields)
    }

    @Test
    fun `treble locks a neutral target immediately`() {
        val r = ShieldsMode.apply(game(), treble(20))
        assertEquals(3, r.state.sector(20).shields)
        assertTrue(r.state.sector(20).locked)
        assertTrue(r.events.any { it is GameEvent.SectorLocked })
    }

    @Test
    fun `hitting your own target adds shields and clamps at three`() {
        val s = applyAll(game(), double(20), double(20))
        assertEquals(0, s.sector(20).ownerId)
        assertEquals(3, s.sector(20).shields)
        assertTrue(s.sector(20).locked)
    }

    @Test
    fun `opponent removes a shield and keeps the target`() {
        var s = fullTurn(game(), double(20))
        s = applyAll(s, single(20))
        assertEquals(0, s.sector(20).ownerId)
        assertEquals(1, s.sector(20).shields)
    }

    @Test
    fun `opponent treble neutralises a two-shield target and surplus is wasted`() {
        var s = fullTurn(game(), double(20))
        s = applyAll(s, treble(20))
        assertNull(s.sector(20).ownerId)
        assertEquals(0, s.sector(20).shields)
        assertFalse(s.sector(20).locked)
    }

    @Test
    fun `locked targets ignore opponent hits`() {
        val s = fullTurn(game(), treble(20))
        val r = ShieldsMode.apply(s, treble(20))
        assertEquals(0, r.state.sector(20).ownerId)
        assertEquals(3, r.state.sector(20).shields)
        assertTrue(r.state.sector(20).locked)
        assertTrue(r.events.any { it is GameEvent.BlockedByLock })
    }

    @Test
    fun `a miss consumes a dart and changes nothing`() {
        val s = applyAll(game(), miss)
        assertEquals(GameState.DARTS_PER_TURN - 1, s.dartsLeft)
        assertTrue(s.sectors.values.all { it.ownerId == null })
    }

    @Test
    fun `turn advances after three darts`() {
        val s = fullTurn(game(), single(20))
        assertEquals(1, s.currentPlayerIndex)
        assertEquals(GameState.DARTS_PER_TURN, s.dartsLeft)
        assertEquals(1, s.turnNumber)
    }

    @Test
    fun `a full round increments the turn number`() {
        var s = fullTurn(game(), single(20))
        s = fullTurn(s, single(19))
        assertEquals(0, s.currentPlayerIndex)
        assertEquals(2, s.turnNumber)
    }

    @Test
    fun `six players rotate in order`() {
        var s = game(6)
        for (i in 0 until 6) {
            s = fullTurn(s, miss)
            assertEquals((i + 1) % 6, s.currentPlayerIndex)
        }
        assertEquals(2, s.turnNumber)
    }

    @Test
    fun `the bull is one target worth one shield`() {
        var state = applyAll(game(), DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL))
        assertEquals(1, state.sector(BoardGeometry.BULL).shields)
        state = applyAll(state, DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL))
        assertEquals(2, state.sector(BoardGeometry.BULL).shields)
        state = applyAll(state, DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL))
        assertTrue(state.sector(BoardGeometry.BULL).locked)
        assertEquals(0, state.sector(BoardGeometry.BULL).ownerId)
    }

    @Test
    fun `locking the last target finishes the game and picks the most locked`() {
        val p = players(2)
        val nearFull = ShieldsMode.newGame(p).copy(
            sectors = BoardGeometry.TARGETS.associateWith { target ->
                if (target == 20) SectorState(ownerId = 1, shields = 2) else SectorState(ownerId = 0, shields = 3)
            },
            currentPlayerIndex = 1,
        )
        val r = ShieldsMode.apply(nearFull, treble(20))
        assertTrue(r.state.finished)
        assertEquals(0, r.state.winnerId)
        assertTrue(r.events.any { it is GameEvent.GameFinished })
    }

    @Test
    fun `level locked counts are a draw`() {
        val p = players(3)
        val sectors = BoardGeometry.TARGETS.withIndex().associate { (i, target) ->
            target to SectorState(ownerId = i % 3, shields = 3)
        }
        assertNull(ShieldsMode.winner(sectors, p))
    }

    @Test
    fun `each claimed number is worth one point`() {
        val p = players(2)
        val sectors = mapOf(
            1 to SectorState(ownerId = 0, shields = 3),
            2 to SectorState(ownerId = 1, shields = 1),
            3 to SectorState(ownerId = 1, shields = 1),
        )
        assertEquals(1, ShieldsMode.winner(sectors, p))
    }

    @Test
    fun `winner breaks owned and locked ties by total shields`() {
        val p = players(2)
        val sectors = mapOf(
            1 to SectorState(ownerId = 0, shields = 2),
            2 to SectorState(ownerId = 0, shields = 2),
            3 to SectorState(ownerId = 1, shields = 1),
            4 to SectorState(ownerId = 1, shields = 1),
        )
        assertEquals(0, ShieldsMode.winner(sectors, p))
    }

    @Test
    fun `locked sectors are a tiebreak, not extra points`() {
        val p = players(2)
        val sectors = mapOf(
            1 to SectorState(ownerId = 0, shields = 3),
            2 to SectorState(ownerId = 1, shields = 1),
        )
        assertEquals(0, ShieldsMode.winner(sectors, p))
    }

    @Test
    fun `finished games ignore further throws`() {
        val finished = ShieldsMode.newGame(players(2)).copy(finished = true, winnerId = 0)
        val r = ShieldsMode.apply(finished, treble(20))
        assertSame(finished, r.state)
        assertTrue(r.events.isEmpty())
    }
}

class GameRulesTest {
    private fun players(n: Int = 2) = List(n) { Player(it, "P${it + 1}", it) }
    private fun game(rules: GameRules, n: Int = 2) = ShieldsMode.newGame(players(n), rules)
    private fun single(n: Int) = DartHit(n, 1, Ring.SINGLE)
    private fun double(n: Int) = DartHit(n, 2, Ring.DOUBLE)
    private fun treble(n: Int) = DartHit(n, 3, Ring.TREBLE)
    private val miss = DartHit(null, 0, Ring.MISS)

    @Test
    fun `game ends after every player has used their darts`() {
        val rules = GameRules(dartsPerPlayer = 3, endOnBoardFull = false)
        var state = game(rules)
        repeat(5) { state = ShieldsMode.apply(state, single(20)).state }
        assertFalse(state.finished)
        state = ShieldsMode.apply(state, single(19)).state
        assertTrue(state.finished)
        assertEquals(6, state.dartsThrown)
        assertEquals(3, state.dartsByPlayer[0])
        assertEquals(3, state.dartsByPlayer[1])
    }

    @Test
    fun `misses count toward the dart budget`() {
        val rules = GameRules(dartsPerPlayer = 3, endOnBoardFull = false)
        var state = game(rules)
        repeat(6) { state = ShieldsMode.apply(state, miss).state }
        assertTrue(state.finished)
    }

    @Test
    fun `doubles add two and trebles lock at three`() {
        val rules = GameRules(endOnBoardFull = false, dartsPerPlayer = 99)
        var state = game(rules)
        state = ShieldsMode.apply(state, double(20)).state
        assertEquals(2, state.sector(20).shields)
        assertFalse(state.isLocked(20))
        state = ShieldsMode.apply(state, DartHit(19, 3, Ring.TREBLE)).state
        assertTrue(state.isLocked(19))
        assertEquals(3, state.sector(19).shields)
    }

    @Test
    fun `multiplier levels are configurable`() {
        val rules = GameRules(
            singleLevels = 1,
            doubleLevels = 3,
            tripleLevels = 4,
            endOnBoardFull = false,
            dartsPerPlayer = 99,
        )
        var state = game(rules)
        state = ShieldsMode.apply(state, single(20)).state
        assertEquals(1, state.sector(20).shields)
        state = ShieldsMode.apply(state, double(19)).state
        assertEquals(3, state.sector(19).shields)
        state = ShieldsMode.apply(state, DartHit(18, 3, Ring.TREBLE)).state
        assertEquals(4, state.sector(18).shields)
        assertTrue(state.isLocked(18))
    }

    @Test
    fun `dart budget must be a multiple of three`() {
        assertThrows(IllegalArgumentException::class.java) {
            GameRules(dartsPerPlayer = 10)
        }
    }

    @Test
    fun `doubles and trebles each cost one go`() {
        val rules = GameRules(dartsPerPlayer = 15, endOnBoardFull = false)
        var state = game(rules)
        state = ShieldsMode.apply(state, double(20)).state
        assertEquals(1, state.dartsByPlayer[0])
        assertEquals(2, state.dartsLeft)
        state = ShieldsMode.apply(state, treble(19)).state
        assertEquals(2, state.dartsByPlayer[0])
        assertEquals(1, state.dartsLeft)
        assertEquals(0, state.currentPlayerIndex)
    }

    @Test
    fun `a triple is a bonus worth three shields for one go`() {
        val rules = GameRules(dartsPerPlayer = 15, endOnBoardFull = false)
        var state = game(rules)
        state = ShieldsMode.apply(state, treble(20)).state
        assertTrue(state.isLocked(20))
        assertEquals(3, state.sector(20).shields)
        assertEquals(1, state.dartsByPlayer[0])
        assertEquals(2, state.dartsLeft)
        assertEquals(0, state.currentPlayerIndex)
    }

    @Test
    fun `three triples can be thrown in one turn`() {
        val rules = GameRules(dartsPerPlayer = 15, endOnBoardFull = false)
        var state = game(rules)
        listOf(20, 19, 18).forEach { state = ShieldsMode.apply(state, treble(it)).state }
        assertEquals(3, state.dartsByPlayer[0])
        assertTrue(state.isLocked(20))
        assertTrue(state.isLocked(19))
        assertTrue(state.isLocked(18))
        assertEquals(1, state.currentPlayerIndex)
        assertEquals(3, state.dartsLeft)
    }

    @Test
    fun `shields to lock is configurable`() {
        val rules = GameRules(tripleLevels = 2, endOnBoardFull = false, dartsPerPlayer = 99)
        val state = ShieldsMode.apply(game(rules), double(20)).state
        assertTrue(state.isLocked(20))
        assertEquals(2, state.sector(20).shields)
    }

    @Test
    fun `board full can be disabled as an end condition`() {
        val rules = GameRules(dartsPerPlayer = 99, endOnBoardFull = false)
        val nearFull = ShieldsMode.newGame(players(), rules).copy(
            sectors = BoardGeometry.TARGETS.associateWith { target ->
                if (target == 20) SectorState(ownerId = 1, shields = 2) else SectorState(ownerId = 0, shields = 3)
            },
            currentPlayerIndex = 1,
        )
        val result = ShieldsMode.apply(nearFull, DartHit(20, 3, Ring.TREBLE))
        assertTrue(result.state.sectors.values.all { it.shields >= 3 })
        assertFalse(result.state.finished)
    }
}
