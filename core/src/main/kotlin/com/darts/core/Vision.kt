package com.darts.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** A single-channel 8-bit image. Deliberately free of Android types so vision is JVM-testable. */
class GrayImage(val width: Int, val height: Int, private val pixels: ByteArray) {
    init {
        require(width > 0 && height > 0) { "image must be non-empty" }
        require(pixels.size == width * height) { "pixel buffer does not match dimensions" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x].toInt() and 0xFF

    fun copyBytes(): ByteArray = pixels.copyOf()

    /** Box-filtered downscale so strong edges do not alias. */
    fun downscale(maxDimension: Int): GrayImage {
        val longest = max(width, height)
        if (longest <= maxDimension) return this
        val scale = maxDimension.toFloat() / longest
        val outW = max(1, (width * scale).roundToInt())
        val outH = max(1, (height * scale).roundToInt())
        val out = ByteArray(outW * outH)
        for (y in 0 until outH) {
            val y0 = (y / scale).toInt().coerceIn(0, height - 1)
            val y1 = ((y + 1) / scale).toInt().coerceIn(y0 + 1, height)
            for (x in 0 until outW) {
                val x0 = (x / scale).toInt().coerceIn(0, width - 1)
                val x1 = ((x + 1) / scale).toInt().coerceIn(x0 + 1, width)
                var sum = 0
                var count = 0
                for (yy in y0 until y1) {
                    for (xx in x0 until x1) {
                        sum += pixels[yy * width + xx].toInt() and 0xFF
                        count++
                    }
                }
                out[y * outW + x] = (sum / max(1, count)).toByte()
            }
        }
        return GrayImage(outW, outH, out)
    }

    companion object {
        /** Builds a grey image from an RGBA_8888 frame, honouring the buffer strides. */
        fun fromRgba(
            buffer: ByteArray,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int = 4,
        ): GrayImage {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                var index = y * rowStride
                for (x in 0 until width) {
                    if (index + 2 >= buffer.size) return GrayImage(width, height, out)
                    val r = buffer[index].toInt() and 0xFF
                    val g = buffer[index + 1].toInt() and 0xFF
                    val b = buffer[index + 2].toInt() and 0xFF
                    out[y * width + x] = ((77 * r + 150 * g + 29 * b) shr 8).toByte()
                    index += pixelStride
                }
            }
            return GrayImage(width, height, out)
        }

        /**
         * Red-minus-green chroma. Dartboard sectors alternate red/green, which have almost
         * identical luma; their chroma difference carries the twenty-fold sector signal.
         */
        fun fromRgbaChroma(
            buffer: ByteArray,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int = 4,
        ): GrayImage {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                var index = y * rowStride
                for (x in 0 until width) {
                    if (index + 1 >= buffer.size) return GrayImage(width, height, out)
                    val r = buffer[index].toInt() and 0xFF
                    val g = buffer[index + 1].toInt() and 0xFF
                    out[y * width + x] = (((r - g) + 255) / 2).coerceIn(0, 255).toByte()
                    index += pixelStride
                }
            }
            return GrayImage(width, height, out)
        }

        /** Copies the luminance plane of a YUV frame, honouring the buffer strides. */
        fun fromYPlane(
            buffer: ByteArray,
            width: Int,
            height: Int,
            rowStride: Int,
            pixelStride: Int = 1,
        ): GrayImage {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                val row = y * rowStride
                for (x in 0 until width) {
                    out[y * width + x] = buffer[row + x * pixelStride]
                }
            }
            return GrayImage(width, height, out)
        }
    }
}

/**
 * Maps image pixels onto the board. The board may be viewed at an angle, so this is an
 * affine map rather than a circle: `canonical = linear * (pixel - centre)`.
 *
 * [rotationDeg] is the board's clockwise rotation from canonical (20 at 12 o'clock) and is
 * kept separately for smoothing and display.
 */
data class BoardCalibration(
    val centerX: Float,
    val centerY: Float,
    val xx: Float,
    val xy: Float,
    val yx: Float,
    val yy: Float,
    val rotationDeg: Float,
    val confidence: Float,
) {
    fun toCanonical(x: Float, y: Float): Pair<Float, Float> {
        val dx = x - centerX
        val dy = y - centerY
        return (xx * dx + xy * dy) to (yx * dx + yy * dy)
    }

    fun hitAt(x: Float, y: Float): DartHit {
        val (u, v) = toCanonical(x, y)
        return BoardGeometry.hitTest(u, v)
    }

    /** Approximate outer-double radius in pixels, for display only. */
    val radiusApprox: Float
        get() = 1f / sqrt(abs(xx * yy - xy * yx)).coerceAtLeast(1e-6f)

    fun scaled(factor: Float): BoardCalibration = copy(
        centerX = centerX * factor,
        centerY = centerY * factor,
        xx = xx / factor,
        xy = xy / factor,
        yx = yx / factor,
        yy = yy / factor,
    )

    companion object {
        /** Convenience for an upright board: a plain similarity transform. */
        fun similarity(
            centerX: Float,
            centerY: Float,
            radius: Float,
            rotationDeg: Float,
            confidence: Float,
        ): BoardCalibration {
            val a = Math.toRadians(rotationDeg.toDouble())
            val c = cos(a).toFloat()
            val s = sin(a).toFloat()
            val inv = 1f / radius
            return BoardCalibration(
                centerX = centerX,
                centerY = centerY,
                xx = c * inv,
                xy = s * inv,
                yx = -s * inv,
                yy = c * inv,
                rotationDeg = rotationDeg,
                confidence = confidence,
            )
        }
    }
}

/**
 * Markerless board localisation that tolerates an angled camera.
 *
 * A dartboard is rotationally symmetric: rings are circles and the twenty wires are radial.
 * Under an affine view that symmetry survives as an ellipse, so the second moments of the
 * board's edge pixels recover the tilt (orientation + axis ratio) exactly. In the
 * moment-normalised frame the concentric rings become concentric circles (scale search) and
 * the twenty wires give rotation as the phase of the 20th angular harmonic.
 */
