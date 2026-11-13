package com.darts.app.autoscore

import com.darts.core.BoardGeometry
import com.darts.core.Ring
import com.darts.core.SingleBand
import io.github.thomasff.bulltrack.core.domain.model.DartPosition
import io.github.thomasff.bulltrack.core.domain.model.RingType
import io.github.thomasff.bulltrack.core.domain.model.ThrowResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoScoreMappingTest {

    @Test
    fun mapsSinglesDoublesAndTrebles() {
        val outer = throwResult(20, RingType.OUTER_SINGLE).toAutoScoredDart().hit
        assertEquals(20, outer.number)
        assertEquals(1, outer.multiplier)
        assertEquals(Ring.SINGLE, outer.ring)
        assertEquals(SingleBand.LARGE, outer.singleBand)

        val inner = throwResult(19, RingType.INNER_SINGLE).toAutoScoredDart().hit
        assertEquals(19, inner.number)
        assertEquals(Ring.SINGLE, inner.ring)
        assertEquals(SingleBand.SMALL, inner.singleBand)

        val double = throwResult(18, RingType.DOUBLE).toAutoScoredDart().hit
        assertEquals(18, double.number)
        assertEquals(2, double.multiplier)
        assertEquals(Ring.DOUBLE, double.ring)
        assertNull(double.singleBand)

        val treble = throwResult(17, RingType.TRIPLE).toAutoScoredDart().hit
        assertEquals(17, treble.number)
        assertEquals(3, treble.multiplier)
        assertEquals(Ring.TREBLE, treble.ring)
    }

    @Test
    fun mapsBothBullsToTheAppsBullTarget() {
        val inner = throwResult(25, RingType.INNER_BULL).toAutoScoredDart().hit
        assertEquals(BoardGeometry.BULL, inner.number)
        assertEquals(1, inner.multiplier)
        assertEquals(Ring.INNER_BULL, inner.ring)
        assertNull(inner.singleBand)

        val outer = throwResult(25, RingType.OUTER_BULL).toAutoScoredDart().hit
        assertEquals(BoardGeometry.BULL, outer.number)
        assertEquals(1, outer.multiplier)
        assertEquals(Ring.OUTER_BULL, outer.ring)
    }

    @Test
    fun mapsMisses() {
        val miss = ThrowResult(sector = 0, ring = RingType.MISS, score = 0)
            .toAutoScoredDart()
            .hit
        assertTrue(miss.isMiss)
        assertNull(miss.number)
        assertEquals(0, miss.multiplier)
        assertEquals(Ring.MISS, miss.ring)
    }

    @Test
    fun mapsNeedsCheckFlag() {
        val dart = ThrowResult(
            sector = 20,
            ring = RingType.TRIPLE,
            score = 60,
            needsCheck = true,
        ).toAutoScoredDart()
        assertTrue(dart.needsCheck)
    }

    @Test
    fun scalesBullTrackPositionToTheAppsOuterDoubleRadius() {
        // BullTrack's virtual outer double is 0.92; ours is 1.0. A top-of-board
        // double must end up at the top of our board, not at 0.92.
        val top = ThrowResult(
            sector = 20,
            ring = RingType.DOUBLE,
            score = 40,
            position = DartPosition(0f, -0.92f),
        ).toAutoScoredDart().hit
        assertEquals(0f, top.x!!, 0.001f)
        assertEquals(-1f, top.y!!, 0.001f)
        assertEquals(Ring.DOUBLE, top.ring)
    }

    @Test
    fun clampsPositionsOutsideTheBoard() {
        val miss = ThrowResult(
            sector = 0,
            ring = RingType.MISS,
            score = 0,
            position = DartPosition(1.2f, 0f),
        ).toAutoScoredDart().hit
        assertEquals(1f, miss.x!!, 0.001f)
        assertEquals(0f, miss.y!!, 0.001f)
    }

    @Test
    fun aScoringRingWithAnInvalidSectorFallsBackToAMiss() {
        val dart = ThrowResult(sector = 0, ring = RingType.TRIPLE, score = 60)
            .toAutoScoredDart()
        assertTrue(dart.hit.isMiss)
        assertTrue(dart.needsCheck)
    }

    private fun throwResult(sector: Int, ring: RingType) = ThrowResult(
        sector = sector,
        ring = ring,
        score = 0,
    )
}
