package com.darts.core

import com.google.gson.Gson

const val MIRROR_PROTOCOL_VERSION = 1

data class MirrorSessionInfo(
    val gameId: String,
    val gameType: String,
    val gameTitle: String,
    val code: String,
    val hostName: String = "",
)

data class MirrorUiState(
    val lastTarget: Int? = null,
    val flashTick: Int = 0,
    val visitHits: List<String> = emptyList(),
    val visitTargets: List<DartHit> = emptyList(),
    val crackTarget: Int? = null,
    val crackColorIndex: Int? = null,
    val crackCount: Int = 0,
    val crackTick: Int = 0,
    val boardStatus: String = "OFFLINE",
)

data class MirrorPlayerView(
    val id: Int,
    val name: String,
    val colorIndex: Int,
    val score: String,
    val detail: String = "",
    val active: Boolean = false,
    val leading: Boolean = false,
)

data class MirrorView(
    val gameType: String = "",
    val title: String,
    val modeLabel: String = "",
    val statusLabel: String = "",
    val players: List<MirrorPlayerView> = emptyList(),
    val currentPlayerId: Int? = null,
    val lastHit: DartHit? = null,
    val recentHits: List<DartHit> = emptyList(),
    val flashTick: Int = 0,
    val highlightNumbers: List<Int> = emptyList(),
    val secondaryHighlightNumbers: List<Int> = emptyList(),
    val dangerNumbers: List<Int> = emptyList(),
    val targetLabels: List<String> = emptyList(),
    val alienCounts: Map<Int, Int> = emptyMap(),
    val alienKills: List<AlienKill> = emptyList(),
    val lastBurned: List<Int> = emptyList(),
    val burnTick: Int = 0,
    val ringRoundIndex: Int = 0,
    val lastPoints: Int? = null,
    val moleTarget: MoleTarget? = null,
    val whackTick: Int = 0,
    val checkoutTarget: CheckoutDart? = null,
    val cricketMarks: Map<Int, Map<Int, Int>> = emptyMap(),
    val finished: Boolean = false,
    val winnerName: String? = null,
    val endReason: String? = null,
)

data class MirrorSnapshot(
    val protocolVersion: Int = MIRROR_PROTOCOL_VERSION,
    val seq: Long = 0,
    val session: MirrorSessionInfo,
    val state: GameState? = null,
    val ui: MirrorUiState = MirrorUiState(),
    val view: MirrorView? = null,
)

object MirrorCodec {
    private val gson = Gson()

    fun encode(snapshot: MirrorSnapshot): String = gson.toJson(snapshot)

    fun decode(json: String?): MirrorSnapshot? = try {
        if (json.isNullOrBlank()) {
            null
        } else {
            gson.fromJson(json, MirrorSnapshot::class.java)
                ?.takeIf { it.protocolVersion == MIRROR_PROTOCOL_VERSION && it.session.gameId.isNotBlank() }
        }
    } catch (_: Throwable) {
        null
    }
}