/** An ellipse found by an external detector (e.g. OpenCV), in source-image pixels. */
data class EllipseHint(
    val centerX: Float,
    val centerY: Float,
    val semiMajor: Float,
    val semiMinor: Float,
    val angleRadians: Float,
    val confidence: Float = 1f,
    val source: String = "auto",
    /** True when the detector knows this ellipse is the outer double ring. */
    val assumeOuterDouble: Boolean = false,
)

data class CalibrationDiagnostics(
    val edges: Int = 0,
    val rings: Int = 0,
    val templateScale: Float = 0f,
    val usedEllipse: Boolean = false,
    val usedRansac: Boolean = false,
    val usedConic: Boolean = false,
    val conicResidual: Float = 0f,
    val confidence: Float = 0f,
    val amplitude: Float = 0f,
    val source: String? = null,
    val failure: String? = null,
)

data class CalibrationResult(
    val calibration: BoardCalibration?,
    val diagnostics: CalibrationDiagnostics,
)

object AutoCalibrator {
    private const val DEFAULT_MAX_DIM = 360

    private class GradientField(val gx: FloatArray, val gy: FloatArray, val mag: FloatArray)
    private class Point(val x: Float, val y: Float, val gx: Float, val gy: Float, val weight: Float)
    private class Moments(
        val cx: Float,
        val cy: Float,
        val cxx: Float,
        val cxy: Float,
        val cyy: Float,
    )
    private class Mat2(val xx: Float, val xy: Float, val yx: Float, val yy: Float)
    private class Shape(val inverse: Mat2, val sqrt: Mat2)
    private class Fit(val shape: Shape, val cx: Float, val cy: Float)
    private class Rotation(val degrees: Float, val amplitude: Float)
    private class ScaleEstimate(val value: Float, val rings: Int)

    fun calibrate(
        source: GrayImage,
        maxDimension: Int = DEFAULT_MAX_DIM,
        upDegrees: Float = 0f,
    ): BoardCalibration? {
        val result = calibrateDetailed(source, maxDimension, upDegrees)
        val calibration = result.calibration ?: return null
        val plausible = calibration.confidence >= 0.15f && result.diagnostics.amplitude >= 0.12f
        return if (plausible) calibration else null
    }

    /**
     * Same pipeline as [calibrate] but always returns diagnostics (and a best-effort
     * calibration even when confidence is low) so the UI can show where it dropped out.
     */
    fun calibrateDetailed(
        source: GrayImage,
        maxDimension: Int = DEFAULT_MAX_DIM,
        upDegrees: Float = 0f,
    ): CalibrationResult {
        var diagnostics = CalibrationDiagnostics()
        val image = source.downscale(maxDimension)
        val w = image.width
        val h = image.height
        if (w < 32 || h < 32) {
            return CalibrationResult(null, diagnostics.copy(failure = "frame ${w}x$h"))
        }

        val raw = FloatArray(w * h) { (image[it % w, it / w]).toFloat() }
        val gray = blur(raw, w, h)
        val field = gradients(gray, w, h)
        val threshold = percentile(field.mag, 0.95f)
        val minDim = min(w, h).toFloat()

        var points = collect(field, w, h, threshold, w / 2f, h / 2f, 0.70f * minDim)
        diagnostics = diagnostics.copy(edges = points.size)
        if (points.size < 100) {
            return CalibrationResult(null, diagnostics.copy(failure = "edges=${points.size}"))
        }

        var moments = moments(points)
        points = collect(field, w, h, threshold, moments.cx, moments.cy, 0.70f * minDim)
        if (points.size < 100) {
            return CalibrationResult(null, diagnostics.copy(failure = "edges2=${points.size}"))
        }
        moments = moments(points)

        var fit = Fit(
            shape = shapeOf(moments)
                ?: return CalibrationResult(null, diagnostics.copy(failure = "shape")),
            cx = moments.cx,
            cy = moments.cy,
        )
        repeat(2) { fit = correctedFit(points, fit) }
        val dense = collect(field, w, h, percentile(field.mag, 0.80f), fit.cx, fit.cy, 0.70f * minDim)
        val templateScale = estimateScale(dense, fit.shape.inverse, fit.cx, fit.cy)
            ?: return CalibrationResult(null, diagnostics.copy(failure = "scale"))
        diagnostics = diagnostics.copy(
            rings = templateScale.rings,
            templateScale = templateScale.value,
        )

        val ringScore = (templateScale.rings / 5f).coerceIn(0f, 1f)
        var scaleValue = templateScale.value
        var shapeScore = 0.5f

        var conic = outerRingFit(
            points = if (dense.size > points.size) dense else points,
            inverse = fit.shape.inverse,
            cx = fit.cx,
            cy = fit.cy,
            ringRadius = scaleValue,
        )
        if (conic != null) {
            fit = Fit(shapeFromConic(conic), conic.cx, conic.cy)
            scaleValue = outerRadiusPeak(dense, fit.shape.inverse, fit.cx, fit.cy)
            shapeScore = (1f - conic.residual / 0.12f).coerceIn(0f, 1f)
            diagnostics = diagnostics.copy(usedConic = true, conicResidual = conic.residual)
        }

        val clipped = points.filter { point ->
            val (vx, vy) = apply(fit.shape.inverse, point.x - fit.cx, point.y - fit.cy)
            hypot(vx.toDouble(), vy.toDouble()).toFloat() <= scaleValue * 1.12f
        }
        if (clipped.size >= 100 && clipped.size < points.size) {
            points = clipped
            val ring = outerRingFit(
                points = if (dense.size > points.size) dense else points,
                inverse = fit.shape.inverse,
                cx = fit.cx,
                cy = fit.cy,
                ringRadius = scaleValue,
            )
            if (ring != null) {
                conic = ring
                fit = Fit(shapeFromConic(ring), ring.cx, ring.cy)
                scaleValue = outerRadiusPeak(dense, fit.shape.inverse, fit.cx, fit.cy)
                shapeScore = (1f - ring.residual / 0.12f).coerceIn(0f, 1f)
                diagnostics = diagnostics.copy(usedConic = true, conicResidual = ring.residual)
            } else {
                val fresh = moments(points)
                fit = Fit(
                    shape = shapeOf(fresh)
                        ?: return CalibrationResult(null, diagnostics.copy(failure = "shape2")),
                    cx = fresh.cx,
                    cy = fresh.cy,
                )
                repeat(2) { fit = correctedFit(points, fit) }
                val rescale = estimateScale(dense, fit.shape.inverse, fit.cx, fit.cy)
                    ?: return CalibrationResult(null, diagnostics.copy(failure = "scale2"))
                scaleValue = rescale.value
                shapeScore = 0.5f
            }
        }
        if (scaleValue <= 0f) {
            return CalibrationResult(null, diagnostics.copy(failure = "scale<=0"))
        }

        // Exclude surround printing and background: only edges inside the board carry the
        // twenty-fold wire pattern.
        val boardPoints = points.filter { point ->
            val (vx, vy) = apply(fit.shape.inverse, point.x - fit.cx, point.y - fit.cy)
            hypot(vx.toDouble(), vy.toDouble()).toFloat() <= scaleValue * 1.10f
        }
        val measured = estimateRotation(boardPoints, fit.shape, fit.cx, fit.cy)
            ?: return CalibrationResult(null, diagnostics.copy(failure = "rotation"))
        val rotation = resolveRotationBranch(measured.degrees, fit.shape, upDegrees)

        val angle = Math.toRadians(rotation.toDouble())
        val c = cos(angle).toFloat()
        val s = sin(angle).toFloat()
        val inv = 1f / scaleValue
        val m = fit.shape.inverse
        val xx = inv * (c * m.xx + s * m.yx)
        val xy = inv * (c * m.xy + s * m.yy)
        val yx = inv * (-s * m.xx + c * m.yx)
        val yy = inv * (-s * m.xy + c * m.yy)

        val amplitudeScore = (measured.amplitude / 0.08f).coerceIn(0f, 1f)
        val confidence = amplitudeScore * 0.4f + shapeScore * 0.35f + ringScore * 0.25f
        diagnostics = diagnostics.copy(confidence = confidence, amplitude = measured.amplitude)

        val back = source.width.toFloat() / w
        val calibration = BoardCalibration(
            centerX = fit.cx,
            centerY = fit.cy,
            xx = xx,
            xy = xy,
            yx = yx,
            yy = yy,
            rotationDeg = wrap18(rotation),
            confidence = confidence,
        ).scaled(back)
        return CalibrationResult(calibration, diagnostics)
    }

