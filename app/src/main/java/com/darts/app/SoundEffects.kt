package com.darts.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Short SoundPool cues for gameplay feedback. */
class SoundEffects(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val add = pool.load(context, R.raw.shield_add, 1)
    private val lock = pool.load(context, R.raw.shield_lock, 1)
    private val remove = pool.load(context, R.raw.shield_remove, 1)
    private val lifeLost = pool.load(context, R.raw.life_lost, 1)
    private val miss = pool.load(context, R.raw.miss, 1)
    private val beep = pool.load(context, R.raw.beep, 1)

    fun play(cue: SoundCue) {
        val sound = when (cue) {
            SoundCue.ADD -> add
            SoundCue.LOCK -> lock
            SoundCue.REMOVE -> remove
            SoundCue.LIFE_LOST -> lifeLost
            SoundCue.MISS -> miss
            SoundCue.BEEP -> beep
        }
        val volume = if (cue == SoundCue.LIFE_LOST) 1f else 0.8f
        if (sound != 0) pool.play(sound, volume, volume, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
