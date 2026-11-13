package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.MatchHistoryCodec
import com.darts.core.MatchRecord

/** Local-only match history (SharedPreferences JSON). Never leaves the device. */
class MatchHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): List<MatchRecord> = MatchHistoryCodec.decode(prefs.getString(KEY_HISTORY, null))

    fun save(matches: List<MatchRecord>) {
        prefs.edit { putString(KEY_HISTORY, MatchHistoryCodec.encode(matches)) }
    }

    companion object {
        private const val PREFS = "darts_match_history"
        private const val KEY_HISTORY = "history"
    }
}