    /**
     * Calibration from an ellipse supplied by an external detector (OpenCV HoughCircles +
     * fitEllipse). The ellipse fixes centre and tilt; the multi-ring template fixes which
     * ring it was and therefore the scale, and the 20-fold harmonic fixes rotation.
     */
    fun calibrateFromEllipse(
        source: GrayImage,
        hint: EllipseHint,
        maxDimension: Int = DEFAULT_MAX_DIM,
        upDegrees: Float = 0f,
    ): CalibrationResult {
        var diagnostics = CalibrationDiagnostics(usedEllipse = true)
        val image = source.downscale(maxDimension)
        val w = image.width
        val h = image.height
        val factor = w.toFloat() / source.width
        val cx = hint.centerX * factor
        val cy = hint.centerY * factor
        val semiMajor = hint.semiMajor * factor
        val semiMinor = hint.semiMinor * factor
        if (semiMajor <= 1f || semiMinor <= 1f) {
            return CalibrationResult(null, diagnostics.copy(failure = "ellipse too small"))
        }

        val raw = FloatArray(w * h) { (image[it % w, it / w]).toFloat() }
        val gray = blur(raw, w, h)
        val field = gradients(gray, w, h)
        val minDim = min(w, h).toFloat()
        val points = collect(field, w, h, percentile(field.mag, 0.90f), cx, cy, 0.80f * minDim)
        val dense = collect(field, w, h, percentile(field.mag, 0.80f), cx, cy, 0.80f * minDim)
        diagnostics = diagnostics.copy(edges = points.size)
        if (points.size < 100 || dense.size < 100) {
            return CalibrationResult(null, diagnostics.copy(failure = "edges=${points.size}"))
        }

        val c = cos(hint.angleRadians.toDouble()).toFloat()
        val s = sin(hint.angleRadians.toDouble()).toFloat()
        // A = R(theta) * diag(semiMajor, semiMinor); the ellipse is |A^-1 (x-t)| = 1.
        val a00 = c * semiMajor
        val a01 = -s * semiMinor
        val a10 = s * semiMajor
        val a11 = c * semiMinor
        val pxx = a00 * a00 + a01 * a01
        val pxy = a00 * a10 + a01 * a11
        val pyy = a10 * a10 + a11 * a11
        val shape = Shape(
            inverse = symmetricPower(pxx, pxy, pyy, -0.5f),
            sqrt = symmetricPower(pxx, pxy, pyy, 0.5f),
        )

        val templateScale = if (hint.assumeOuterDouble) {
            ScaleEstimate(1f, 5)
        } else {
            estimateScale(dense, shape.inverse, cx, cy)
                ?: return CalibrationResult(null, diagnostics.copy(failure = "scale"))
        }
        diagnostics = diagnostics.copy(
            rings = templateScale.rings,
            templateScale = templateScale.value,
        )
        val scaleValue = templateScale.value
        if (scaleValue <= 0f) {
            return CalibrationResult(null, diagnostics.copy(failure = "scale<=0"))
        }

        val boardPoints = points.filter { point ->
            val (vx, vy) = apply(shape.inverse, point.x - cx, point.y - cy)
            hypot(vx.toDouble(), vy.toDouble()).toFloat() <= scaleValue * 1.10f
        }
        val measured = estimateRotation(boardPoints, shape, cx, cy)
            ?: return CalibrationResult(null, diagnostics.copy(failure = "rotation"))
        val rotation = resolveRotationBranch(measured.degrees, shape, upDegrees)

        val angle = Math.toRadians(rotation.toDouble())
        val rc = cos(angle).toFloat()
        val rs = sin(angle).toFloat()
        val inv = 1f / scaleValue
        val m = shape.inverse
        val xx = inv * (rc * m.xx + rs * m.yx)
        val xy = inv * (rc * m.xy + rs * m.yy)
        val yx = inv * (-rs * m.xx + rc * m.yx)
        val yy = inv * (-rs * m.xy + rc * m.yy)

        val ringScore = (templateScale.rings / 5f).coerceIn(0f, 1f)
        val amplitudeScore = (measured.amplitude / 0.08f).coerceIn(0f, 1f)
        val hintScore = hint.confidence.coerceIn(0f, 1f)
        val confidence = amplitudeScore * 0.40f + ringScore * 0.25f + hintScore * 0.35f
        diagnostics = diagnostics.copy(confidence = confidence, amplitude = measured.amplitude)

        val back = source.width.toFloat() / w
        val calibration = BoardCalibration(
            centerX = cx,
            centerY = cy,
            xx = xx,
            xy = xy,
            yx = yx,
            yy = yy,
            rotationDeg = wrap18(rotation),
            confidence = confidence,
        ).scaled(back)
        return CalibrationResult(calibration, diagnostics)
    }

