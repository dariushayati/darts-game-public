package com.darts.app

import com.darts.core.CricketEvent

/**
 * Cricket sound priority: a closing hit locks, a mark or score confirms, and a miss
 * gets the soft miss cue. A hit that closes a target also emits [CricketEvent.MarksAdded],
 * so the close check must come first.
 */
fun cricketSoundCue(events: List<CricketEvent>): SoundCue? = when {
    events.any { it is CricketEvent.TargetClosed } -> SoundCue.LOCK
    events.any { it is CricketEvent.PointsScored || it is CricketEvent.MarksAdded } -> SoundCue.ADD
    events.any { it is CricketEvent.HitMissed } -> SoundCue.MISS
    else -> null
}
