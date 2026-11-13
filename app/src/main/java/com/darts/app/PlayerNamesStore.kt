package com.darts.app

import android.content.Context
import androidx.core.content.edit

/** Persists the six setup name slots so names survive leaving or restarting the app. */
class PlayerNamesStore(context: Context) {
    private val prefs = context.getSharedPreferences("darts_player_names", Context.MODE_PRIVATE)

    fun load(): List<String> = List(SLOTS) { index ->
        prefs.getString(key(index), null)
            ?.takeIf { it.isNotBlank() }
            ?: defaultName(index)
    }

    fun save(names: List<String>) {
        prefs.edit {
            for (index in 0 until SLOTS) {
                putString(key(index), names.getOrElse(index) { defaultName(index) })
            }
        }
    }

    private fun key(index: Int): String = "player${index + 1}"

    private fun defaultName(index: Int): String = "Player ${index + 1}"

    private companion object {
        const val SLOTS = 6
    }
}