    private class Conic(
        val cx: Float,
        val cy: Float,
        val qxx: Float,
        val qxy: Float,
        val qyy: Float,
        val residual: Float,
    ) {
        override fun toString(): String = "Conic(c=($cx,$cy) q=($qxx,$qxy,$qyy) res=$residual)"
    }

    private fun shapeFromConic(conic: Conic): Shape = Shape(
        inverse = symmetricPower(conic.qxx, conic.qxy, conic.qyy, 0.5f),
        sqrt = symmetricPower(conic.qxx, conic.qxy, conic.qyy, -0.5f),
    )

    /**
     * Ellipse through the outer ring, fitted twice: once from the approximate shape and once
     * re-selected with the fitted shape. The centre is a free parameter, so a partially
     * sampled ring does not bias it the way a centroid would.
     */
    private fun outerRingFit(
        points: List<Point>,
        inverse: Mat2,
        cx: Float,
        cy: Float,
        ringRadius: Float,
    ): Conic? {
        val first = fitRingOnce(points, inverse, cx, cy, ringRadius, 0.05f) ?: return null
        val refined = shapeFromConic(first)
        return fitRingOnce(points, refined.inverse, first.cx, first.cy, 1f, 0.03f) ?: first
    }

    private fun fitRingOnce(
        points: List<Point>,
        inverse: Mat2,
        cx: Float,
        cy: Float,
        ringRadius: Float,
        tolerance: Float,
    ): Conic? {
        val selected = points.filter { point ->
            val (vx, vy) = apply(inverse, point.x - cx, point.y - cy)
            val rho = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            abs(rho - ringRadius) <= ringRadius * tolerance
        }
        if (selected.size < 40) return null
        return fitConic(selected)
    }

    private fun fitConic(selected: List<Point>): Conic? {
        var sumX = 0.0
        var sumY = 0.0
        for (point in selected) {
            sumX += point.x
            sumY += point.y
        }
        val n = selected.size.toDouble()
        val originX = (sumX / n).toFloat()
        val originY = (sumY / n).toFloat()
        var extent = 1f
        for (point in selected) {
            val distance = hypot(
                (point.x - originX).toDouble(),
                (point.y - originY).toDouble(),
            ).toFloat()
            if (distance > extent) extent = distance
        }

        val matrix = Array(5) { DoubleArray(5) }
        val rhs = DoubleArray(5)
        for (point in selected) {
            val u = ((point.x - originX) / extent).toDouble()
            val v = ((point.y - originY) / extent).toDouble()
            val row = doubleArrayOf(u * u, u * v, v * v, u, v)
            for (i in 0 until 5) {
                rhs[i] += row[i]
                for (j in 0 until 5) matrix[i][j] += row[i] * row[j]
            }
        }
        val coefficients = solve5(matrix, rhs) ?: return null
        return conicFromCoefficients(
            coefficients = coefficients,
            originX = originX.toDouble(),
            originY = originY.toDouble(),
            extent = extent.toDouble(),
            selected = selected,
        )
    }

    private fun conicFromCoefficients(
        coefficients: DoubleArray,
        originX: Double,
        originY: Double,
        extent: Double,
        selected: List<Point>,
    ): Conic? {
        val a = coefficients[0]
        val b = coefficients[1]
        val c = coefficients[2]
        val d = coefficients[3]
        val e = coefficients[4]
        val halfB = b * 0.5
        val det = a * c - halfB * halfB
        if (a <= 1e-12 || det <= 1e-18) return null

        val inv00 = c / det
        val inv01 = -halfB / det
        val inv10 = -halfB / det
        val inv11 = a / det
        val tu = -0.5 * (inv00 * d + inv01 * e)
        val tv = -0.5 * (inv10 * d + inv11 * e)
        val k = tu * (a * tu + halfB * tv) + tv * (halfB * tu + c * tv) + 1.0
        if (k <= 1e-18) return null

        val quxx = a / k
        val quxy = halfB / k
        val quyy = c / k
        if (quxx <= 1e-12 || quyy <= 1e-12) return null

        val half = (quxx + quyy) * 0.5
        val spread = sqrt(((quxx - quyy) * 0.5).let { it * it } + quxy * quxy)
        val lambdaMax = half + spread
        val lambdaMin = half - spread
        if (lambdaMin <= 1e-12) return null
        val axisRatio = sqrt(lambdaMin / lambdaMax)
        if (axisRatio < 0.3 || axisRatio > 1.02) return null

        var residual = 0.0
        for (point in selected) {
            val u = ((point.x - originX) / extent) - tu
            val v = ((point.y - originY) / extent) - tv
            val value = quxx * u * u + 2.0 * quxy * u * v + quyy * v * v
            residual += abs(value - 1.0)
        }
        residual /= selected.size

        val centreX = originX + extent * tu
        val centreY = originY + extent * tv
        val factor = extent * extent
        return Conic(
            cx = centreX.toFloat(),
            cy = centreY.toFloat(),
            qxx = (quxx / factor).toFloat(),
            qxy = (quxy / factor).toFloat(),
            qyy = (quyy / factor).toFloat(),
            residual = residual.toFloat(),
        )
    }

