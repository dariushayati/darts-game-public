package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class AutoCalibratorTest {

    private data class Affine(
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val tx: Float,
        val ty: Float,
    ) {
        fun project(x: Float, y: Float): Pair<Float, Float> =
            (a * x + b * y + tx) to (c * x + d * y + ty)

        fun unproject(x: Float, y: Float): Pair<Float, Float> {
            val det = a * d - b * c
            val dx = x - tx
            val dy = y - ty
            return ((d * dx - b * dy) / det) to ((-c * dx + a * dy) / det)
        }
    }

    private fun renderBoard(
        size: Int = 480,
        centerX: Float = 240f,
        centerY: Float = 240f,
        scale: Float = 190f,
        tiltRatio: Float = 1f,
        tiltDeg: Float = 0f,
        rotationDeg: Float = 0f,
        noise: Int = 3,
        seed: Long = 7L,
        surroundText: Boolean = false,
    ): Pair<GrayImage, Affine> {
        val tilt = Math.toRadians(tiltDeg.toDouble())
        val affine = Affine(
            a = (cos(tilt) * scale).toFloat(),
            b = (-sin(tilt) * scale * tiltRatio).toFloat(),
            c = (sin(tilt) * scale).toFloat(),
            d = (cos(tilt) * scale * tiltRatio).toFloat(),
            tx = centerX,
            ty = centerY,
        )
        val random = Random(seed)
        val pixels = ByteArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val (ux, uy) = affine.unproject(x.toFloat(), y.toFloat())
                val r = hypot(ux, uy)
                var value = 18
                if (r <= 1.07f) value = 30
                if (r <= 1.0f) {
                    val degrees = Math.toDegrees(atan2(ux.toDouble(), -uy.toDouble())).toFloat()
                    val rotated = degrees - rotationDeg
                    val index = Math.floorMod(
                        (((rotated + 9f) % 360f + 360f) % 360f / 18f).toInt(),
                        20,
                    )
                    value = if (index % 2 == 0) 205 else 160
                    val band = if (index % 2 == 0) 95 else 120
                    if (r in 0.953f..1.0f) value = band
                    if (r in 0.582f..0.629f) value = band
                    if (r <= 0.0935f) value = 70
                    if (r <= 0.0374f) value = 235
                    val boundary = ((rotated + 9f) % 18f + 18f) % 18f
                    val arc = min(boundary, 18f - boundary) * (Math.PI.toFloat() / 180f) * r * scale
                    if (r > 0.08f && arc < 1.4f) value = 25
                }
                val jitter = if (noise > 0) random.nextInt(-noise, noise + 1) else 0
                pixels[y * size + x] = (value + jitter).coerceIn(0, 255).toByte()
            }
        }
        if (surroundText) {
            val bars = Random(seed + 99)
            repeat(70) {
                val x0 = bars.nextInt(0, size)
                val y0 = bars.nextInt(0, size)
                val barWidth = bars.nextInt(maxOf(3, size / 40), maxOf(5, size / 12))
                val barHeight = bars.nextInt(maxOf(2, size / 50), maxOf(4, size / 10))
                for (yy in y0 until minOf(size, y0 + barHeight)) {
                    for (xx in x0 until minOf(size, x0 + barWidth)) {
                        val dx = xx - centerX
                        val dy = yy - centerY
                        val rr = hypot(dx, dy) / scale
                        if (rr in 1.06f..1.45f) pixels[yy * size + xx] = 240.toByte()
                    }
                }
            }
        }
        return GrayImage(size, size, pixels) to affine
    }

    private fun canonicalPoint(index: Int, radius: Float, rotationDeg: Float): Pair<Float, Float> {
        val angle = Math.toRadians(index * 18.0 + rotationDeg)
        return (sin(angle) * radius).toFloat() to (-cos(angle) * radius).toFloat()
    }

    private fun rotationError(actual: Float, expected: Float): Float {
        var difference = (actual - expected) % 18f
        if (difference > 9f) difference -= 18f
        if (difference < -9f) difference += 18f
        return abs(difference)
    }

    private fun assertBoardMapsCorrectly(
        calibration: BoardCalibration,
        affine: Affine,
        rotationDeg: Float,
    ) {
        for (index in 0 until 20) {
            val (ux, uy) = canonicalPoint(index, 0.55f, rotationDeg)
            val (px, py) = affine.project(ux, uy)
            assertEquals(
                "sector $index",
                BoardGeometry.SECTOR_ORDER[index],
                calibration.hitAt(px, py).number,
            )
        }

        val (dx, dy) = canonicalPoint(0, 0.98f, rotationDeg)
        val (dpx, dpy) = affine.project(dx, dy)
        val double = calibration.hitAt(dpx, dpy)
        assertEquals(2, double.multiplier)
        assertEquals(Ring.DOUBLE, double.ring)

        val (tx, ty) = canonicalPoint(0, 0.605f, rotationDeg)
        val (tpx, tpy) = affine.project(tx, ty)
        assertEquals(3, calibration.hitAt(tpx, tpy).multiplier)

        val (bx, by) = affine.project(0f, 0f)
        assertEquals(BoardGeometry.BULL, calibration.hitAt(bx, by).number)
    }

    @Test
    fun `front-on board maps every sector`() {
        val (image, affine) = renderBoard()
        val calibration = AutoCalibrator.calibrate(image)
        assertNotNull(calibration)
        calibration!!
        assertTrue("cal=$calibration", calibration.confidence > 0.3f)
        assertBoardMapsCorrectly(calibration, affine, 0f)
        assertEquals(190f, calibration.radiusApprox, 8f)
    }

    @Test
    fun `tilted board maps every sector`() {
        val (image, affine) = renderBoard(tiltRatio = 0.78f, tiltDeg = 22f, rotationDeg = 5f)
        val calibration = AutoCalibrator.calibrate(image, upDegrees = 22f)
        assertNotNull(calibration)
        calibration!!
        assertTrue("cal=$calibration", calibration.confidence > 0.25f)
        assertBoardMapsCorrectly(calibration, affine, 5f)
    }

    @Test
    fun `steep tilt and negative rotation`() {
        val (image, affine) = renderBoard(
            centerX = 210f,
            centerY = 250f,
            tiltRatio = 0.62f,
            tiltDeg = -28f,
            rotationDeg = -4f,
        )
        val calibration = AutoCalibrator.calibrate(image, upDegrees = -28f)
        assertNotNull(calibration)
        calibration!!
        assertTrue("cal=$calibration", calibration.confidence > 0.2f)
        assertBoardMapsCorrectly(calibration, affine, -4f)
    }

    @Test
    fun `rotation wraps within half a sector`() {
        val (image, _) = renderBoard(rotationDeg = 7.5f)
        val calibration = AutoCalibrator.calibrate(image)
        assertNotNull(calibration)
        assertTrue(
            "rot=${calibration!!.rotationDeg}",
            rotationError(calibration.rotationDeg, 7.5f) < 3f,
        )
    }

    @Test
    fun `ellipse hint maps every sector when tilted`() {
        val (image, affine) = renderBoard(tiltRatio = 0.78f, tiltDeg = 22f, rotationDeg = 5f)
        val hint = EllipseHint(
            centerX = 240f,
            centerY = 240f,
            semiMajor = 190f,
            semiMinor = 190f * 0.78f,
            angleRadians = Math.toRadians(22.0).toFloat(),
            confidence = 1f,
        )
        val result = AutoCalibrator.calibrateFromEllipse(image, hint, upDegrees = 22f)
        assertNotNull(result.calibration)
        assertBoardMapsCorrectly(result.calibration!!, affine, 5f)
    }

    @Test
    fun `ellipse hint does not assume it is the outer double`() {
        val (image, affine) = renderBoard(tiltRatio = 0.85f, tiltDeg = 8f, rotationDeg = -3f)
        val hint = EllipseHint(
            centerX = 240f,
            centerY = 240f,
            semiMajor = 190f * 1.32f,
            semiMinor = 190f * 0.85f * 1.32f,
            angleRadians = Math.toRadians(8.0).toFloat(),
            confidence = 1f,
        )
        val result = AutoCalibrator.calibrateFromEllipse(image, hint, upDegrees = 8f)
        assertNotNull(result.calibration)
        assertBoardMapsCorrectly(result.calibration!!, affine, -3f)
    }

    @Test
    fun `ellipse hint may be an inner ring`() {
        val (image, affine) = renderBoard(tiltRatio = 0.80f, tiltDeg = -12f, rotationDeg = 6f)
        val hint = EllipseHint(
            centerX = 240f,
            centerY = 240f,
            semiMajor = 190f * 0.6055f,
            semiMinor = 190f * 0.80f * 0.6055f,
            angleRadians = Math.toRadians(-12.0).toFloat(),
            confidence = 1f,
        )
        val result = AutoCalibrator.calibrateFromEllipse(image, hint, upDegrees = -12f)
        assertNotNull(result.calibration)
        assertBoardMapsCorrectly(result.calibration!!, affine, 6f)
    }

    @Test
    fun `ellipse hint on a front-on board`() {
        val (image, affine) = renderBoard()
        val hint = EllipseHint(240f, 240f, 190f, 190f, 0f, 1f)
        val result = AutoCalibrator.calibrateFromEllipse(image, hint)
        assertNotNull(result.calibration)
        assertBoardMapsCorrectly(result.calibration!!, affine, 0f)
    }

    @Test
    fun `noise alone is not a board`() {
        val random = Random(11)
        val pixels = ByteArray(256 * 256) { random.nextInt(0, 256).toByte() }
        val calibration = AutoCalibrator.calibrate(GrayImage(256, 256, pixels))
        assertTrue(calibration == null || calibration.confidence < 0.5f)
    }

    @Test
    fun `rgba frames convert to grey`() {
        val rgba = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 255.toByte(), 0, 255.toByte(),
            0, 0, 255.toByte(), 255.toByte(),
            255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(),
        )
        val image = GrayImage.fromRgba(rgba, width = 2, height = 2, rowStride = 8, pixelStride = 4)
        assertEquals(76, image[0, 0])
        assertEquals(149, image[1, 0])
        assertEquals(28, image[0, 1])
        assertEquals(255, image[1, 1])
    }

    @Test
    fun `hitAt undoes board rotation`() {
        val calibration = BoardCalibration.similarity(0f, 0f, 1f, 5f, 1f)
        val angle = Math.toRadians(23.0)
        val hit = calibration.hitAt((sin(angle) * 0.8).toFloat(), (-cos(angle) * 0.8).toFloat())
        assertEquals(1, hit.number)
        assertEquals(Ring.SINGLE, hit.ring)
    }

    @Test
    fun `tracker median-filters jitter, rejects outliers and drops a lost board`() {
        val tracker = CalibrationTracker(
            window = 9,
            toleranceFraction = 0.10f,
            relockAfter = 6,
            maxMisses = 3,
        )
        val random = kotlin.random.Random(1)
        var result: BoardCalibration? = null
        repeat(12) {
            result = tracker.update(
                BoardCalibration.similarity(
                    100f + random.nextFloat() * 2f - 1f,
                    100f + random.nextFloat() * 2f - 1f,
                    50f,
                    0f,
                    0.9f,
                ),
            )
        }
        result!!
        assertTrue(abs(result.centerX - 100f) < 1f)
        assertTrue(abs(result.radiusApprox - 50f) < 1f)

        val locked = result
        result = tracker.update(BoardCalibration.similarity(130f, 130f, 62f, 18f, 0.9f))
        assertTrue(abs(result!!.centerX - locked.centerX) < 0.5f)

        repeat(10) {
            result = tracker.update(BoardCalibration.similarity(130f, 130f, 62f, 18f, 0.9f))
        }
        assertTrue(abs(result!!.centerX - 130f) < 2f)

        assertNotNull(tracker.update(null))
        assertNotNull(tracker.update(null))
        assertNull(tracker.update(null))
    }

}

