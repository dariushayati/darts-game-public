package com.darts.app

import android.content.Context
import androidx.core.content.edit
import com.darts.core.GameRules

class RulesStore(context: Context) {
    private val prefs = context.getSharedPreferences("darts_rules", Context.MODE_PRIVATE)

    // Every darts turn is three darts.
    fun load(): GameRules = GameRules(
        dartsPerPlayer = ((prefs.getInt("dartsPerPlayer", prefs.getInt("maxDarts", 15)) / 3) * 3).coerceIn(9, 300),
        endOnBoardFull = prefs.getBoolean("endOnBoardFull", true),
        singleLevels = prefs.getInt("singleLevels", 1).coerceIn(1, 3),
        doubleLevels = prefs.getInt("doubleLevels", 2).coerceIn(1, 4),
        tripleLevels = prefs.getInt("tripleLevels", 3).coerceIn(2, 6),
    )

    fun save(rules: GameRules) {
        prefs.edit {
            putInt("singleLevels", rules.singleLevels)
            putInt("doubleLevels", rules.doubleLevels)
            putInt("tripleLevels", rules.tripleLevels)
            putInt("dartsPerPlayer", rules.dartsPerPlayer)
            putBoolean("endOnBoardFull", rules.endOnBoardFull)
        }
    }
}