    /** Exact conic through five points, in normalised coordinates for conditioning. */
    private fun conicThrough5(sample: List<Point>): Conic? {
        if (sample.size != 5) return null
        var originX = 0.0
        var originY = 0.0
        for (point in sample) {
            originX += point.x
            originY += point.y
        }
        originX /= 5.0
        originY /= 5.0
        var extent = 1.0
        for (point in sample) {
            val distance = hypot(point.x - originX, point.y - originY)
            if (distance > extent) extent = distance
        }

        val matrix = Array(5) { DoubleArray(5) }
        val rhs = DoubleArray(5)
        for (point in sample) {
            val u = (point.x - originX) / extent
            val v = (point.y - originY) / extent
            val row = doubleArrayOf(u * u, u * v, v * v, u, v)
            for (i in 0 until 5) {
                rhs[i] += row[i]
                for (j in 0 until 5) matrix[i][j] += row[i] * row[j]
            }
        }
        val coefficients = solve5(matrix, rhs) ?: return null
        return conicFromCoefficients(coefficients, originX, originY, extent, sample)
    }

    private fun conicResidual(conic: Conic, point: Point): Float {
        val dx = point.x - conic.cx
        val dy = point.y - conic.cy
        val value = conic.qxx * dx * dx + 2f * conic.qxy * dx * dy + conic.qyy * dy * dy
        val gx = 2f * conic.qxx * dx + 2f * conic.qxy * dy
        val gy = 2f * conic.qxy * dx + 2f * conic.qyy * dy
        val gradient = hypot(gx, gy)
        return if (gradient > 1e-6f) abs(value - 1f) / gradient else Float.MAX_VALUE
    }

    /**
     * Finds the dominant elliptical edge by RANSAC. A full ring has hundreds of inliers;
     * the surround's printed text does not lie on an ellipse, so it cannot win.
     */
    private fun ransacEllipse(points: List<Point>, minDim: Float): Conic? {
        if (points.size < 20) return null
        val random = Random(7)
        val maxPoints = 1600
        val data = if (points.size > maxPoints) {
            val stride = (points.size + maxPoints - 1) / maxPoints
            points.filterIndexed { index, _ -> index % stride == 0 }
        } else {
            points
        }
        if (data.size < 20) return null

        val threshold = max(1.5f, minDim * 0.008f)
        var best: Conic? = null
        var bestInliers = 0
        for (iteration in 0 until 600) {
            val sample = List(5) { data[random.nextInt(data.size)] }
            val conic = conicThrough5(sample) ?: continue
            val half = (conic.qxx + conic.qyy) * 0.5f
            val spread = sqrt(((conic.qxx - conic.qyy) * 0.5f).let { it * it } + conic.qxy * conic.qxy)
            val lambdaMin = half - spread
            if (lambdaMin <= 1e-9f) continue
            val maxRadius = 1f / sqrt(lambdaMin)
            if (maxRadius < minDim * 0.12f || maxRadius > minDim * 0.75f) continue

            // A real ring has inliers all the way around; a spurious ellipse usually covers
            // only an arc.
            var inliers = 0
            var sectors = 0
            for (point in data) {
                if (conicResidual(conic, point) > threshold) continue
                inliers++
                val angle = Math.atan2((point.y - conic.cy).toDouble(), (point.x - conic.cx).toDouble())
                val sector = (((angle + Math.PI) / (2.0 * Math.PI)) * 8.0).toInt().coerceIn(0, 7)
                sectors = sectors or (1 shl sector)
            }
            if (Integer.bitCount(sectors) < 6) continue
            if (inliers > bestInliers) {
                bestInliers = inliers
                best = conic
            }
            if (bestInliers > data.size * 0.6f) break
        }
        val seed = best ?: return null
        val inliers = data.filter { conicResidual(seed, it) <= threshold }
        if (inliers.size < 30) return null
        return fitConic(inliers) ?: seed
    }

    private fun solve5(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val n = 5
        val a = Array(n) { i ->
            DoubleArray(n + 1) { j -> if (j < n) matrix[i][j] else rhs[i] }
        }
        for (column in 0 until n) {
            var pivot = column
            for (row in column + 1 until n) {
                if (abs(a[row][column]) > abs(a[pivot][column])) pivot = row
            }
            if (abs(a[pivot][column]) < 1e-12) return null
            val swap = a[column]
            a[column] = a[pivot]
            a[pivot] = swap

            val divisor = a[column][column]
            for (j in column until n + 1) a[column][j] /= divisor
            for (row in 0 until n) {
                if (row == column) continue
                val factor = a[row][column]
                if (factor == 0.0) continue
                for (j in column until n + 1) a[row][j] -= factor * a[column][j]
            }
        }
        return DoubleArray(n) { a[it][n] }
    }

    /** Sub-pixel peak of the outer-edge radius in the shape-normalised frame. */
    private fun outerRadiusPeak(points: List<Point>, inverse: Mat2, cx: Float, cy: Float): Float {
        val low = 0.85f
        val high = 1.15f
        val binWidth = 0.002f
        val bins = ((high - low) / binWidth).toInt() + 1
        val histogram = FloatArray(bins)
        for (point in points) {
            val (vx, vy) = apply(inverse, point.x - cx, point.y - cy)
            val rho = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            if (rho < low || rho > high) continue
            val bin = ((rho - low) / binWidth).toInt()
            if (bin in 0 until bins) histogram[bin] += point.weight
        }
        val smooth = FloatArray(bins) { i ->
            val left = histogram[max(0, i - 1)]
            val centre = histogram[i]
            val right = histogram[min(bins - 1, i + 1)]
            (left + 2f * centre + right) / 4f
        }
        var best = 0
        for (i in smooth.indices) if (smooth[i] > smooth[best]) best = i
        if (smooth[best] <= 0f) return 1f
        val y0 = smooth[max(0, best - 1)]
        val y1 = smooth[best]
        val y2 = smooth[min(bins - 1, best + 1)]
        val denominator = y0 - 2f * y1 + y2
        val offset = if (abs(denominator) > 1e-6f) 0.5f * (y0 - y2) / denominator else 0f
        return low + (best + offset) * binWidth
    }

