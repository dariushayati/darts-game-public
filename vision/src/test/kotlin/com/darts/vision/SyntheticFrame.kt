package com.darts.vision

import com.darts.core.GrayImage
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

data class Affine(
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    val tx: Float,
    val ty: Float,
) {
    fun project(u: Float, v: Float): Pair<Float, Float> =
        (a * u + b * v + tx) to (c * u + d * v + ty)

    fun unproject(x: Float, y: Float): Pair<Float, Float> {
        val det = a * d - b * c
        val dx = x - tx
        val dy = y - ty
        return ((d * dx - b * dy) / det) to ((-c * dx + a * dy) / det)
    }

    /** this * R(degrees), R clockwise in screen coordinates. */
    fun timesRotation(degrees: Float): Affine {
        val t = Math.toRadians(degrees.toDouble())
        val co = cos(t).toFloat()
        val si = sin(t).toFloat()
        return Affine(
            a = a * co + b * si,
            b = -a * si + b * co,
            c = c * co + d * si,
            d = -c * si + d * co,
            tx = tx,
            ty = ty,
        )
    }
}

/** Synthetic colour dartboard with a black/white printed surround, like the real frame. */
class SyntheticFrame(
    val width: Int = 360,
    val height: Int = 270,
    val centerX: Float = 180f,
    val centerY: Float = 135f,
    val radius: Float = 95f,
    val tiltRatio: Float = 1f,
    val tiltDeg: Float = 0f,
    val rotationDeg: Float = 0f,
    val lighting: Float = 1f,
    val noise: Int = 4,
    val surroundText: Boolean = true,
    val seed: Long = 1L,
    val surroundTint: IntArray = intArrayOf(20, 20, 20),
) {
    val affine: Affine
    val boardAffine: Affine
    val bytes: ByteArray
    val rgba: ByteBuffer
    val gray: GrayImage
    val chroma: GrayImage

    init {
        val t = Math.toRadians(tiltDeg.toDouble())
        affine = Affine(
            a = (cos(t) * radius).toFloat(),
            b = (-sin(t) * radius * tiltRatio).toFloat(),
            c = (sin(t) * radius).toFloat(),
            d = (cos(t) * radius * tiltRatio).toFloat(),
            tx = centerX,
            ty = centerY,
        )
        boardAffine = affine.timesRotation(rotationDeg)

        val random = Random(seed)
        bytes = ByteArray(width * height * 4)
        val red = intArrayOf(196, 43, 58)
        val green = intArrayOf(46, 139, 87)
        val cream = intArrayOf(231, 220, 195)
        val black = intArrayOf(26, 28, 33)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val (u, v) = affine.unproject(x + 0.5f, y + 0.5f)
                val r = hypot(u.toDouble(), v.toDouble()).toFloat()
                var col = intArrayOf(18, 18, 18)
                if (r <= 1.13f) col = surroundTint
                if (r <= 1.0f) {
                    val degrees = Math.toDegrees(atan2(u.toDouble(), -v.toDouble())).toFloat()
                    val rotated = degrees - rotationDeg
                    val index = floor((((rotated + 9f) % 360f + 360f) % 360f) / 18f).toInt() % 20
                    val base = if (index % 2 == 0) red else green
                    val single = if (index % 2 == 0) cream else black
                    col = when {
                        r <= 0.0374f -> red
                        r <= 0.0935f -> green
                        r <= 0.582f -> single
                        r <= 0.629f -> base
                        r <= 0.953f -> single
                        else -> base
                    }
                    val boundary = ((rotated + 9f) % 18f + 18f) % 18f
                    val angular = min(boundary, 18f - boundary)
                    val arc = angular * (Math.PI.toFloat() / 180f) * r * radius
                    if (r > 0.08f && arc < 1.4f) col = intArrayOf(25, 25, 25)
                }
                val i = (y * width + x) * 4
                for (c in 0 until 3) {
                    val value = (col[c] * lighting).toInt() + random.nextInt(-noise, noise + 1)
                    bytes[i + c] = value.coerceIn(0, 255).toByte()
                }
                bytes[i + 3] = 255.toByte()
            }
        }

        if (surroundText) {
            repeat(90) {
                val x0 = random.nextInt(width)
                val y0 = random.nextInt(height)
                val bw = random.nextInt(8, 40)
                val bh = random.nextInt(3, 12)
                for (yy in y0 until minOf(height, y0 + bh)) {
                    for (xx in x0 until minOf(width, x0 + bw)) {
                        val (u, v) = affine.unproject(xx + 0.5f, yy + 0.5f)
                        if (hypot(u.toDouble(), v.toDouble()) > 1.13) {
                            val i = (yy * width + xx) * 4
                            bytes[i] = 240.toByte()
                            bytes[i + 1] = 240.toByte()
                            bytes[i + 2] = 240.toByte()
                            bytes[i + 3] = 255.toByte()
                        }
                    }
                }
            }
        }

        rgba = ByteBuffer.allocateDirect(bytes.size)
        rgba.put(bytes)
        rgba.rewind()
        gray = GrayImage.fromRgba(bytes, width, height, width * 4, 4)
        chroma = GrayImage.fromRgbaChroma(bytes, width, height, width * 4, 4)
    }
}
