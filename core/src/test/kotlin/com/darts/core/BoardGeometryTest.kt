package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class BoardGeometryTest {

    @Test
    fun `top of the board is the 20`() {
        val hit = BoardGeometry.hitTest(0f, -0.8f)
        assertEquals(20, hit.number)
        assertEquals(1, hit.multiplier)
        assertEquals(Ring.SINGLE, hit.ring)
        assertEquals(SingleBand.LARGE, hit.singleBand)
    }

    @Test
    fun `right is 6, bottom is 3, left is 11`() {
        assertEquals(6, BoardGeometry.hitTest(0.8f, 0f).number)
        assertEquals(3, BoardGeometry.hitTest(0f, 0.8f).number)
        assertEquals(11, BoardGeometry.hitTest(-0.8f, 0f).number)
    }

    @Test
    fun `every sector centre maps back to itself`() {
        for (i in 0 until BoardGeometry.SECTOR_COUNT) {
            val a = Math.toRadians((i * BoardGeometry.SECTOR_ANGLE).toDouble())
            val x = sin(a).toFloat() * 0.8f
            val y = -cos(a).toFloat() * 0.8f
            assertEquals(BoardGeometry.SECTOR_ORDER[i], BoardGeometry.hitTest(x, y).number)
        }
    }

    @Test
    fun `rings map to the right multiplier`() {
        assertEquals(Ring.DOUBLE, BoardGeometry.hitTest(0f, -0.98f).ring)
        assertEquals(2, BoardGeometry.hitTest(0f, -0.98f).multiplier)
        assertEquals(Ring.TREBLE, BoardGeometry.hitTest(0f, -0.60f).ring)
        assertEquals(3, BoardGeometry.hitTest(0f, -0.60f).multiplier)
        assertEquals(Ring.SINGLE, BoardGeometry.hitTest(0f, -0.80f).ring)
        assertEquals(SingleBand.LARGE, BoardGeometry.hitTest(0f, -0.80f).singleBand)
        assertEquals(SingleBand.SMALL, BoardGeometry.hitTest(0f, -0.40f).singleBand)
    }

    @Test
    fun `the bull is one target worth one shield`() {
        val inner = BoardGeometry.hitTest(0f, 0f)
        assertEquals(BoardGeometry.BULL, inner.number)
        assertEquals(1, inner.multiplier)
        assertEquals(Ring.INNER_BULL, inner.ring)

        val outer = BoardGeometry.hitTest(0f, 0.07f)
        assertEquals(BoardGeometry.BULL, outer.number)
        assertEquals(1, outer.multiplier)
        assertEquals(Ring.OUTER_BULL, outer.ring)
    }

    @Test
    fun `off the board is a miss`() {
        val hit = BoardGeometry.hitTest(1.2f, 0f)
        assertTrue(hit.isMiss)
        assertNull(hit.number)
        assertEquals(0, hit.score)
    }

    @Test
    fun `targets are the twenty numbers plus bull`() {
        assertEquals(21, BoardGeometry.TARGETS.size)
        assertTrue(BoardGeometry.BULL in BoardGeometry.TARGETS)
        assertTrue(BoardGeometry.TARGETS.containsAll((1..20).toList()))
    }
}
