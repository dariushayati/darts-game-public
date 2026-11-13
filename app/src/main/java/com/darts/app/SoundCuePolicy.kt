package com.darts.app

import com.darts.core.GameEvent

enum class SoundCue { ADD, LOCK, REMOVE, LIFE_LOST, MISS, BEEP }

fun soundCueFor(events: List<GameEvent>): SoundCue? = when {
    events.any { it is GameEvent.SectorLocked } -> SoundCue.LOCK
    events.any { it is GameEvent.ShieldAdded } -> SoundCue.ADD
    events.any { it is GameEvent.ShieldRemoved || it is GameEvent.SectorNeutralised } -> SoundCue.REMOVE
    else -> null
}