    /**
     * The twenty wires are periodic every 18 degrees, so the angular harmonic only fixes
     * rotation modulo one sector. The absolute sector identity comes from the board being
     * mounted with the 20 at the top. Enumerate the twenty candidates and pick the one whose
     * canonical 20 direction points closest to [upDegrees] (the image direction of gravity).
     */
    private fun resolveRotationBranch(phase: Float, shape: Shape, upDegrees: Float): Float {
        val upRad = Math.toRadians(upDegrees.toDouble())
        val upX = sin(upRad).toFloat()
        val upY = -cos(upRad).toFloat()
        var best = phase
        var bestDot = -2f
        for (k in 0 until 20) {
            val delta = phase + 18f * k
            val rad = Math.toRadians(delta.toDouble())
            val c = cos(rad).toFloat()
            val s = sin(rad).toFloat()
            // A = shape.sqrt * R(delta); R(delta) * (0,-1) = (s,-c)
            val vx = s
            val vy = -c
            val ax = shape.sqrt.xx * vx + shape.sqrt.xy * vy
            val ay = shape.sqrt.yx * vx + shape.sqrt.yy * vy
            val length = hypot(ax, ay)
            val dot = if (length > 1e-6f) (ax * upX + ay * upY) / length else -1f
            if (dot > bestDot) {
                bestDot = dot
                best = delta
            }
        }
        return best
    }

    private fun wrap18(value: Float): Float {
        var v = value
        while (v < -9f) v += 18f
        while (v >= 9f) v -= 18f
        return v
    }

