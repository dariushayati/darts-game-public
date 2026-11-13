package com.darts.core

import com.google.gson.Gson

/**
 * A finished match as kept in the on-device history. [game] is the [GameType] name
 * (e.g. `SHIELDS`) so the history can colour and label itself, and [gameTitle] is the
 * name shown at the time of play in case a game is renamed later.
 */
data class MatchRecord(
    val id: String,
    val game: String,
    val gameTitle: String,
    val finishedAt: Long,
    val winnerId: Int? = null,
    val endReason: String? = null,
    val players: List<MatchPlayerRecord> = emptyList(),
) {
    fun winnerName(): String? = winnerId?.let { id -> players.firstOrNull { it.id == id }?.name }
}

data class MatchPlayerRecord(
    val id: Int,
    val name: String,
    val colorIndex: Int,
    /** Per-game headline metric: points, shields, kills, lives, or moles. */
    val score: Int,
    val darts: Int,
    /** Optional extra line, e.g. "2 locked" or "4 kills". */
    val detail: String? = null,
)

/** JSON (de)serialisation for the match history; malformed data degrades to an empty list. */
object MatchHistoryCodec {
    private val gson = Gson()

    fun encode(matches: List<MatchRecord>): String = gson.toJson(matches)

    fun decode(json: String?): List<MatchRecord> = try {
        if (json.isNullOrBlank()) {
            emptyList()
        } else {
            gson.fromJson(json, Array<MatchRecord>::class.java)?.toList() ?: emptyList()
        }
    } catch (_: Throwable) {
        emptyList()
    }
}
