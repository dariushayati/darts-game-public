package com.darts.app.autoscore

import android.graphics.Bitmap
import io.github.thomasff.bulltrack.core.vision.DartDetector
import io.github.thomasff.bulltrack.core.vision.NativeDartDetector
import io.github.thomasff.bulltrack.core.vision.PrepBoardGuide

/**
 * [DartDetector] plus the non-interface extras that only the native implementation
 * offers. Keeping them behind this interface lets the engine state machine be tested
 * with a fake, while the production adapter delegates to [NativeDartDetector].
 */
interface AutoScoreDriver : DartDetector {
    fun setYoloForceCpu(forceCpu: Boolean)
    fun setDebugImagesEnabled(enabled: Boolean)
    fun detectPrepBoard(frame: Bitmap, rotationDegrees: Int): PrepBoardGuide
    fun describeFrameStageTimes(sessionId: Long): String
    fun releaseDetector()
}

class NativeAutoScoreDriver(
    private val native: NativeDartDetector,
) : AutoScoreDriver {
    override fun init() = native.init()
    override fun warmUp() = native.warmUp()
    override fun createSession(): Long = native.createSession()
    override fun destroySession(sessionId: Long) = native.destroySession(sessionId)
    override fun startRound(
        sessionId: Long,
        firstFrame: Bitmap,
        rotationDegrees: Int,
        softBoard: Boolean,
    ) = native.startRound(sessionId, firstFrame, rotationDegrees, softBoard)

    override fun processFrame(sessionId: Long, frame: Bitmap, rotationDegrees: Int) =
        native.processFrame(sessionId, frame, rotationDegrees)

    override fun redetect(sessionId: Long, frame: Bitmap, rotationDegrees: Int) =
        native.redetect(sessionId, frame, rotationDegrees)

    override fun findMissedDarts(sessionId: Long) = native.findMissedDarts(sessionId)
    override fun setYoloForceCpu(forceCpu: Boolean) = native.setYoloForceCpu(forceCpu)
    override fun setDebugImagesEnabled(enabled: Boolean) = native.setDebugImagesEnabled(enabled)
    override fun detectPrepBoard(frame: Bitmap, rotationDegrees: Int) =
        native.detectPrepBoard(frame, rotationDegrees)

    override fun describeFrameStageTimes(sessionId: Long) =
        native.describeFrameStageTimes(sessionId)

    override fun releaseDetector() = native.release()
}
