package com.darts.app

import com.darts.core.AroundTheClockMode
import com.darts.core.CheckoutDifficulty
import com.darts.core.CheckoutMode
import com.darts.core.CheckoutRules
import com.darts.core.CheckoutState
import com.darts.core.CricketMode
import com.darts.core.DartHit
import com.darts.core.GunslingerMode
import com.darts.core.Ring
import com.darts.core.ShieldsMode
import com.darts.core.InvadersMode
import com.darts.core.LavaMode
import com.darts.core.Player
import com.darts.core.RingRunMode
import com.darts.core.RobinHoodMode
import com.darts.core.WhackAMoleMode
import com.darts.core.X01Mode
import com.darts.core.X01OutRule
import com.darts.core.X01Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MirrorViewsTest {

    private val players = listOf(Player(1, "Ana", 0), Player(2, "Ben", 1))
    private val treble20 = DartHit(20, 3, Ring.TREBLE)

    @Test
    fun shieldsViewCarriesScoresAndLocks() {
        val state = ShieldsMode.apply(ShieldsMode.newGame(players), treble20).state
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("SHIELDS", view.gameType)
        assertEquals("SHIELDS", view.title)
        assertEquals(2, view.players.size)
        assertEquals("1", view.players.first().score)
        assertEquals("1 locked", view.players.first().detail)
    }

    @Test
    fun invadersViewCarriesScoresAndTargets() {
        val view = InvadersMode.newGame(players).toMirrorView(null, emptyList(), 0)
        assertEquals("INVADERS", view.gameType)
        assertEquals("INVADERS", view.title)
        assertEquals(2, view.players.size)
        assertEquals("Ana", view.players.first().name)
        assertTrue(view.players.first().active)
        assertTrue(view.targetLabels.isNotEmpty())
        assertEquals(60, view.alienCounts.values.sum())
        assertFalse(view.finished)
    }

    @Test
    fun lavaViewMarksLavaNumbersAsDanger() {
        val state = LavaMode.newGame(players, random = Random(1))
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("LAVA", view.gameType)
        assertEquals("BOARD IS LAVA", view.title)
        assertEquals(state.lavaNumbers.sorted(), view.dangerNumbers)
        assertTrue(view.dangerNumbers.isNotEmpty())
        assertTrue(view.players.all { it.detail == "lives" })
    }

    @Test
    fun ringRunViewShowsRoundAndTargets() {
        val view = RingRunMode.newGame(players).toMirrorView(null, emptyList(), 0)
        assertEquals("RING_RUN", view.gameType)
        assertEquals("RING RUN", view.title)
        assertEquals("ROUND 1/4", view.modeLabel)
        assertEquals(0, view.ringRoundIndex)
        assertTrue(view.targetLabels.isNotEmpty())
    }

    @Test
    fun robinHoodViewShowsRound() {
        val view = RobinHoodMode.newGame(players).toMirrorView(null, emptyList(), 0)
        assertEquals("ROBIN_HOOD", view.gameType)
        assertEquals("ROBIN HOOD", view.title)
        assertEquals("ROUND 1/5", view.modeLabel)
    }

    @Test
    fun cricketViewShowsPointsAndOpenTargets() {
        val state = CricketMode.apply(CricketMode.newGame(players), treble20).state
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("CRICKET", view.gameType)
        assertEquals("CRICKET", view.title)
        assertEquals("0", view.players.first().score)
        assertEquals("1/7 closed", view.players.first().detail)
        assertEquals(6, view.highlightNumbers.size)
        assertEquals(7, view.targetLabels.size)
        assertEquals(3, view.cricketMarks.getValue(1)?.get(20))
    }

    @Test
    fun gunslingerViewShowsLiveTargetAndScore() {
        val state = GunslingerMode.newGame(players).copy(target = 17)
        val view = state.toMirrorView(
            lastTarget = null,
            recentHits = emptyList(),
            flashTick = 0,
            live = true,
            potentialPoints = 900,
        )
        assertEquals("GUNSLINGER", view.gameType)
        assertEquals("GUNSLINGER", view.title)
        assertEquals("TARGET LIVE", view.statusLabel)
        assertEquals(listOf(17), view.highlightNumbers)
        assertEquals(setOf(2, 3), view.secondaryHighlightNumbers.toSet())
        assertTrue(view.targetLabels.any { it == "TARGET 17" })
        assertTrue(view.targetLabels.any { it == "NEXT 900" })
        assertTrue(view.targetLabels.any { it == "BANKED 0" })
    }

    @Test
    fun gunslingerViewShowsTakeoutWait() {
        val state = GunslingerMode.newGame(players).copy(target = 17)
        val view = state.toMirrorView(
            lastTarget = null,
            recentHits = emptyList(),
            flashTick = 0,
            live = false,
            potentialPoints = 900,
            waitingForTakeout = true,
        )
        assertEquals("TAKE OUT DARTS", view.statusLabel)
        assertTrue(view.targetLabels.any { it == "TAKE OUT DARTS" })
    }

    @Test
    fun whackAMoleViewShowsTargetList() {
        val state = WhackAMoleMode.newGame(players, random = Random(1))
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("WHACK_A_MOLE", view.gameType)
        assertEquals("WHACK A MOLE", view.title)
        assertEquals(state.targets.size, view.targetLabels.size)
        assertEquals(state.target, view.moleTarget)
        assertEquals("0/${state.targets.size}", view.players.first().score)
    }

    @Test
    fun aroundTheClockViewShowsTargetAndProgress() {
        val state = AroundTheClockMode.newGame(players)
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("AROUND_THE_CLOCK", view.gameType)
        assertEquals("AROUND THE CLOCK", view.title)
        assertEquals("TARGET 1", view.modeLabel)
        assertEquals(listOf(1), view.highlightNumbers)
        assertEquals(21, view.targetLabels.size)
        assertEquals("0", view.players.first().score)
        assertEquals("NEXT 1", view.players.first().detail)
        assertTrue(view.players.first().active)
        assertFalse(view.finished)
    }

    @Test
    fun checkoutViewShowsPathAndCurrentDart() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 60 }
        val state = CheckoutState(
            players = listOf(players.first()),
            rules = CheckoutRules(CheckoutDifficulty.EASY, checkoutsPerSession = 1),
            order = listOf(path),
            dartsByPlayer = mapOf(1 to 0),
        )
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("CHECKOUT", view.gameType)
        assertEquals("CHECKOUT", view.title)
        assertEquals("EASY · 1/1", view.modeLabel)
        assertEquals("0/1", view.players.first().score)
        assertEquals("NEXT 20", view.players.first().detail)
        assertEquals(listOf(20), view.highlightNumbers)
        assertEquals(listOf("20", "D20"), view.targetLabels)
        assertEquals("20", view.checkoutTarget?.label)
        assertFalse(view.finished)
    }

    @Test
    fun x01ViewShowsScoreLegsAndRules() {
        val state = X01Mode.newGame(
            players,
            X01Rules(startingScore = 301, outRule = X01OutRule.MASTER),
        )
        val view = state.toMirrorView(null, emptyList(), 0)
        assertEquals("X01", view.gameType)
        assertEquals("X01", view.title)
        assertEquals("301 · MASTER OUT · LEG 1", view.modeLabel)
        assertEquals("301", view.players.first().score)
        assertEquals("0 legs", view.players.first().detail)
        assertTrue(view.players.first().active)
        assertTrue(view.targetLabels.contains("LEG 1"))
        assertTrue(view.targetLabels.contains("STRAIGHT IN"))
        assertTrue(view.targetLabels.contains("MASTER OUT"))
        assertTrue(view.targetLabels.contains("FIRST TO 1 LEG"))
    }
}
