package com.darts.app

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.darts.app.autoscore.AutoScoreDriver
import com.darts.app.autoscore.AutoScoreEngine
import com.darts.app.autoscore.AutoScoreSettings
import com.darts.app.autoscore.AutoScoredDart
import io.github.thomasff.bulltrack.core.domain.model.RingType
import io.github.thomasff.bulltrack.core.domain.model.ThrowResult
import io.github.thomasff.bulltrack.core.vision.FrameDetectionUpdate
import io.github.thomasff.bulltrack.core.vision.PrepBoardGuide
import io.github.thomasff.bulltrack.core.vision.RedetectDetectionUpdate
import io.github.thomasff.bulltrack.core.vision.StartRoundDetectionUpdate
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraScoringControllerTest {

    @Test
    fun aFailedStartWaitsAndRetriesAfterTheBoardIsClear() {
        val driver = FakeDriver()
        val engine = AutoScoreEngine(
            driver = driver,
            dispatcher = Dispatchers.Unconfined,
            ownsDispatcher = false,
            clock = { 0L },
        )
        val controller = CameraScoringController(
            settingsProvider = { AutoScoreSettings() },
            engineFactory = { engine },
            dispatcher = Dispatchers.Unconfined,
            retryDelayMs = 0L,
        )
        try {
            controller.start()
            controller.beginTurn("turn-1")

            driver.startRoundError = "too_few_perpendicular_double_beds"
            controller.submitFrame(mockk(relaxed = true))
            assertEquals("too_few_perpendicular_double_beds", controller.lastError.value)

            driver.startRoundError = null
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(null, controller.lastError.value)
            assertEquals(2, driver.startRoundCalls)
        } finally {
            controller.release()
        }
    }

    @Test
    fun earlyTurnEndSignalsOnlyAfterTheBoardIsClear() {
        val driver = FakeDriver()
        val engine = AutoScoreEngine(
            driver = driver,
            dispatcher = Dispatchers.Unconfined,
            ownsDispatcher = false,
            clock = { 0L },
        )
        val controller = CameraScoringController(
            settingsProvider = { AutoScoreSettings() },
            engineFactory = { engine },
            dispatcher = Dispatchers.Unconfined,
        )
        val turns = mutableListOf<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val job = scope.launch { controller.turnComplete.collect { turns += it } }
        try {
            controller.start()
            controller.beginTurn("turn-1")
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(1, driver.startRoundCalls)

            controller.endTurnEarly()
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(1, turns.size)
            // The game already advanced on the bust; the camera clears the ignore flag
            // and starts the next native round from the clear frame.
            assertEquals(2, driver.startRoundCalls)
        } finally {
            job.cancel()
            scope.cancel()
            controller.release()
        }
    }

    @Test
    fun waitsForTheBoardToClearBeforeStartingTheNextRound() {
        val driver = FakeDriver()
        val engine = AutoScoreEngine(
            driver = driver,
            dispatcher = Dispatchers.Unconfined,
            ownsDispatcher = false,
            clock = { 0L },
        )
        val controller = CameraScoringController(
            settingsProvider = { AutoScoreSettings() },
            engineFactory = { engine },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start()
            controller.beginTurn("turn-1")

            driver.prepDartCount = 3
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(0, driver.startRoundCalls)

            driver.prepDartCount = 0
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(1, driver.startRoundCalls)
        } finally {
            controller.release()
        }
    }

    @Test
    fun requestRedetectUsesTheNextFrame() {
        val driver = FakeDriver()
        val engine = AutoScoreEngine(
            driver = driver,
            dispatcher = Dispatchers.Unconfined,
            ownsDispatcher = false,
            clock = { 0L },
        )
        val controller = CameraScoringController(
            settingsProvider = { AutoScoreSettings() },
            engineFactory = { engine },
            dispatcher = Dispatchers.Unconfined,
        )
        try {
            controller.start()
            controller.beginTurn("turn-1")
            controller.submitFrame(mockk(relaxed = true))

            controller.requestRedetect()
            controller.submitFrame(mockk(relaxed = true))

            assertEquals(1, driver.redetectCalls)
        } finally {
            controller.release()
        }
    }

    @Test
    fun emitsEachDartOnceAndCompletesAfterThree() {
        val driver = FakeDriver()
        var now = 1_000L
        val engine = AutoScoreEngine(
            driver = driver,
            dispatcher = Dispatchers.Unconfined,
            ownsDispatcher = false,
            clock = { now },
        )
        val controller = CameraScoringController(
            settingsProvider = { AutoScoreSettings() },
            engineFactory = { engine },
            dispatcher = Dispatchers.Unconfined,
        )
        val detected = mutableListOf<AutoScoredDart>()
        val turns = mutableListOf<Unit>()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val jobs = listOf(
            scope.launch { controller.detected.collect { detected += it } },
            scope.launch { controller.turnComplete.collect { turns += it } },
        )

        try {
            controller.start()
            controller.beginTurn("turn-1")
            controller.submitFrame(mockk(relaxed = true))

            repeat(3) {
                now += 200
                driver.processResult = FrameDetectionUpdate(
                    gateTriggered = true,
                    gateReason = "dart_confirmed",
                    newThrows = listOf(ThrowResult(20, RingType.OUTER_SINGLE, 20)),
                )
                controller.submitFrame(mockk(relaxed = true))
            }

            assertEquals(3, detected.size)
            assertEquals(0, turns.size)

            // A new turn starts a fresh native round without re-emitting old darts.
            controller.beginTurn("turn-2")
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(3, detected.size)
            assertEquals(0, turns.size)

            now += 200
            driver.processResult = FrameDetectionUpdate(
                gateTriggered = true,
                gateReason = "dart_confirmed",
                newThrows = listOf(ThrowResult(19, RingType.DOUBLE, 38)),
            )
            controller.submitFrame(mockk(relaxed = true))
            assertEquals(4, detected.size)
        } finally {
            jobs.forEach { it.cancel() }
            scope.cancel()
            controller.release()
        }
    }

    private class FakeDriver : AutoScoreDriver {
        var processResult = FrameDetectionUpdate(gateTriggered = false, gateReason = "idle")
        var redetectCalls = 0
        var startRoundCalls = 0
        var prepDartCount = 0
        var startRoundError: String? = null

        override fun init() = Unit
        override fun warmUp() = Unit
        override fun createSession(): Long = 1L
        override fun destroySession(sessionId: Long) = Unit
        override fun startRound(
            sessionId: Long,
            firstFrame: Bitmap,
            rotationDegrees: Int,
            softBoard: Boolean,
        ): StartRoundDetectionUpdate {
            startRoundCalls++
            return StartRoundDetectionUpdate(errorMessage = startRoundError)
        }

        override fun processFrame(
            sessionId: Long,
            frame: Bitmap,
            rotationDegrees: Int,
        ): FrameDetectionUpdate = processResult

        override fun redetect(
            sessionId: Long,
            frame: Bitmap,
            rotationDegrees: Int,
        ): RedetectDetectionUpdate {
            redetectCalls++
            return RedetectDetectionUpdate()
        }

        override fun findMissedDarts(sessionId: Long): RedetectDetectionUpdate =
            RedetectDetectionUpdate()

        override fun setYoloForceCpu(forceCpu: Boolean) = Unit
        override fun setDebugImagesEnabled(enabled: Boolean) = Unit

        override fun detectPrepBoard(frame: Bitmap, rotationDegrees: Int): PrepBoardGuide =
            PrepBoardGuide(
                frameWidth = 640,
                frameHeight = 480,
                bandTop = 0,
                bandHeight = 480,
                boardBox = mockk<Rect>(relaxed = true),
                dartTips = List(prepDartCount) { mockk<Point>(relaxed = true) },
            )

        override fun describeFrameStageTimes(sessionId: Long): String = ""
        override fun releaseDetector() = Unit
    }


}
