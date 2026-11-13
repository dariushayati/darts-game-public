package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.LavaRules

class LavaStore(context: Context) {
    private val prefs = context.getSharedPreferences("lava_rules", Context.MODE_PRIVATE)

    fun load(): LavaRules = LavaRules(
        lives = prefs.getInt("lives", 3).coerceIn(1, 9),
        lavaPerRound = prefs.getInt("lavaPerRound", 3).coerceIn(1, 20),
    )

    fun save(rules: LavaRules) {
        prefs.edit {
            putInt("lives", rules.lives)
            putInt("lavaPerRound", rules.lavaPerRound)
        }
    }
}
