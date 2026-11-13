package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CheckoutModeTest {

    private val player = Player(id = 0, name = "Ana", colorIndex = 0)

    private fun stateFor(
        vararg paths: CheckoutPath,
    ) = CheckoutState(
        players = listOf(player),
        order = paths.toList(),
        dartsByPlayer = mapOf(0 to 0),
    )

    @Test
    fun everyEasyPathIsAValidTwoDartFinish() {
        CheckoutMode.EASY_PATHS.forEach { path ->
            assertEquals(2, path.darts.size)
            assertEquals(path.score, path.darts.sumOf { it.value })
            assertTrue("${path.score} must finish on a double or bull", path.darts.last().isCheckout)
        }
        assertTrue(CheckoutMode.EASY_PATHS.any { it.score == 60 })
        assertTrue(CheckoutMode.EASY_PATHS.any { it.score == 100 })
    }

    @Test
    fun everyHardPathIsAValidThreeDartFinish() {
        CheckoutMode.HARD_PATHS.forEach { path ->
            assertEquals(3, path.darts.size)
            assertEquals(path.score, path.darts.sumOf { it.value })
            assertTrue("${path.score} must finish on a double or bull", path.darts.last().isCheckout)
        }
        assertTrue(CheckoutMode.HARD_PATHS.any { it.score == 170 })
        assertTrue(CheckoutMode.HARD_PATHS.any { it.score == 121 })
        assertTrue(CheckoutMode.HARD_PATHS.none { it.score in setOf(159, 162, 163, 165, 166, 168, 169) })
    }

    @Test
    fun newGamePicksAUniqueSessionFromTheChosenDifficulty() {
        val easy = CheckoutMode.newGame(
            player,
            CheckoutRules(CheckoutDifficulty.EASY, checkoutsPerSession = 10),
            Random(1),
        )
        val hard = CheckoutMode.newGame(
            player,
            CheckoutRules(CheckoutDifficulty.HARD, checkoutsPerSession = 10),
            Random(2),
        )

        assertEquals(10, easy.order.size)
        assertEquals(10, hard.order.size)
        assertEquals(10, easy.order.map { it.score }.distinct().size)
        assertEquals(10, hard.order.map { it.score }.distinct().size)
        assertTrue(easy.order.all { it.darts.size == 2 })
        assertTrue(hard.order.all { it.darts.size == 3 })
    }

    @Test
    fun hittingThePathInOrderCompletesTheCheckout() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 60 }
        var state = stateFor(path, CheckoutMode.EASY_PATHS.first { it.score == 100 })

        state = CheckoutMode.apply(state, DartHit(20, 1, Ring.SINGLE)).state
        assertEquals(1, state.step)
        assertEquals(2, state.dartsLeft)

        val reduction = CheckoutMode.apply(state, DartHit(20, 2, Ring.DOUBLE))
        state = reduction.state

        assertEquals(1, state.completed)
        assertEquals(0, state.step)
        assertEquals(3, state.dartsLeft)
        assertTrue(reduction.events.any { it is CheckoutEvent.CheckoutCompleted })
    }

    @Test
    fun aWrongDartKeepsTheCurrentTarget() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 60 }
        var state = stateFor(path)

        state = CheckoutMode.apply(state, DartHit(20, 1, Ring.SINGLE)).state
        assertEquals(1, state.step)

        state = CheckoutMode.apply(state, DartHit(19, 2, Ring.DOUBLE)).state
        assertEquals(1, state.step)
        assertEquals(1, state.dartsLeft)

        val reduction = CheckoutMode.apply(state, DartHit(20, 2, Ring.DOUBLE))
        assertTrue(reduction.state.finished)
        assertTrue(reduction.events.any { it is CheckoutEvent.CheckoutCompleted })
    }

    @Test
    fun threeMissesStartTheSameCheckoutAgain() {
        val path = CheckoutMode.HARD_PATHS.first { it.score == 170 }
        var state = stateFor(path)

        repeat(3) {
            state = CheckoutMode.apply(state, DartHit(null, 0, Ring.MISS)).state
        }

        assertEquals(0, state.completed)
        assertEquals(0, state.step)
        assertEquals(3, state.dartsLeft)
        assertEquals(3, state.dartsThrown)
    }

    @Test
    fun completingTheLastCheckoutFinishesTheSession() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 60 }
        var state = stateFor(path)

        state = CheckoutMode.apply(state, DartHit(20, 1, Ring.SINGLE)).state
        val reduction = CheckoutMode.apply(state, DartHit(20, 2, Ring.DOUBLE))

        assertTrue(reduction.state.finished)
        assertEquals(0, reduction.state.winnerId)
        assertEquals(1, reduction.state.completed)
        assertTrue(reduction.events.any { it is CheckoutEvent.GameFinished })
    }

    @Test
    fun bullMustBeTheInnerBull() {
        val bull = CheckoutDart(BoardGeometry.BULL, Ring.INNER_BULL)

        assertTrue(bull.matches(DartHit(BoardGeometry.BULL, 2, Ring.INNER_BULL)))
        assertFalse(bull.matches(DartHit(BoardGeometry.BULL, 1, Ring.OUTER_BULL)))
        assertFalse(bull.matches(DartHit(20, 1, Ring.SINGLE)))
    }

    @Test
    fun missThenBothPathHitsCompletesTheCheckout() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 52 }
        var state = stateFor(path, CheckoutMode.EASY_PATHS.first { it.score == 60 })

        state = CheckoutMode.apply(state, DartHit(null, 0, Ring.MISS)).state
       state = CheckoutMode.apply(state, DartHit(12, 1, Ring.SINGLE)).state
        val reduction = CheckoutMode.apply(state, DartHit(20, 2, Ring.DOUBLE))

        assertFalse(reduction.state.finished)
        assertEquals(1, reduction.state.completed)
        assertEquals(0, reduction.state.step)
        assertEquals(3, reduction.state.dartsLeft)
    }

    @Test
    fun advanceTurnKeepsPathProgress() {
        val path = CheckoutMode.HARD_PATHS.first { it.score == 170 }
        var state = stateFor(path)
        state = CheckoutMode.apply(state, DartHit(20, 3, Ring.TREBLE)).state

        state = CheckoutMode.advanceTurn(state).state

        assertEquals(0, state.completed)
        assertEquals(1, state.step)
        assertEquals(3, state.dartsLeft)
    }

    @Test
    fun threeDartsKeepTheCurrentPathProgress() {
        val path = CheckoutMode.HARD_PATHS.first { it.score == 170 }
        var state = stateFor(path)

        state = CheckoutMode.apply(state, DartHit(20, 3, Ring.TREBLE)).state
        state = CheckoutMode.apply(state, DartHit(null, 0, Ring.MISS)).state
        state = CheckoutMode.apply(state, DartHit(null, 0, Ring.MISS)).state

        assertEquals(0, state.completed)
        assertEquals(1, state.step)
        assertEquals(3, state.dartsLeft)
    }
}
