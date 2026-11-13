package com.darts.vision

import com.darts.core.AutoCalibrator
import com.darts.core.BoardCalibration
import com.darts.core.BoardGeometry
import com.darts.core.CalibrationTracker
import com.darts.core.EllipseHint
import com.darts.core.GrayImage
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class DetectionHarnessTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun loadOpenCv() {
            nu.pattern.OpenCV.loadLocally()
        }
    }

    private fun detectAndCalibrate(
        frame: SyntheticFrame,
        previous: BoardCalibration? = null,
    ): Pair<EllipseHint, com.darts.core.CalibrationResult>? {
        frame.rgba.rewind()
        val detection = OpenCvBoardDetector.detect(
            frame.rgba,
            frame.width,
            frame.height,
            frame.width * 4,
            previous,
        ) ?: return null
        val result = AutoCalibrator.calibrateFromEllipse(
            detection.chroma,
            detection.hint,
            upDegrees = frame.tiltDeg,
        )
        return detection.hint to result
    }

    private fun accuracy(frame: SyntheticFrame, calibration: BoardCalibration): Float {
        val random = kotlin.random.Random(11)
        var total = 0
        var correct = 0
        val rings = listOf(1.0f, 0.953f, 0.629f, 0.582f, 0.0935f, 0.0374f)
        repeat(1200) {
            val u = random.nextFloat() * 2f - 1f
            val v = random.nextFloat() * 2f - 1f
            val r = hypot(u.toDouble(), v.toDouble()).toFloat()
            if (r > 0.98f) return@repeat
            if (rings.any { abs(r - it) < 0.02f }) return@repeat
            val degrees = Math.toDegrees(atan2(u.toDouble(), -v.toDouble())).toFloat()
            val boundary = ((degrees + 9f) % 18f + 18f) % 18f
            if (min(boundary, 18f - boundary) < 1.5f) return@repeat
            val expected = BoardGeometry.hitTest(u, v)
            val (px, py) = frame.boardAffine.project(u, v)
            val actual = calibration.hitAt(px, py)
            total++
            if (actual.number == expected.number && actual.multiplier == expected.multiplier) correct++
        }
        return if (total == 0) 0f else correct.toFloat() / total
    }

    @Test
    fun `synthetic boards`() {
        val cases = listOf(
            "front" to SyntheticFrame(tiltRatio = 1.0f, tiltDeg = 0f, rotationDeg = 0f),
            "tilt10" to SyntheticFrame(tiltRatio = 0.85f, tiltDeg = 10f, rotationDeg = 5f),
            "tilt-20" to SyntheticFrame(
                tiltRatio = 0.72f,
                tiltDeg = -20f,
                rotationDeg = -4f,
                centerX = 170f,
                centerY = 140f,
            ),
            "dim" to SyntheticFrame(
                tiltRatio = 0.90f,
                tiltDeg = 6f,
                rotationDeg = 3f,
                lighting = 0.55f,
            ),
            "no-text" to SyntheticFrame(
                tiltRatio = 0.80f,
                tiltDeg = 14f,
                rotationDeg = -6f,
                surroundText = false,
            ),
            "dark-cast-surround" to SyntheticFrame(
                tiltRatio = 0.85f,
                tiltDeg = 8f,
                rotationDeg = 2f,
                noise = 9,
                surroundTint = intArrayOf(26, 18, 38),
            ),
        )
        for ((name, frame) in cases) {
            val detected = detectAndCalibrate(frame)
            assertNotNull("no detection for $name", detected)
            val (hint, result) = detected!!
            val calibration = result.calibration
            assertNotNull("no calibration for $name diag=${result.diagnostics}", calibration)
            val score = accuracy(frame, calibration!!)
            println(
                "CASE $name src=${hint.source} hint=(${hint.centerX.toInt()},${hint.centerY.toInt()}," +
                    "${hint.semiMajor.toInt()}/${hint.semiMinor.toInt()}) conf=${calibration.confidence} " +
                    "rings=${result.diagnostics.rings} acc=$score",
            )
            writeAnnotated(name, frame, hint, calibration)
            assertTrue("accuracy $score for $name", score > 0.95f)
        }
    }

    @Test
    fun `temporal stability`() {
        val tracker = CalibrationTracker()
        var previous: BoardCalibration? = null
        val centres = ArrayList<Float>()
        val radii = ArrayList<Float>()
        val rotations = ArrayList<Float>()
        for (i in 0 until 30) {
            val jitter = kotlin.random.Random(i.toLong())
            val frame = SyntheticFrame(
                centerX = 180f + (jitter.nextFloat() * 2f - 1f),
                centerY = 135f + (jitter.nextFloat() * 2f - 1f),
                radius = 95f * (1f + (jitter.nextFloat() * 2f - 1f) * 0.005f),
                tiltRatio = 0.82f,
                tiltDeg = 12f + (jitter.nextFloat() * 2f - 1f) * 0.5f,
                rotationDeg = 4f + (jitter.nextFloat() * 2f - 1f) * 0.4f,
                noise = 6,
                seed = i.toLong(),
            )
            val detection = OpenCvBoardDetector.detect(
                frame.rgba,
                frame.width,
                frame.height,
                frame.width * 4,
                previous,
            )
            val result = detection?.let {
                AutoCalibrator.calibrateFromEllipse(
                    it.chroma,
                    it.hint,
                    upDegrees = frame.tiltDeg,
                )
            }
            val tracked = tracker.update(result?.calibration)
            previous = tracked
            if (i >= 12 && tracked != null) {
                centres.add(tracked.centerX)
                radii.add(tracked.radiusApprox)
                rotations.add(tracked.rotationDeg)
            }
        }
        val centreStd = std(centres)
        val radiusStd = std(radii)
        val rotationStd = std(rotations)
        println("STABILITY n=${centres.size} centreStd=$centreStd radiusStd=$radiusStd rotationStd=$rotationStd")
        assertTrue("centre jitter $centreStd", centreStd < 1.5f)
        assertTrue("scale jitter $radiusStd", radiusStd < 1.5f)
    }

    @Test
    fun `real frames if present`() {
        val folders = listOf(File("../frames"), File("frames"))
        val folder = folders.firstOrNull { it.isDirectory } ?: return
        val files = folder.listFiles { file ->
            file.extension.lowercase() in setOf("png", "jpg", "jpeg")
        }?.sortedBy { it.name } ?: return
        for (file in files) {
            val bgr = Imgcodecs.imread(file.absolutePath)
            if (bgr.empty()) continue
            val rgba = Mat()
            Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
            val width = rgba.cols()
            val height = rgba.rows()
            val bytes = ByteArray(width * height * 4)
            rgba.get(0, 0, bytes)
            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.rewind()
            val detection = OpenCvBoardDetector.detect(buffer, width, height, width * 4, null)
            println("REAL ${file.name} hint=${detection?.hint}")
            if (detection != null) {
                val result = AutoCalibrator.calibrateFromEllipse(
                    detection.chroma,
                    detection.hint,
                    upDegrees = 0f,
                )
                println("REAL ${file.name} calib=${result.calibration} diag=${result.diagnostics}")
                if (result.calibration != null) {
                    val scaled = Mat()
                    Imgproc.resize(
                        bgr,
                        scaled,
                        Size(
                            detection.chroma.width.toDouble(),
                            detection.chroma.height.toDouble(),
                        ),
                    )
                    writeAnnotated(
                        "real-${file.nameWithoutExtension}",
                        scaled,
                        detection.hint,
                        result.calibration!!,
                    )
                    scaled.release()
                }
            }
            rgba.release()
            bgr.release()
        }
        runRealSequence(files)
    }

    private fun runRealSequence(files: List<File>) {
        if (files.isEmpty()) return
        val tracker = CalibrationTracker()
        var previous: BoardCalibration? = null
        val centres = ArrayList<Float>()
        val radii = ArrayList<Float>()
        val rotations = ArrayList<Float>()
        println("--- real sequence with tracker ---")
        for (file in files) {
            val bgr = Imgcodecs.imread(file.absolutePath)
            if (bgr.empty()) continue
            val rgba = Mat()
            Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
            val width = rgba.cols()
            val height = rgba.rows()
            val bytes = ByteArray(width * height * 4)
            rgba.get(0, 0, bytes)
            val buffer = ByteBuffer.allocateDirect(bytes.size)
            buffer.put(bytes)
            buffer.rewind()
            val detection = OpenCvBoardDetector.detect(buffer, width, height, width * 4, previous)
            val result = detection?.let {
                AutoCalibrator.calibrateFromEllipse(it.chroma, it.hint, upDegrees = 0f)
            }
            val tracked = tracker.update(result?.calibration)
            previous = tracked
            if (tracked != null) {
                centres.add(tracked.centerX)
                radii.add(tracked.radiusApprox)
                rotations.add(tracked.rotationDeg)
                println(
                    "SEQ ${file.name} src=${detection?.hint?.source} " +
                        "raw=(${result?.calibration?.centerX?.toInt()},${result?.calibration?.centerY?.toInt()}," +
                        "${result?.calibration?.radiusApprox?.toInt()},${result?.calibration?.rotationDeg?.toInt()}) " +
                        "tracked=(${tracked.centerX.toInt()},${tracked.centerY.toInt()}," +
                        "${tracked.radiusApprox.toInt()},${tracked.rotationDeg.toInt()})",
                )
            } else {
                println("SEQ ${file.name} src=${detection?.hint?.source} no calibration")
            }
            rgba.release()
            bgr.release()
        }
        println(
            "REAL STABILITY n=${centres.size} centreStd=${std(centres)} " +
                "radiusStd=${std(radii)} rotationStd=${std(rotations)}",
        )
    }

    private fun writeAnnotated(name: String, frame: SyntheticFrame, hint: EllipseHint, calibration: BoardCalibration) {
        writeAnnotation(name, frame.width, frame.height, frame.bytes, hint, calibration)
    }

    private fun writeAnnotated(name: String, bgr: Mat, hint: EllipseHint, calibration: BoardCalibration) {
        val width = bgr.cols()
        val height = bgr.rows()
        val rgbaBytes = ByteArray(width * height * 4)
        val rgba = Mat()
        Imgproc.cvtColor(bgr, rgba, Imgproc.COLOR_BGR2RGBA)
        rgba.get(0, 0, rgbaBytes)
        rgba.release()
        writeAnnotation(name, width, height, rgbaBytes, hint, calibration)
    }

    private fun writeAnnotation(
        name: String,
        width: Int,
        height: Int,
        rgbaBytes: ByteArray,
        hint: EllipseHint,
        calibration: BoardCalibration,
    ) {
        val bgr = Mat(height, width, CvType.CV_8UC3)
        val out = ByteArray(width * height * 3)
        for (i in 0 until width * height) {
            out[i * 3] = rgbaBytes[i * 4 + 2]
            out[i * 3 + 1] = rgbaBytes[i * 4 + 1]
            out[i * 3 + 2] = rgbaBytes[i * 4]
        }
        bgr.put(0, 0, out)
        Imgproc.ellipse(
            bgr,
            Point(hint.centerX.toDouble(), hint.centerY.toDouble()),
            Size(hint.semiMajor.toDouble(), hint.semiMinor.toDouble()),
            Math.toDegrees(hint.angleRadians.toDouble()),
            0.0,
            360.0,
            Scalar(0.0, 255.0, 255.0),
            2,
        )
        val det = calibration.xx * calibration.yy - calibration.xy * calibration.yx
        fun project(u: Float, v: Float): Point {
            val a00 = calibration.yy / det
            val a01 = -calibration.xy / det
            val a10 = -calibration.yx / det
            val a11 = calibration.xx / det
            return Point(
                (a00 * u + a01 * v + calibration.centerX).toDouble(),
                (a10 * u + a11 * v + calibration.centerY).toDouble(),
            )
        }
        for (ratio in listOf(1.0f, 0.953f, 0.629f, 0.582f, 0.0935f)) {
            val points = (0..120).map { k ->
                val angle = 2.0 * Math.PI * k / 120.0
                project((sin(angle) * ratio).toFloat(), (-cos(angle) * ratio).toFloat())
            }
            Imgproc.polylines(
                bgr,
                listOf(MatOfPoint(*points.toTypedArray())),
                false,
                Scalar(0.0, 255.0, 0.0),
                1,
            )
        }
        for (k in 0 until 20) {
            val angle = Math.toRadians(9.0 + k * 18.0)
            val p0 = project((sin(angle) * 0.1).toFloat(), (-cos(angle) * 0.1).toFloat())
            val p1 = project((sin(angle) * 1.0).toFloat(), (-cos(angle) * 1.0).toFloat())
            Imgproc.line(bgr, p0, p1, Scalar(0.0, 255.0, 0.0), 1)
        }
        val directory = File("build/sim")
        directory.mkdirs()
        Imgcodecs.imwrite(File(directory, "$name.png").absolutePath, bgr)
        bgr.release()
    }

    private fun std(values: List<Float>): Float {
        if (values.isEmpty()) return Float.MAX_VALUE
        val mean = values.average().toFloat()
        return sqrt(values.sumOf { ((it - mean) * (it - mean)).toDouble() } / values.size).toFloat()
    }
}
