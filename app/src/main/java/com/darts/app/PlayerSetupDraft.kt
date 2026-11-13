package com.darts.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Draft of the player roster shown on the game setup screens. It lives above the
 * screens (remembered in [DartsApp]) so visiting the rules editor — or any other
 * screen — and coming back keeps the chosen player count and the typed names.
 * [reset] is called only when a setup screen is opened fresh from the games menu.
 */
class PlayerSetupDraft(private val namesStore: PlayerNamesStore) {
    var count: Int by mutableIntStateOf(DEFAULT_COUNT)

    private var namesState by mutableStateOf(namesStore.load())

    /** Persist every edit, not just the names visible when a game starts. */
    var names: List<String>
        get() = namesState
        set(value) {
            val normalized = List(SLOTS) { index -> value.getOrElse(index) { "" } }
            namesState = normalized
            namesStore.save(normalized)
        }

    fun reset() {
        count = DEFAULT_COUNT
        namesState = namesStore.load()
    }

    private companion object {
        const val DEFAULT_COUNT = 2
        const val SLOTS = 6
    }
}