    private fun collect(
        field: GradientField,
        w: Int,
        h: Int,
        threshold: Float,
        cx: Float,
        cy: Float,
        maxDistance: Float,
    ): List<Point> {
        val points = ArrayList<Point>()
        val maxDistanceSquared = maxDistance * maxDistance
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val magnitude = field.mag[i]
                if (magnitude <= threshold || magnitude <= 1e-3f) continue
                val dx = x - cx
                val dy = y - cy
                if (dx * dx + dy * dy > maxDistanceSquared) continue
                points.add(
                    Point(
                        x = x.toFloat(),
                        y = y.toFloat(),
                        gx = field.gx[i] / magnitude,
                        gy = field.gy[i] / magnitude,
                        weight = magnitude,
                    ),
                )
            }
        }
        return points
    }

    private fun radialHistogram(
        points: List<Point>,
        cx: Float,
        cy: Float,
        maxRadius: Float,
    ): FloatArray {
        val bins = maxRadius.toInt().coerceAtLeast(32) + 2
        val histogram = FloatArray(bins)
        for (point in points) {
            val distance = hypot((point.x - cx).toDouble(), (point.y - cy).toDouble()).toFloat()
            val bin = distance.toInt()
            if (bin in 0 until bins) histogram[bin] += point.weight
        }
        return FloatArray(bins) { i ->
            val left = histogram[max(0, i - 1)]
            val centre = histogram[i]
            val right = histogram[min(bins - 1, i + 1)]
            (left + 2f * centre + right) / 4f
        }
    }

    /**
     * Outer-double radius in pixels. The treble/double spacings are the board's fingerprint:
     * only the true outer double is paired with a ring about 0.61x its radius. Anchoring on
     * that pair stops a single very strong ring from winning the template.
     */
    private fun ringScale(histogram: FloatArray, minDim: Float): Float? {
        val ratios = floatArrayOf(1.0f, 0.953f, 0.629f, 0.582f, 0.0935f)
        var maxValue = 0f
        for (value in histogram) if (value > maxValue) maxValue = value
        if (maxValue <= 0f) return null

        val peaks = ArrayList<Float>()
        for (i in 2 until histogram.size - 2) {
            if (histogram[i] < maxValue * 0.08f) continue
            if (histogram[i] >= histogram[i - 1] && histogram[i] > histogram[i + 1]) {
                peaks.add(i.toFloat())
            }
        }

        var bestScale = 0f
        var bestScore = -1f
        for (a in peaks.indices) {
            for (b in peaks.indices) {
                val inner = peaks[a]
                val outer = peaks[b]
                if (outer <= inner) continue
                val ratio = inner / outer
                if (ratio < 0.55f || ratio > 0.68f) continue
                if (outer < 0.12f * minDim || outer > 0.55f * minDim) continue
                var score = 0f
                for (expected in ratios) {
                    val bin = (outer * expected).roundToInt()
                    if (bin in histogram.indices) score += histogram[bin]
                }
                if (score > bestScore) {
                    bestScore = score
                    bestScale = outer
                }
            }
        }
        if (bestScale > 0f) return bestScale

        // Fallback: single-ring template.
        var scale = 0.12f * minDim
        val limit = 0.55f * minDim
        while (scale <= limit) {
            var score = 0f
            for (ratio in ratios) {
                val bin = (scale * ratio).roundToInt()
                if (bin in histogram.indices) score += histogram[bin]
            }
            if (score > bestScore) {
                bestScore = score
                bestScale = scale
            }
            scale += 1f
        }
        return if (bestScale > 0f) bestScale else null
    }

    private fun median(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sorted()
        return sorted[sorted.size / 2]
    }

    private fun nearest(points: List<Point>, cx: Float, cy: Float, fraction: Float): List<Point> {
        val take = max(30, (points.size * fraction).toInt()).coerceAtMost(points.size)
        return points
            .sortedBy { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }
            .take(take)
    }

    private fun moments(points: List<Point>): Moments {
        if (points.isEmpty()) return Moments(0f, 0f, 0f, 0f, 0f)
        var sx = 0f
        var sy = 0f
        for (p in points) {
            sx += p.x
            sy += p.y
        }
        val cx = sx / points.size
        val cy = sy / points.size
        var cxx = 0f
        var cxy = 0f
        var cyy = 0f
        for (p in points) {
            val dx = p.x - cx
            val dy = p.y - cy
            cxx += dx * dx
            cxy += dx * dy
            cyy += dy * dy
        }
        val n = points.size.toFloat()
        return Moments(cx, cy, cxx / n, cxy / n, cyy / n)
    }

    /**
     * Edge pixels are sampled evenly along the *image* curve, which under a tilt is not even
     * along the canonical curve. That density bias skews the covariance. Re-weighting each
     * point by 1/|A * tangent| restores canonical arc-length sampling; iterating lets the
     * correction converge on the true shape.
     */
    private fun correctedFit(points: List<Point>, previous: Fit): Fit {
        val forward = invert(previous.shape.inverse)
        val weights = FloatArray(points.size)
        var sumWeight = 0f
        var sumX = 0f
        var sumY = 0f
        for (i in points.indices) {
            val p = points[i]
            val (ux, uy) = apply(previous.shape.inverse, p.x - previous.cx, p.y - previous.cy)
            val theta = atan2(ux.toDouble(), -uy.toDouble())
            val tangentX = cos(theta).toFloat()
            val tangentY = sin(theta).toFloat()
            val imageX = forward.xx * tangentX + forward.xy * tangentY
            val imageY = forward.yx * tangentX + forward.yy * tangentY
            val arc = hypot(imageX.toDouble(), imageY.toDouble()).toFloat().coerceAtLeast(1e-4f)
            val weight = 1f / arc
            weights[i] = weight
            sumWeight += weight
            sumX += weight * p.x
            sumY += weight * p.y
        }
        if (sumWeight <= 1e-6f) return previous
        val cx = sumX / sumWeight
        val cy = sumY / sumWeight
        var cxx = 0f
        var cxy = 0f
        var cyy = 0f
        for (i in points.indices) {
            val p = points[i]
            val weight = weights[i]
            val dx = p.x - cx
            val dy = p.y - cy
            cxx += weight * dx * dx
            cxy += weight * dx * dy
            cyy += weight * dy * dy
        }
        cxx /= sumWeight
        cxy /= sumWeight
        cyy /= sumWeight
        val shape = shapeOfMoments(cxx, cxy, cyy) ?: return Fit(previous.shape, cx, cy)
        return Fit(shape, cx, cy)
    }

    private fun invert(m: Mat2): Mat2 {
        val det = m.xx * m.yy - m.xy * m.yx
        if (abs(det) < 1e-9f) return m
        return Mat2(m.yy / det, -m.xy / det, -m.yx / det, m.xx / det)
    }

    private fun shapeOf(m: Moments): Shape? = shapeOfMoments(m.cxx, m.cxy, m.cyy)

    private fun shapeOfMoments(cxx: Float, cxy: Float, cyy: Float): Shape? {
        val half = (cxx + cyy) * 0.5f
        val delta = sqrt(((cxx - cyy) * 0.5f).let { it * it } + cxy * cxy)
        if (half + delta <= 1e-6f || half - delta <= 1e-6f) return null
        return Shape(
            inverse = symmetricPower(cxx, cxy, cyy, -0.5f),
            sqrt = symmetricPower(cxx, cxy, cyy, 0.5f),
        )
    }

    /** C^power for a symmetric 2x2 matrix, via its closed-form eigendecomposition. */
    private fun symmetricPower(cxx: Float, cxy: Float, cyy: Float, power: Float): Mat2 {
        val theta = 0.5 * atan2(2.0 * cxy.toDouble(), (cxx - cyy).toDouble())
        val c = cos(theta)
        val s = sin(theta)
        val half = (cxx + cyy) * 0.5
        val delta = sqrt(max(0.0, ((cxx - cyy) * 0.5).toDouble().let { it * it } + cxy.toDouble() * cxy))
        val lambda1 = max(1e-9, half + delta)
        val lambda2 = max(1e-9, half - delta)
        val p1 = Math.pow(lambda1, power.toDouble())
        val p2 = Math.pow(lambda2, power.toDouble())
        val xx = (c * c * p1 + s * s * p2).toFloat()
        val xy = (c * s * (p1 - p2)).toFloat()
        val yy = (s * s * p1 + c * c * p2).toFloat()
        return Mat2(xx, xy, xy, yy)
    }

    private fun apply(m: Mat2, x: Float, y: Float): Pair<Float, Float> =
        (m.xx * x + m.xy * y) to (m.yx * x + m.yy * y)

    /** Outer-double scale and a ring-pattern score in the moment-normalised frame. */
    private fun estimateScale(points: List<Point>, inverse: Mat2, cx: Float, cy: Float): ScaleEstimate? {
        val rho = FloatArray(points.size)
        var maxRho = 0f
        for (i in points.indices) {
            val p = points[i]
            val (vx, vy) = apply(inverse, p.x - cx, p.y - cy)
            val value = hypot(vx.toDouble(), vy.toDouble()).toFloat()
            rho[i] = value
            if (value > maxRho) maxRho = value
        }
        if (maxRho <= 1e-3f) return null

        val binWidth = 0.01f
        val bins = ((maxRho / binWidth).toInt() + 2).coerceIn(32, 4000)
        val histogram = FloatArray(bins)
        for (i in points.indices) {
            val bin = (rho[i] / binWidth).toInt()
            if (bin in 0 until bins) histogram[bin] += points[i].weight
        }
        val smooth = FloatArray(bins) { i ->
            val left = histogram[max(0, i - 1)]
            val centre = histogram[i]
            val right = histogram[min(bins - 1, i + 1)]
            (left + 2f * centre + right) / 4f
        }

        val ratios = floatArrayOf(1.0f, 0.953f, 0.629f, 0.582f, 0.0935f)
        var bestScale = 0f
        var bestScore = -1f
        var scale = 0.30f
        while (scale < maxRho * 1.02f) {
            var score = 0f
            for (ratio in ratios) {
                val bin = (scale * ratio / binWidth).roundToInt()
                if (bin in 0 until bins) score += smooth[bin]
            }
            if (score > bestScore) {
                bestScore = score
                bestScale = scale
            }
            scale += binWidth * 0.5f
        }
        if (bestScale <= 0f) return null

        var rings = 0
        for (ratio in ratios) {
            val bin = (bestScale * ratio / binWidth).roundToInt()
            if (bin !in 0 until bins) continue
            val window = max(2, (bestScale * 0.10f / binWidth).toInt())
            val neighbours = ArrayList<Float>()
            for (k in max(0, bin - window)..min(bins - 1, bin + window)) {
                if (abs(k - bin) <= 1) continue
                neighbours.add(smooth[k])
            }
            if (neighbours.isEmpty()) continue
            neighbours.sort()
            val baseline = neighbours[neighbours.size / 2]
            val prominence = smooth[bin] / (baseline + 1e-3f)
            if (prominence > 1.6f) rings++
        }
        return ScaleEstimate(bestScale, rings)
    }

    private fun estimateRotation(points: List<Point>, shape: Shape, cx: Float, cy: Float): Rotation? {
        val inverse = shape.inverse
        val root = shape.sqrt
        var real = 0.0
        var imaginary = 0.0
        var total = 0f
        for (p in points) {
            val (vx, vy) = apply(inverse, p.x - cx, p.y - cy)
            // Gradients transform with C^(1/2) into the normalised frame.
            val gx = root.xx * p.gx + root.xy * p.gy
            val gy = root.yx * p.gx + root.yy * p.gy
            val theta = atan2(vx.toDouble(), -vy.toDouble())
            val tangential = abs(gx * cos(theta).toFloat() + gy * sin(theta).toFloat()) * p.weight
            real += tangential * cos(20.0 * theta)
            imaginary += tangential * sin(20.0 * theta)
            total += tangential
        }
        if (total <= 1e-3f) return null

        val delta = Math.toDegrees(atan2(imaginary, real)) / 20.0
        val wrapped = (((delta - 9.0) + 9.0) % 18.0 + 18.0) % 18.0 - 9.0
        val amplitude = (hypot(real, imaginary) / total).toFloat()
        return Rotation(wrapped.toFloat(), amplitude)
    }

    private fun blur(src: FloatArray, w: Int, h: Int): FloatArray {
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var count = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx !in 0 until w) continue
                        sum += src[yy * w + xx]
                        count++
                    }
                }
                out[y * w + x] = sum / count
            }
        }
        return out
    }

    private fun gradients(src: FloatArray, w: Int, h: Int): GradientField {
        val gx = FloatArray(w * h)
        val gy = FloatArray(w * h)
        val mag = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val tl = src[i - w - 1]
                val tc = src[i - w]
                val tr = src[i - w + 1]
                val ml = src[i - 1]
                val mr = src[i + 1]
                val bl = src[i + w - 1]
                val bc = src[i + w]
                val br = src[i + w + 1]
                val dx = (tr + 2f * mr + br) - (tl + 2f * ml + bl)
                val dy = (bl + 2f * bc + br) - (tl + 2f * tc + tr)
                gx[i] = dx
                gy[i] = dy
                mag[i] = hypot(dx, dy)
            }
        }
        return GradientField(gx, gy, mag)
    }

    private fun percentile(values: FloatArray, fraction: Float): Float {
        val copy = values.copyOf()
        copy.sort()
        val index = ((copy.size - 1) * fraction).toInt().coerceIn(0, copy.size - 1)
        return copy[index]
    }
}

