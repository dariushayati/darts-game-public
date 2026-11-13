package com.darts.app.autoscore

import android.graphics.Bitmap
import com.darts.core.Ring
import io.github.thomasff.bulltrack.core.domain.model.DartPosition
import io.github.thomasff.bulltrack.core.domain.model.RingType
import io.github.thomasff.bulltrack.core.domain.model.ThrowResult
import io.github.thomasff.bulltrack.core.vision.FrameDetectionUpdate
import io.github.thomasff.bulltrack.core.vision.PrepBoardGuide
import io.github.thomasff.bulltrack.core.vision.RedetectDetectionUpdate
import io.github.thomasff.bulltrack.core.vision.StartRoundDetectionUpdate
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScoreEngineTest {

    @Test
    fun initializesWarmsUpAndStartsARound() {
        val driver = FakeDriver()
        val engine = engine(driver)
        engine.initialize(AutoScoreSettings(softBoard = true, forceCpu = true))

        assertEquals(1, driver.initCalls)
        assertEquals(1, driver.warmUpCalls)
        assertEquals(1, driver.createSessionCalls)
        assertEquals(true, driver.forceCpu)
        assertEquals(AutoScoreState.Idle, engine.state.value)

        driver.startRoundResult = StartRoundDetectionUpdate(startDartCount = 1)
        val frame = mockk<Bitmap>(relaxed = true)
        engine.beginRound(frame, rotationDegrees = 90)

        assertEquals(1, driver.startRoundCalls)
        assertEquals(true, driver.softBoard)
        assertEquals(90, driver.rotationDegrees)
        assertEquals(AutoScoreState.Ready(1), engine.state.value)
        verify { frame.recycle() }
    }

    @Test
    fun processFramePublishesDetectedDarts() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        now += 200
        driver.processResult = FrameDetectionUpdate(
            gateTriggered = true,
            gateReason = "dart_confirmed",
            newThrows = listOf(
                ThrowResult(
                    sector = 20,
                    ring = RingType.TRIPLE,
                    score = 60,
                    position = DartPosition(0f, -0.6f),
                ),
            ),
        )
        engine.processFrame(mockk(relaxed = true))

        val state = engine.state.value as AutoScoreState.DartDetected
        assertEquals(1, state.dartsOnBoard)
        assertEquals(20, state.darts.single().hit.number)
        assertEquals(Ring.TREBLE, state.darts.single().hit.ring)
    }

    @Test
    fun aConfirmedMissBecomesAMissDart() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        now += 200
        driver.processResult = FrameDetectionUpdate(
            gateTriggered = true,
            gateReason = "dart_confirmed",
            newThrows = listOf(null),
        )
        engine.processFrame(mockk(relaxed = true))

        val state = engine.state.value as AutoScoreState.DartDetected
        assertTrue(state.darts.single().hit.isMiss)
        assertTrue(state.darts.single().needsCheck)
    }

    @Test
    fun endTurnMergesMissedDartsAndCompletesTheTurn() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        now += 200
        driver.processResult = FrameDetectionUpdate(
            gateTriggered = true,
            gateReason = "dart_confirmed",
            newThrows = listOf(ThrowResult(20, RingType.OUTER_SINGLE, 20)),
        )
        engine.processFrame(mockk(relaxed = true))

        driver.missedResult = RedetectDetectionUpdate(
            throws = listOf(ThrowResult(0, RingType.MISS, 0)),
        )
        engine.endTurn()

        val state = engine.state.value as AutoScoreState.TurnComplete
        assertEquals(2, state.darts.size)
        assertEquals(1, driver.findMissedCalls)
    }

    @Test
    fun redetectReplacesTheVisibleDarts() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        driver.redetectResult = RedetectDetectionUpdate(
            throws = listOf(ThrowResult(19, RingType.DOUBLE, 38)),
        )
        engine.redetect(mockk(relaxed = true))

        val state = engine.state.value as AutoScoreState.DartDetected
        assertEquals(19, state.darts.single().hit.number)
        assertEquals(Ring.DOUBLE, state.darts.single().hit.ring)
        assertEquals(1, driver.redetectCalls)
    }

    @Test
    fun anAlignmentFailureTriggersAutomaticRedetect() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        now += 200
        driver.processResult = FrameDetectionUpdate(
            gateTriggered = false,
            gateReason = "ecc_alignment_failed: camera moved",
        )
        driver.redetectResult = RedetectDetectionUpdate(
            throws = listOf(ThrowResult(5, RingType.TRIPLE, 15)),
        )
        engine.processFrame(mockk(relaxed = true))

        assertEquals(1, driver.redetectCalls)
        val state = engine.state.value as AutoScoreState.DartDetected
        assertEquals(5, state.darts.single().hit.number)
    }


    @Test
    fun updateSettingsReachesTheDriver() {
        val driver = FakeDriver()
        val engine = engine(driver)
        engine.initialize(AutoScoreSettings())

        engine.updateSettings(AutoScoreSettings(forceCpu = true, debugImages = true))

        assertEquals(true, driver.forceCpu)
        assertEquals(true, driver.debugImages)
    }

    @Test
    fun correctionsReplaceTheVisibleDarts() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = engine(driver) { now }
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        now += 200
        driver.processResult = FrameDetectionUpdate(
            gateTriggered = true,
            gateReason = "dart_confirmed",
            newThrows = listOf(ThrowResult(20, RingType.OUTER_SINGLE, 20)),
        )
        engine.processFrame(mockk(relaxed = true))

        engine.correctDarts(listOf(missAutoScoredDart(needsCheck = false)))

        val state = engine.state.value as AutoScoreState.DartDetected
        assertTrue(state.darts.single().hit.isMiss)
        assertEquals(1, state.dartsOnBoard)
    }

    @Test
    fun startRoundErrorBecomesErrorState() {
        val driver = FakeDriver()
        val engine = engine(driver)
        engine.initialize(AutoScoreSettings())
        driver.startRoundResult = StartRoundDetectionUpdate(errorMessage = "no board")

        engine.beginRound(mockk(relaxed = true))

        assertEquals(AutoScoreState.Error("no board"), engine.state.value)
    }

    @Test
    fun releaseDestroysTheSessionAndReleasesTheDetector() {
        val driver = FakeDriver()
        val engine = engine(driver)
        engine.initialize(AutoScoreSettings())
        engine.beginRound(mockk(relaxed = true))

        engine.release()

        assertEquals(1, driver.destroySessionCalls)
        assertEquals(1, driver.releaseCalls)
    }

    private fun engine(
        driver: FakeDriver,
        clock: () -> Long = { 0L },
    ) = AutoScoreEngine(
        driver = driver,
        dispatcher = Dispatchers.Unconfined,
        ownsDispatcher = false,
        clock = clock,
    )

    private class FakeDriver : AutoScoreDriver {
        var initCalls = 0
        var warmUpCalls = 0
        var createSessionCalls = 0
        var startRoundCalls = 0
        var processCalls = 0
        var redetectCalls = 0
        var findMissedCalls = 0
        var destroySessionCalls = 0
        var releaseCalls = 0
        var forceCpu: Boolean? = null
        var debugImages: Boolean? = null
        var softBoard: Boolean? = null
        var rotationDegrees: Int? = null

        var startRoundResult = StartRoundDetectionUpdate()
        var processResult = FrameDetectionUpdate(gateTriggered = false, gateReason = "idle")
        var redetectResult = RedetectDetectionUpdate()
        var missedResult = RedetectDetectionUpdate()

        override fun init() {
            initCalls++
        }

        override fun warmUp() {
            warmUpCalls++
        }

        override fun createSession(): Long {
            createSessionCalls++
            return 42L
        }

        override fun destroySession(sessionId: Long) {
            destroySessionCalls++
        }

        override fun startRound(
            sessionId: Long,
            firstFrame: Bitmap,
            rotationDegrees: Int,
            softBoard: Boolean,
        ): StartRoundDetectionUpdate {
            startRoundCalls++
            this.rotationDegrees = rotationDegrees
            this.softBoard = softBoard
            return startRoundResult
        }

        override fun processFrame(
            sessionId: Long,
            frame: Bitmap,
            rotationDegrees: Int,
        ): FrameDetectionUpdate {
            processCalls++
            return processResult
        }

        override fun redetect(
            sessionId: Long,
            frame: Bitmap,
            rotationDegrees: Int,
        ): RedetectDetectionUpdate {
            redetectCalls++
            return redetectResult
        }

        override fun findMissedDarts(sessionId: Long): RedetectDetectionUpdate {
            findMissedCalls++
            return missedResult
        }

        override fun setYoloForceCpu(forceCpu: Boolean) {
            this.forceCpu = forceCpu
        }

        override fun setDebugImagesEnabled(enabled: Boolean) {
            debugImages = enabled
        }

        override fun detectPrepBoard(frame: Bitmap, rotationDegrees: Int): PrepBoardGuide =
            error("not used by the engine state-machine tests")

        override fun describeFrameStageTimes(sessionId: Long): String = ""

        override fun releaseDetector() {
            releaseCalls++
        }
    }
}
