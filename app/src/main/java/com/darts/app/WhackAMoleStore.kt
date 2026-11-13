package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.WhackAMoleRules

class WhackAMoleStore(context: Context) {
    private val prefs = context.getSharedPreferences("whack_mole_rules", Context.MODE_PRIVATE)

    fun load(): WhackAMoleRules = WhackAMoleRules(
        moles = prefs.getInt("moles", 8).coerceIn(1, 20),
        hardMode = prefs.getBoolean("hardMode", false),
    )

    fun save(rules: WhackAMoleRules) {
        prefs.edit {
            putInt("moles", rules.moles)
            putBoolean("hardMode", rules.hardMode)
        }
    }
}
