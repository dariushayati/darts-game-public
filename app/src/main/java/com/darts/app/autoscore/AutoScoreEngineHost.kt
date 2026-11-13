package com.darts.app.autoscore

import android.content.Context

/**
 * One native detector per process. The standalone camera screen and the in-game
 * controller take turns using it, which avoids loading the YOLO model twice and
 * keeps native init/release from overlapping during navigation.
 */
object AutoScoreEngineHost {
    @Volatile
    private var engine: AutoScoreEngine? = null

    fun get(context: Context): AutoScoreEngine = synchronized(this) {
        engine ?: AutoScoreEngine.create(context.applicationContext).also { engine = it }
    }
}
