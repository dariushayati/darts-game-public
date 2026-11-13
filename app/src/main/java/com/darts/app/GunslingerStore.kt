package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.GunslingerDifficulty
import com.darts.core.GunslingerRules

class GunslingerStore(context: Context) {
    private val prefs = context.getSharedPreferences("gunslinger_rules", Context.MODE_PRIVATE)

    fun load(): GunslingerRules {
        val storedDrop = prefs.getInt("pointsPerSecond", 50).coerceIn(0, 500)
        val difficulty = prefs.getString("difficulty", null)
            ?.let { runCatching { GunslingerDifficulty.valueOf(it) }.getOrNull() }
            ?: GunslingerDifficulty.NORMAL
        return GunslingerRules(
            rounds = prefs.getInt("rounds", 5).coerceIn(1, 50),
            beeps = prefs.getInt("beeps", 3).coerceIn(0, 10),
            pointsPerSecond = storedDrop / 50 * 50,
            difficulty = difficulty,
        )
    }

    fun save(rules: GunslingerRules) {
        prefs.edit {
            putInt("rounds", rules.rounds)
            putInt("beeps", rules.beeps)
            putInt("pointsPerSecond", rules.pointsPerSecond)
            putString("difficulty", rules.difficulty.name)
        }
    }
}