/**
 * Stabilises per-frame calibrations. A fixed board should not visibly move, so samples are
 * median-filtered over a short window and outliers (wrong ring, sector flip, missed circle)
 * are ignored until they persist long enough to mean the camera actually moved.
 */
class CalibrationTracker(
    private val window: Int = 9,
    private val toleranceFraction: Float = 0.08f,
    private val relockAfter: Int = 6,
    private val minConfidence: Float = 0.08f,
    private val maxMisses: Int = 8,
) {
    private val samples = ArrayDeque<BoardCalibration>()
    private var current: BoardCalibration? = null
    private var outliers = 0
    private var misses = 0

    fun current(): BoardCalibration? = current

    fun isLocked(): Boolean = samples.size >= 3

    fun update(sample: BoardCalibration?): BoardCalibration? {
        if (sample == null || sample.confidence < minConfidence) {
            misses++
            if (misses >= maxMisses) {
                samples.clear()
                current = null
                outliers = 0
            }
            return current
        }
        misses = 0

        val reference = current
        val locked = samples.size >= 3
        if (reference != null && !agree(reference, sample)) {
            if (!locked) {
                // Still warming up: restart rather than lock onto a bad first frame.
                samples.clear()
                outliers = 0
            } else {
                outliers++
                if (outliers < relockAfter) return current
                samples.clear()
                outliers = 0
            }
        } else {
            outliers = 0
        }

        samples.addLast(sample)
        while (samples.size > window) samples.removeFirst()
        current = medianOf(samples)
        return current
    }

    fun reset() {
        samples.clear()
        current = null
        outliers = 0
        misses = 0
    }

    private fun agree(a: BoardCalibration, b: BoardCalibration): Boolean {
        val scale = max(a.radiusApprox, b.radiusApprox)
        return transformDistance(a, b) <= scale * toleranceFraction
    }

    /** Mean pixel distance of canonical board points under the two transforms. */
    private fun transformDistance(a: BoardCalibration, b: BoardCalibration): Float {
        val probes = arrayOf(
            0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f,
            0.5f to -0.5f, -0.5f to 0.5f, 0f to 0f,
        )
        var sum = 0f
        for ((u, v) in probes) {
            val pa = project(a, u, v)
            val pb = project(b, u, v)
            sum += hypot(
                (pa.first - pb.first).toDouble(),
                (pa.second - pb.second).toDouble(),
            ).toFloat()
        }
        return sum / probes.size
    }

    private fun project(calibration: BoardCalibration, u: Float, v: Float): Pair<Float, Float> {
        val det = calibration.xx * calibration.yy - calibration.xy * calibration.yx
        if (abs(det) < 1e-9f) return calibration.centerX to calibration.centerY
        val a00 = calibration.yy / det
        val a01 = -calibration.xy / det
        val a10 = -calibration.yx / det
        val a11 = calibration.xx / det
        return (a00 * u + a01 * v + calibration.centerX) to
            (a10 * u + a11 * v + calibration.centerY)
    }

    private fun medianOf(values: Collection<BoardCalibration>): BoardCalibration {
        val n = values.size
        fun median(selector: (BoardCalibration) -> Float): Float {
            val sorted = values.map(selector).sorted()
            return sorted[n / 2]
        }
        val base = values.first().rotationDeg
        val rotations = values.map { wrap18(it.rotationDeg - base) }.sorted()
        return BoardCalibration(
            centerX = median { it.centerX },
            centerY = median { it.centerY },
            xx = median { it.xx },
            xy = median { it.xy },
            yx = median { it.yx },
            yy = median { it.yy },
            rotationDeg = wrap18(base + rotations[n / 2]),
            confidence = median { it.confidence },
        )
    }

    private fun wrap18(value: Float): Float {
        var v = value
        while (v < -9f) v += 18f
        while (v >= 9f) v -= 18f
        return v
    }
}
