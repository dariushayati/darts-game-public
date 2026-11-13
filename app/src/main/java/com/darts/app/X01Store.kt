package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.X01InRule
import com.darts.core.X01OutRule
import com.darts.core.X01Rules

class X01Store(context: Context) {
    private val prefs = context.getSharedPreferences("x01_rules", Context.MODE_PRIVATE)

    fun load(): X01Rules = X01Rules(
        startingScore = prefs.getInt("startingScore", 501)
            .takeIf { it in X01Rules.STARTING_SCORES } ?: 501,
        inRule = prefs.getString("inRule", null)
            ?.let { runCatching { X01InRule.valueOf(it) }.getOrNull() }
            ?: X01InRule.STRAIGHT,
        outRule = prefs.getString("outRule", null)
            ?.let { runCatching { X01OutRule.valueOf(it) }.getOrNull() }
            ?: X01OutRule.DOUBLE,
        legsToWin = prefs.getInt("legsToWin", 1).coerceIn(1, 7),
    )

    fun save(rules: X01Rules) {
        prefs.edit {
            putInt("startingScore", rules.startingScore)
            putString("inRule", rules.inRule.name)
            putString("outRule", rules.outRule.name)
            putInt("legsToWin", rules.legsToWin)
        }
    }
}
