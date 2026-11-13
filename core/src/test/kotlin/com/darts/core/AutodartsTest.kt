package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutodartsTest {

    private val sample = """
        {"connected":true,"event":"throwDetected","running":true,"status":"throw","throws":[
          {"bed":"SingleOuter","bouncer":false,"coordsX":0.319,"coordsY":0.632,"failed":false,
           "method":"BestCam","multiplier":1,"segmentName":"S1","segmentNumber":1},
          {"bed":"SingleOuter","bouncer":false,"coordsX":-0.094,"coordsY":-0.668,"failed":false,
           "method":"BestCam","multiplier":1,"segmentName":"S3","segmentNumber":3}
        ]}
    """.trimIndent()

    @Test
    fun `parses the real board payload`() {
        val state = Autodarts.parseState(sample)!!
        assertTrue(state.connected)
        assertEquals("throwDetected", state.event)
        assertEquals(2, state.throws.size)

        val darts = state.throws.map { it.toDartHit() }
        assertEquals(1, darts[0].number)
        assertEquals(1, darts[0].multiplier)
        assertEquals(3, darts[1].number)
        assertEquals(Ring.SINGLE, darts[1].ring)
        assertEquals(SingleBand.LARGE, darts[1].singleBand)
    }

    @Test
    fun `landing coordinates are attached with the board y axis`() {
        val state = Autodarts.parseState(sample)!!
        val first = state.throws[0].toDartHit()
        // Autodarts y grows up; the renderer's y grows down.
        assertEquals(0.319f, first.x!!, 0.0001f)
        assertEquals(-0.632f, first.y!!, 0.0001f)
        val firstProbe = BoardGeometry.hitTest(first.x!!, first.y!!)
        assertEquals(1, firstProbe.number)
        assertEquals(Ring.SINGLE, firstProbe.ring)
        assertEquals(SingleBand.LARGE, firstProbe.singleBand)

        val second = state.throws[1].toDartHit()
        val secondProbe = BoardGeometry.hitTest(second.x!!, second.y!!)
        assertEquals(3, secondProbe.number)
        assertEquals(SingleBand.LARGE, secondProbe.singleBand)
    }

    @Test
    fun `throws without coordinates have no landing point`() {
        val hit = AutodartsThrow(segmentNumber = 20, multiplier = 3).toDartHit()
        assertNull(hit.x)
        assertNull(hit.y)
    }

    @Test
    fun `surround misses keep their landing point for rendering`() {
        val miss = AutodartsThrow(
            segmentName = "M19",
            segmentNumber = 19,
            multiplier = 1,
            coordsX = 0.2f,
            coordsY = 1.31f,
        ).toDartHit()
        assertTrue(miss.isMiss)
        assertEquals(0.2f, miss.x!!, 0.0001f)
        assertEquals(-1.31f, miss.y!!, 0.0001f)
    }

    @Test
    fun `triple double and bull map correctly`() {
        val t20 = AutodartsThrow(segmentName = "T20", segmentNumber = 20, multiplier = 3).toDartHit()
        assertEquals(20, t20.number)
        assertEquals(3, t20.multiplier)
        assertEquals(Ring.TREBLE, t20.ring)

        assertEquals(Ring.DOUBLE, AutodartsThrow(segmentNumber = 16, multiplier = 2).toDartHit().ring)
        val bull = AutodartsThrow(segmentName = "BULL", segmentNumber = 25, multiplier = 2).toDartHit()
        assertEquals(25, bull.number)
        assertEquals(1, bull.multiplier)
        assertEquals(Ring.INNER_BULL, bull.ring)
        assertEquals(Ring.OUTER_BULL, AutodartsThrow(segmentNumber = 25, multiplier = 1).toDartHit().ring)
    }

    @Test
    fun `single bands come from the bed name`() {
        val large = AutodartsThrow(
            bed = "SingleOuter",
            segmentNumber = 20,
            multiplier = 1,
        ).toDartHit()
        assertEquals(SingleBand.LARGE, large.singleBand)

        val small = AutodartsThrow(
            bed = "SingleInner",
            segmentNumber = 20,
            multiplier = 1,
        ).toDartHit()
        assertEquals(SingleBand.SMALL, small.singleBand)
    }

    @Test
    fun `bouncer and failed are misses`() {
        assertTrue(AutodartsThrow(segmentNumber = 20, multiplier = 3, bouncer = true).toDartHit().isMiss)
        assertTrue(AutodartsThrow(segmentNumber = 20, multiplier = 3, failed = true).toDartHit().isMiss)
    }

    @Test
    fun `darts outside the double ring are misses, not the nearest number`() {
        // The board anchors surround darts to the nearest wedge: M1..M20 / MISS / OUT
        // with the neighbour's segmentNumber. None of them may score.
        assertTrue(
            AutodartsThrow(bed = "SingleOuter", segmentName = "M20", segmentNumber = 20, multiplier = 1)
                .toDartHit().isMiss,
        )
        assertTrue(AutodartsThrow(segmentName = "m3", segmentNumber = 3, multiplier = 1).toDartHit().isMiss)
        assertTrue(AutodartsThrow(segmentName = "OUT", segmentNumber = 7, multiplier = 1).toDartHit().isMiss)
        assertTrue(AutodartsThrow(segmentName = "MISS", segmentNumber = 19, multiplier = 1).toDartHit().isMiss)
        assertTrue(
            AutodartsThrow(bed = "Outside", segmentName = "S16", segmentNumber = 16, multiplier = 1)
                .toDartHit().isMiss,
        )
        assertTrue(AutodartsThrow(segmentName = "S20", segmentNumber = 20, multiplier = 0).toDartHit().isMiss)
    }

    @Test
    fun `surround coordinates override the nearest number`() {
        val surround = AutodartsThrow(
            segmentName = "S19",
            segmentNumber = 19,
            multiplier = 1,
            coordsX = 0.2f,
            coordsY = 1.31f,
        ).toDartHit()
        assertTrue(surround.isMiss)

        val nearWire = AutodartsThrow(
            segmentName = "D20",
            segmentNumber = 20,
            multiplier = 2,
            coordsX = 0.01f,
            coordsY = -0.99f,
        ).toDartHit()
        assertEquals(20, nearWire.number)
        assertEquals(Ring.DOUBLE, nearWire.ring)
    }

    @Test
    fun `segment name fallback`() {
        assertEquals(5, AutodartsThrow(segmentName = "S5", segmentNumber = 0).toDartHit().number)
        assertEquals(25, AutodartsThrow(segmentName = "S25", segmentNumber = 0).toDartHit().number)
        assertNull(AutodartsThrow(segmentName = "MISS").toDartHit().number)
    }

    @Test
    fun `stream emits only new darts and detects a visit end`() {
        val stream = AutodartsThrowStream()

        var update = stream.update(AutodartsState(throws = emptyList()))
        assertTrue(update.darts.isEmpty())
        assertTrue(!update.visitEnded)

        update = stream.update(Autodarts.parseState(sample)!!)
        assertEquals(2, update.darts.size)
        assertTrue(!update.visitEnded)

        update = stream.update(Autodarts.parseState(sample)!!)
        assertTrue(update.darts.isEmpty())

        update = stream.update(AutodartsState(throws = emptyList()))
        assertTrue(update.darts.isEmpty())
        assertTrue(update.visitEnded)

        update = stream.update(Autodarts.parseState(sample)!!)
        assertEquals(2, update.darts.size)
    }

    @Test
    fun `taking darts out one at a time does not re-emit them`() {
        val stream = AutodartsThrowStream()
        val state = Autodarts.parseState(sample)!!
        assertEquals(2, stream.update(state).darts.size)

        val oneRemoved = state.copy(throws = state.throws.take(1))
        val shrink = stream.update(oneRemoved)
        assertTrue(shrink.darts.isEmpty())
        assertTrue(!shrink.visitEnded)

        val allRemoved = stream.update(state.copy(throws = emptyList()))
        assertTrue(allRemoved.darts.isEmpty())
        assertTrue(allRemoved.visitEnded)
    }

    @Test
    fun `advanceTurn moves to the next player`() {
        val players = listOf(
            Player(0, "P1", 0),
            Player(1, "P2", 1),
        )
        val start = ShieldsMode.newGame(players)
        val next = ShieldsMode.advanceTurn(start).state
        assertEquals(1, next.currentPlayerIndex)
        assertEquals(GameState.DARTS_PER_TURN, next.dartsLeft)
    }

    @Test
    fun `board status derives from connection and events`() {
        assertEquals(AutodartsBoardStatus.OFFLINE, deriveBoardStatus(null))
        assertEquals(
            AutodartsBoardStatus.OFFLINE,
            deriveBoardStatus(AutodartsState(connected = false, running = true)),
        )
        assertEquals(
            AutodartsBoardStatus.READY,
            deriveBoardStatus(AutodartsState(connected = true, running = true, event = "throwDetected")),
        )
        assertEquals(
            AutodartsBoardStatus.TAKEOUT,
            deriveBoardStatus(AutodartsState(connected = true, running = true, event = "Takeout started")),
        )
        assertEquals(
            AutodartsBoardStatus.TAKEOUT,
            deriveBoardStatus(AutodartsState(connected = true, running = true, status = "takeout")),
        )
        assertEquals(
            AutodartsBoardStatus.READY,
            deriveBoardStatus(AutodartsState(connected = true, running = true, event = "Takeout finished")),
        )
        assertEquals(
            AutodartsBoardStatus.UNKNOWN,
            deriveBoardStatus(AutodartsState(connected = true, running = false)),
        )
    }
}
