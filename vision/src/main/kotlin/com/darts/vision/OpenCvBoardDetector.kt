package com.darts.vision

import com.darts.core.BoardCalibration
import com.darts.core.EllipseHint
import com.darts.core.GrayImage
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.RotatedRect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Board localisation with OpenCV.
 *
 * Acquisition uses colour: the board's red/green segments are the only saturated thing, so
 * a saturation mask removes the black/white surround and its print. Once locked, tracking
 * switches to a grey crop around the previously locked board, so saturated objects (a hand)
 * and the surround are both out of the picture.
 */
data class Detection(val hint: EllipseHint, val chroma: GrayImage)

object OpenCvBoardDetector {
    private const val MAX_DIMENSION = 360

    fun detect(
        rgba: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        previous: BoardCalibration? = null,
    ): Detection? {
        if (width < 32 || height < 32 || rowStride <= 0) return null

        val longest = max(width, height)
        val scale = min(1f, MAX_DIMENSION.toFloat() / longest)
        val targetWidth = max(1, (width * scale).roundToInt())
        val targetHeight = max(1, (height * scale).roundToInt())

        val full = Mat(height, width, CvType.CV_8UC4, rgba, rowStride.toLong())
        val small = Mat()
        try {
            Imgproc.resize(
                full,
                small,
                Size(targetWidth.toDouble(), targetHeight.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_AREA,
            )

            // Lab a* is the red-green opponent channel: lighting-robust and 20-fold strong.
            val chroma = labChroma(small)

            // Once locked, track the same board on a grey crop: no surround, no colour outliers.
            if (previous != null) {
                val locked = detectLocked(small, previous, targetWidth, targetHeight)
                if (locked != null) return Detection(locked, chroma)
            }

            // Acquisition (or recovery): colour.
            val colour = detectColour(small, targetWidth, targetHeight, previous)
            if (colour != null) return Detection(colour, chroma)

            // Last resort: grey Hough over the whole frame.
            return detectGreyFull(small, targetWidth, targetHeight, previous)?.let {
                Detection(it, chroma)
            }
        } catch (t: Throwable) {
            return null
        } finally {
            full.release()
            small.release()
        }
    }

    private fun labChroma(small: Mat): GrayImage {
        val bgr = Mat()
        val lab = Mat()
        val channel = Mat()
        try {
            Imgproc.cvtColor(small, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)
            Core.extractChannel(lab, channel, 1)
            val bytes = ByteArray(channel.cols() * channel.rows())
            channel.get(0, 0, bytes)
            return GrayImage(channel.cols(), channel.rows(), bytes)
        } finally {
            bgr.release()
            lab.release()
            channel.release()
        }
    }

    private fun detectColour(
        small: Mat,
        width: Int,
        height: Int,
        previous: BoardCalibration?,
    ): EllipseHint? {
        val bgr = Mat()
        val lab = Mat()
        val lightness = Mat()
        val redGreen = Mat()
        val difference = Mat()
        val chroma = Mat()
        val bright = Mat()
        val mask = Mat()
        val region = Mat.zeros(height, width, CvType.CV_8UC1)
        try {
            Imgproc.cvtColor(small, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)
            Core.extractChannel(lab, lightness, 0)
            Core.extractChannel(lab, redGreen, 1)
            Core.absdiff(redGreen, Scalar(128.0), difference)

            val centreX = previous?.centerX ?: (width / 2f)
            val centreY = previous?.centerY ?: (height / 2f)
            val regionRadius = (min(width, height) * 0.70).roundToInt()
            Imgproc.circle(
                region,
                Point(centreX.toDouble(), centreY.toDouble()),
                regionRadius,
                Scalar(255.0),
                -1,
            )

            // Acquire on the RED board segments: the surround's border/numbers are
            // green/white, so they drop out. Red is the board's most reliable colour.
            for ((redMin, brightness, label) in listOf(
                Triple(146.0, 65.0, "colour-red"),
                Triple(138.0, 50.0, "colour-red-loose"),
            )) {
                Core.inRange(redGreen, Scalar(redMin), Scalar(255.0), chroma)
                Core.inRange(lightness, Scalar(brightness), Scalar(238.0), bright)
                Core.bitwise_and(chroma, bright, mask)
                Core.bitwise_and(mask, region, mask)
                val hint = fromMask(mask, width, height, label, assumeOuterDouble = true)
                if (hint != null) return hint
            }
            // Fallback: green segments (some boards/lights favour green).
            for ((greenMax, brightness, label) in listOf(
                Triple(110.0, 65.0, "colour-green"),
                Triple(118.0, 50.0, "colour-green-loose"),
            )) {
                Core.inRange(redGreen, Scalar(0.0), Scalar(greenMax), chroma)
                Core.inRange(lightness, Scalar(brightness), Scalar(238.0), bright)
                Core.bitwise_and(chroma, bright, mask)
                Core.bitwise_and(mask, region, mask)
                val hint = fromMask(mask, width, height, label, assumeOuterDouble = true)
                if (hint != null) return hint
            }
            return null
        } finally {
            bgr.release()
            lab.release()
            lightness.release()
            redGreen.release()
            difference.release()
            chroma.release()
            bright.release()
            mask.release()
            region.release()
        }
    }

    private fun detectLocked(
        small: Mat,
        previous: BoardCalibration,
        width: Int,
        height: Int,
    ): EllipseHint? {
        val radius = previous.radiusApprox
        if (radius < 12f) return null
        val margin = 1.35f
        val x0 = (previous.centerX - radius * margin).roundToInt().coerceIn(0, width - 1)
        val y0 = (previous.centerY - radius * margin).roundToInt().coerceIn(0, height - 1)
        val x1 = (previous.centerX + radius * margin).roundToInt().coerceIn(x0 + 1, width)
        val y1 = (previous.centerY + radius * margin).roundToInt().coerceIn(y0 + 1, height)
        if (x1 - x0 < 40 || y1 - y0 < 40) return null

        val grey = Mat()
        var sub: Mat? = null
        val blurred = Mat()
        val circles = Mat()
        val edges = Mat()
        val nonZero = MatOfPoint()
        try {
            Imgproc.cvtColor(small, grey, Imgproc.COLOR_RGBA2GRAY)
            sub = Mat(grey, Rect(x0, y0, x1 - x0, y1 - y0))
            Imgproc.GaussianBlur(sub!!, blurred, Size(5.0, 5.0), 1.5)
            Imgproc.HoughCircles(
                blurred,
                circles,
                Imgproc.HOUGH_GRADIENT,
                1.4,
                radius * 0.35,
                120.0,
                45.0,
                (radius * 0.45).toInt(),
                (radius * 1.25).toInt(),
            )
            if (circles.cols() == 0) return null

            var best: DoubleArray? = null
            var bestScore = -Double.MAX_VALUE
            for (i in 0 until circles.cols()) {
                val candidate = circles.get(0, i) ?: continue
                val globalX = candidate[0] + x0
                val globalY = candidate[1] + y0
                val score = -abs(candidate[2] - radius) -
                    hypot(globalX - previous.centerX, globalY - previous.centerY) * 0.5
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            val circle = best ?: return null

            Imgproc.Canny(blurred, edges, 60.0, 160.0)
            Core.findNonZero(edges, nonZero)
            val selected = nonZero.toArray().map { point ->
                Point(point.x + x0, point.y + y0)
            }.filter { point ->
                val distance = hypot(point.x - previous.centerX, point.y - previous.centerY)
                distance in (radius * 0.97)..(radius * 1.03)
            }
            if (selected.size < 30) return null
            val rect = Imgproc.fitEllipse(MatOfPoint2f(*selected.toTypedArray()))
            val hint = hintFromRect(
                rect = rect,
                width = width,
                height = height,
                confidence = 0.8,
                source = "locked",
                assumeOuterDouble = true,
            ) ?: return null
            val global = hint
            if (hypot(global.centerX - previous.centerX, global.centerY - previous.centerY) >
                radius * 0.25f
            ) {
                return null
            }
            if (global.semiMajor < radius * 0.85f || global.semiMajor > radius * 1.15f) return null
            return global
        } finally {
            grey.release()
            sub?.release()
            blurred.release()
            circles.release()
            edges.release()
            nonZero.release()
        }
    }

    private fun detectGreyFull(
        small: Mat,
        width: Int,
        height: Int,
        previous: BoardCalibration?,
    ): EllipseHint? {
        val grey = Mat()
        val blurred = Mat()
        val edges = Mat()
        val circles = Mat()
        try {
            Imgproc.cvtColor(small, grey, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(grey, blurred, Size(5.0, 5.0), 1.5)
            val minDim = min(width, height).toDouble()
            fun findCircles(param2: Double) {
                Imgproc.HoughCircles(
                    blurred,
                    circles,
                    Imgproc.HOUGH_GRADIENT,
                    1.4,
                    minDim * 0.25,
                    120.0,
                    param2,
                    (minDim * 0.16).toInt(),
                    (minDim * 0.48).toInt(),
                )
            }
            findCircles(55.0)
            if (circles.cols() == 0) findCircles(38.0)
            if (circles.cols() == 0) return null

            val imageCentreX = width / 2.0
            val imageCentreY = height / 2.0
            var best: DoubleArray? = null
            var bestScore = -Double.MAX_VALUE
            for (i in 0 until circles.cols()) {
                val candidate = circles.get(0, i) ?: continue
                val distance = hypot(candidate[0] - imageCentreX, candidate[1] - imageCentreY)
                val previousPenalty = if (previous != null) {
                    hypot(
                        candidate[0] - previous.centerX.toDouble(),
                        candidate[1] - previous.centerY.toDouble(),
                    ) * 3.0
                } else {
                    0.0
                }
                val score = candidate[2] - distance * 1.5 - previousPenalty
                if (score > bestScore) {
                    bestScore = score
                    best = candidate
                }
            }
            val circle = best ?: return null
            Imgproc.Canny(blurred, edges, 60.0, 160.0)
            val nonZero = MatOfPoint()
            try {
                Core.findNonZero(edges, nonZero)
                val minRadius = circle[2] * 0.90
                val maxRadius = circle[2] * 1.10
                val selected = nonZero.toArray().filter { point ->
                    val distance = hypot(point.x - circle[0], point.y - circle[1])
                    distance in minRadius..maxRadius
                }
                if (selected.size < 30) return null
                val rect = Imgproc.fitEllipse(MatOfPoint2f(*selected.toTypedArray()))
                val hint = hintFromRect(
                    rect = rect,
                    width = width,
                    height = height,
                    confidence = 0.65,
                    source = "grey",
                    assumeOuterDouble = false,
                )
                if (hint != null) {
                }
                return hint
            } finally {
                nonZero.release()
            }
        } finally {
            grey.release()
            blurred.release()
            edges.release()
            circles.release()
        }
    }

    private fun fromMask(
        mask: Mat,
        width: Int,
        height: Int,
        source: String,
        assumeOuterDouble: Boolean,
    ): EllipseHint? {
        val points = MatOfPoint()
        val indices = MatOfInt()
        try {
            Core.findNonZero(mask, points)
            if (points.rows() < 80) return null
            Imgproc.convexHull(points, indices)
            val order = indices.toArray()
            if (order.size < 6) return null
            val all = points.toArray()
            var hull = Array(order.size) { all[order[it]] }
            var rect = Imgproc.fitEllipse(MatOfPoint2f(*hull))

            // Trimmed refit rejects a few saturated outliers (e.g. a hand at the rim).
            repeat(2) {
                val kept = trimOutliers(rect, hull, 0.80)
                if (kept.size >= 6) {
                    hull = kept
                    rect = Imgproc.fitEllipse(MatOfPoint2f(*hull))
                }
            }
            val confidence = (order.size / 45.0).coerceIn(0.55, 0.95)
            return hintFromRect(
                rect = rect,
                width = width,
                height = height,
                confidence = confidence,
                source = source,
                assumeOuterDouble = assumeOuterDouble,
            )
        } finally {
            points.release()
            indices.release()
        }
    }

    private fun trimOutliers(rect: RotatedRect, points: Array<Point>, keep: Double): Array<Point> {
        val major = max(rect.size.width, rect.size.height) / 2.0
        val minor = min(rect.size.width, rect.size.height) / 2.0
        if (major <= 1.0 || minor <= 1.0) return points
        var angle = rect.angle
        if (rect.size.width < rect.size.height) angle += 90.0
        val theta = Math.toRadians(angle)
        val c = cos(theta)
        val s = sin(theta)
        val residuals = points.map { point ->
            val dx = point.x - rect.center.x
            val dy = point.y - rect.center.y
            val u = (dx * c + dy * s) / major
            val v = (-dx * s + dy * c) / minor
            abs(u * u + v * v - 1.0)
        }
        val threshold = residuals.sorted()[(points.size * keep).toInt().coerceIn(0, points.size - 1)]
        return points.filterIndexed { index, _ -> residuals[index] <= threshold }.toTypedArray()
    }

    private fun hintFromRect(
        rect: RotatedRect,
        width: Int,
        height: Int,
        confidence: Double,
        source: String,
        assumeOuterDouble: Boolean,
    ): EllipseHint? {
        val w = rect.size.width
        val h = rect.size.height
        if (w <= 4.0 || h <= 4.0) return null
        val major = max(w, h) / 2.0
        val minor = min(w, h) / 2.0
        val ratio = minor / major
        if (ratio < 0.35 || ratio > 1.0) return null
        val minDim = min(width, height).toDouble()
        if (major < minDim * 0.10 || major > minDim * 0.75) return null
        if (rect.center.x !in 0.0..width.toDouble() || rect.center.y !in 0.0..height.toDouble()) {
            return null
        }
        var angle = rect.angle
        if (w < h) angle += 90.0
        angle = ((angle % 180.0) + 180.0) % 180.0
        return EllipseHint(
            centerX = rect.center.x.toFloat(),
            centerY = rect.center.y.toFloat(),
            semiMajor = major.toFloat(),
            semiMinor = minor.toFloat(),
            angleRadians = Math.toRadians(angle).toFloat(),
            confidence = confidence.coerceIn(0.3, 0.95).toFloat(),
            source = source,
            assumeOuterDouble = assumeOuterDouble,
        )
    }
}
