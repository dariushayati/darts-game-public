package com.darts.app

import com.darts.core.GameEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SoundCuePolicyTest {
    private val add = GameEvent.ShieldAdded(target = 20, playerId = 0, shields = 1)
    private val lock = GameEvent.SectorLocked(target = 20, playerId = 0)
    private val removed = GameEvent.ShieldRemoved(target = 20, ownerId = 0, shields = 1)
    private val neutralised = GameEvent.SectorNeutralised(target = 20, previousOwnerId = 0)
    private val blocked = GameEvent.BlockedByLock(target = 20)
    private val miss = GameEvent.HitMissed(playerId = 0)

    @Test
    fun `lock wins over the shield added event`() {
        assertEquals(SoundCue.LOCK, soundCueFor(listOf(add, lock)))
        assertEquals(SoundCue.LOCK, soundCueFor(listOf(lock, add)))
    }

    @Test
    fun `plain add plays the add cue`() {
        assertEquals(SoundCue.ADD, soundCueFor(listOf(add)))
    }

    @Test
    fun `partial and full removals play the remove cue`() {
        assertEquals(SoundCue.REMOVE, soundCueFor(listOf(removed)))
        assertEquals(SoundCue.REMOVE, soundCueFor(listOf(neutralised)))
    }

    @Test
    fun `blocked and missed darts are silent`() {
        assertNull(soundCueFor(listOf(blocked)))
        assertNull(soundCueFor(listOf(miss)))
        assertNull(soundCueFor(emptyList()))
    }
}
