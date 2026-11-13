package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.RobinHoodRules

class RobinHoodStore(context: Context) {
    private val prefs = context.getSharedPreferences("robin_hood_rules", Context.MODE_PRIVATE)

    fun load(): RobinHoodRules = RobinHoodRules(
        rounds = prefs.getInt("rounds", 5).coerceIn(1, 20),
    )

    fun save(rules: RobinHoodRules) {
        prefs.edit {
            putInt("rounds", rules.rounds)
        }
    }
}
