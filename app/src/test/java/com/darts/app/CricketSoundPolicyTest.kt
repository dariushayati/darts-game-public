package com.darts.app

import com.darts.core.CricketEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CricketSoundPolicyTest {
    private val marks = CricketEvent.MarksAdded(playerId = 0, target = 20, marks = 1, total = 1)
    private val closed = CricketEvent.TargetClosed(playerId = 0, target = 20)
    private val scored = CricketEvent.PointsScored(playerId = 0, target = 20, points = 20, total = 20)
    private val missed = CricketEvent.HitMissed(playerId = 0)

    @Test
    fun `closing a target wins over its added marks`() {
        assertEquals(SoundCue.LOCK, cricketSoundCue(listOf(marks, closed)))
        assertEquals(SoundCue.LOCK, cricketSoundCue(listOf(closed)))
    }

    @Test
    fun `marks and points use the add cue`() {
        assertEquals(SoundCue.ADD, cricketSoundCue(listOf(marks)))
        assertEquals(SoundCue.ADD, cricketSoundCue(listOf(scored)))
    }

    @Test
    fun `miss uses the miss cue`() {
        assertEquals(SoundCue.MISS, cricketSoundCue(listOf(missed)))
    }

    @Test
    fun `silent when nothing happened`() {
        assertNull(cricketSoundCue(emptyList()))
    }
}
