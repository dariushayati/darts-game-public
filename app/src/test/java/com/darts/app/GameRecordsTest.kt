package com.darts.app

import com.darts.core.AroundTheClockMode
import com.darts.core.CheckoutDifficulty
import com.darts.core.CheckoutMode
import com.darts.core.CheckoutRules
import com.darts.core.CheckoutState
import com.darts.core.CricketMode
import com.darts.core.DartHit
import com.darts.core.GunslingerMode
import com.darts.core.GunslingerRules
import com.darts.core.GameRules
import com.darts.core.InvadersMode
import com.darts.core.InvadersRules
import com.darts.core.InvadersState
import com.darts.core.LavaFinishReason
import com.darts.core.LavaMode
import com.darts.core.LavaRules
import com.darts.core.LavaState
import com.darts.core.MoleTarget
import com.darts.core.Player
import com.darts.core.Ring
import com.darts.core.RingRunMode
import com.darts.core.RingRunState
import com.darts.core.RobinHoodMode
import com.darts.core.RobinHoodState
import com.darts.core.GameState
import com.darts.core.ShieldsMode
import com.darts.core.WhackAMoleMode
import com.darts.core.WhackAMoleState
import com.darts.core.X01Mode
import com.darts.core.X01Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class GameRecordsTest {

    private val players = listOf(Player(1, "Ana", 0), Player(2, "Ben", 1))
    private val id = "match-1"
    private val finishedAt = 1234L
    private val treble20 = DartHit(20, 3, Ring.TREBLE)

    @Test
    fun shieldsRecordMapsScoreDartsAndLocks() {
        val state = ShieldsMode.apply(ShieldsMode.newGame(players), treble20).state
        val record = shieldsMatchRecord(state, id, finishedAt)
        assertEquals(GameType.SHIELDS.name, record.game)
        assertEquals("SHIELDS", record.gameTitle)
        assertEquals(id, record.id)
        assertEquals(finishedAt, record.finishedAt)
        assertEquals(1, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("1 locked", record.players.first().detail)
        assertEquals("Dart limit reached", record.endReason)
    }

    @Test
    fun invadersRecordMapsScoresAndKills() {
        val state = InvadersMode.apply(InvadersMode.newGame(players), treble20).state
        val record = invadersMatchRecord(state, id, finishedAt)
        assertEquals(GameType.INVADERS.name, record.game)
        assertEquals(9, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("3 kills", record.players.first().detail)
        assertEquals("Dart limit reached", record.endReason)
    }

    @Test
    fun lavaRecordMapsLivesAndDarts() {
        val start = LavaMode.newGame(players, random = Random(1))
        val lava = start.lavaNumbers.first()
        val state = LavaMode.apply(start, DartHit(lava, 1, Ring.SINGLE), Random(1)).state
        val record = lavaMatchRecord(state, id, finishedAt)
        assertEquals(GameType.LAVA.name, record.game)
        assertEquals(start.rules.lives - 1, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("Survived", record.players.first().detail)
        assertNull(record.endReason)
    }

    @Test
    fun ringRunRecordMapsScores() {
        val state = RingRunMode.apply(RingRunMode.newGame(players), DartHit(20, 2, Ring.DOUBLE)).state
        val record = ringRunMatchRecord(state, id, finishedAt)
        assertEquals(GameType.RING_RUN.name, record.game)
        assertEquals(1, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("Run complete", record.endReason)
    }

    @Test
    fun robinHoodRecordMapsScores() {
        val state = RobinHoodMode.apply(RobinHoodMode.newGame(players), treble20).state
        val record = robinHoodMatchRecord(state, id, finishedAt)
        assertEquals(GameType.ROBIN_HOOD.name, record.game)
        assertEquals(4, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("Dart limit reached", record.endReason)
    }

    @Test
    fun cricketRecordMapsPointsAndClosedTargets() {
        val state = CricketMode.apply(CricketMode.newGame(players), treble20).state
        val record = cricketMatchRecord(state, id, finishedAt)
        assertEquals(GameType.CRICKET.name, record.game)
        assertEquals("CRICKET", record.gameTitle)
        assertEquals(0, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("1/7 closed", record.players.first().detail)
        assertEquals("Closed out", record.endReason)
    }

    @Test
    fun gunslingerRecordMapsPointsHitsAndDarts() {
        var state = GunslingerMode.newGame(
            listOf(players.first()),
            GunslingerRules(rounds = 1),
        ).copy(target = 20)
        state = GunslingerMode.apply(state, treble20, 1000).state
        state = GunslingerMode.apply(state, treble20, 2000).state
        state = GunslingerMode.apply(state, treble20, 3000).state
        val record = gunslingerMatchRecord(state, id, finishedAt)
        assertEquals(GameType.GUNSLINGER.name, record.game)
        assertEquals("GUNSLINGER", record.gameTitle)
        assertEquals(2700, record.players.first().score)
        assertEquals(3, record.players.first().darts)
        assertEquals("3 hits", record.players.first().detail)
        assertEquals("1 rounds complete", record.endReason)
    }

    @Test
    fun finishedRecordsCarryWinnerAndReason() {
        val invaders = invadersMatchRecord(
            InvadersState(
                players = players,
                rules = InvadersRules(),
                aliens = InvadersState.NUMBERS.associateWith { 0 },
                finished = true,
                winnerId = 1,
            ),
            id,
            finishedAt,
        )
        assertEquals(1, invaders.winnerId)
        assertEquals("All aliens cleared", invaders.endReason)

        val lava = lavaMatchRecord(
            LavaState(
                players = players,
                rules = LavaRules(),
                finished = true,
                winnerId = 1,
                finishReason = LavaFinishReason.LAST_STANDING,
            ),
            id,
            finishedAt,
        )
        assertEquals(1, lava.winnerId)
        assertEquals("Last player standing", lava.endReason)

        val ringRun = ringRunMatchRecord(
            RingRunState(players = players, finished = true, winnerId = 1),
            id,
            finishedAt,
        )
        assertEquals(1, ringRun.winnerId)
        assertEquals("Run complete", ringRun.endReason)

        val robinHood = robinHoodMatchRecord(
            RobinHoodState(players = players, finished = true, winnerId = 1),
            id,
            finishedAt,
        )
        assertEquals(1, robinHood.winnerId)
        assertEquals("Dart limit reached", robinHood.endReason)

        val whack = whackAMoleMatchRecord(
            WhackAMoleState(
                players = listOf(players.first()),
                finished = true,
                winnerId = 1,
            ),
            id,
            finishedAt,
        )
        assertEquals(1, whack.winnerId)
        assertEquals("Cleared 8 moles", whack.endReason)

        val shields = shieldsMatchRecord(
            GameState(
                players = players,
                sectors = emptyMap(),
                rules = GameRules(dartsPerPlayer = 0, endOnBoardFull = false),
                finished = true,
                winnerId = 1,
            ),
            id,
            finishedAt,
        )
        assertEquals(1, shields.winnerId)
        assertEquals("Dart limit reached", shields.endReason)
    }

    @Test
    fun whackAMoleRecordMapsProgress() {
        val start = WhackAMoleState(
            players = players,
            targets = listOf(MoleTarget(20), MoleTarget(19)),
        )
        val state = WhackAMoleMode.apply(start, treble20).state
        val record = whackAMoleMatchRecord(state, id, finishedAt)
        assertEquals(GameType.WHACK_A_MOLE.name, record.game)
        assertEquals(1, record.players.first().score)
        assertEquals(0, record.players.first().darts)
        assertEquals("1/8 moles", record.players.first().detail)
        assertEquals("First to 8 moles", record.endReason)
    }

    @Test
    fun aroundTheClockRecordMapsProgressAndDarts() {
        var state = AroundTheClockMode.newGame(players)
        state = AroundTheClockMode.apply(
            state,
            DartHit(1, 1, Ring.SINGLE, com.darts.core.SingleBand.LARGE),
        ).state
        val record = aroundTheClockMatchRecord(state, id, finishedAt)
        assertEquals(GameType.AROUND_THE_CLOCK.name, record.game)
        assertEquals("AROUND THE CLOCK", record.gameTitle)
        assertEquals(1, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("Next 2", record.players.first().detail)
        assertEquals("First around the clock", record.endReason)
    }

    @Test
    fun checkoutRecordMapsClearedCheckoutsAndDarts() {
        val path = CheckoutMode.EASY_PATHS.first { it.score == 60 }
        var state = CheckoutState(
            players = listOf(players.first()),
            rules = CheckoutRules(CheckoutDifficulty.EASY, checkoutsPerSession = 1),
            order = listOf(path),
            dartsByPlayer = mapOf(1 to 0),
        )
        state = CheckoutMode.apply(state, DartHit(20, 1, Ring.SINGLE)).state
        state = CheckoutMode.apply(state, DartHit(20, 2, Ring.DOUBLE)).state
        val record = checkoutMatchRecord(state, id, finishedAt)
        assertEquals(GameType.CHECKOUT.name, record.game)
        assertEquals("CHECKOUT", record.gameTitle)
        assertEquals(1, record.players.first().score)
        assertEquals(2, record.players.first().darts)
        assertEquals("1/1 checkouts", record.players.first().detail)
        assertEquals("Completed 1 checkouts", record.endReason)
    }

    @Test
    fun x01RecordMapsLegsAndRemainingScore() {
        var state = X01Mode.newGame(players, X01Rules(startingScore = 121))
        state = X01Mode.apply(state, treble20).state
        val record = x01MatchRecord(state, id, finishedAt)
        assertEquals(GameType.X01.name, record.game)
        assertEquals("X01", record.gameTitle)
        assertEquals(0, record.players.first().score)
        assertEquals(1, record.players.first().darts)
        assertEquals("61 left", record.players.first().detail)
        assertNull(record.endReason)

        val winnerState = X01Mode.newGame(
            listOf(players.first()),
            X01Rules(startingScore = 121),
        ).copy(
            scores = mapOf(1 to 40),
            opened = mapOf(1 to true),
            turnStartScore = 40,
            turnStartOpened = true,
        )
        val finished = X01Mode.apply(winnerState, DartHit(20, 2, Ring.DOUBLE)).state
        val winRecord = x01MatchRecord(finished, id, finishedAt)
        assertEquals(1, winRecord.players.first().score)
        assertEquals("Checked out", winRecord.players.first().detail)
        assertEquals("121 · Checked out", winRecord.endReason)
    }
}
