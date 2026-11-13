package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.InvadersRules

class InvadersStore(context: Context) {
    private val prefs = context.getSharedPreferences("invaders_rules", Context.MODE_PRIVATE)

    fun load(): InvadersRules = InvadersRules(
        dartsPerPlayer = ((prefs.getInt("dartsPerPlayer", 15) / 3) * 3).coerceIn(15, 300),
    )

    fun save(rules: InvadersRules) {
        prefs.edit {
            putInt("dartsPerPlayer", rules.dartsPerPlayer)
        }
    }
}
