package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorProtocolTest {

    private fun snapshot(): MirrorSnapshot {
        val players = listOf(Player(1, "Ana", 0), Player(2, "Ben", 1))
        val state = ShieldsMode.apply(
            ShieldsMode.newGame(players, GameRules()),
            DartHit(20, 3, Ring.TREBLE, x = 0.1f, y = -0.2f),
        ).state
        return MirrorSnapshot(
            seq = 7,
            session = MirrorSessionInfo(
                gameId = "game-1",
                gameType = "SHIELDS",
                gameTitle = "SHIELDS",
                code = "4821",
                hostName = "Pixel",
            ),
            state = state,
            ui = MirrorUiState(
                lastTarget = 20,
                flashTick = 3,
                visitHits = listOf("T20"),
                visitTargets = listOf(DartHit(20, 3, Ring.TREBLE)),
                crackTarget = 20,
                crackColorIndex = 1,
                crackCount = 2,
                crackTick = 1,
                boardStatus = "READY",
            ),
        )
    }

    @Test
    fun roundTripPreservesStateAndUi() {
        val original = snapshot()
        val decoded = MirrorCodec.decode(MirrorCodec.encode(original))

        requireNotNull(decoded)
        assertEquals(MIRROR_PROTOCOL_VERSION, decoded.protocolVersion)
        assertEquals(original.seq, decoded.seq)
        assertEquals(original.session, decoded.session)
        assertEquals(original.state, decoded.state)
        assertEquals(original.ui, decoded.ui)
        assertEquals(3, requireNotNull(decoded.state).sectors.getValue(20).shields)
        assertEquals(3, decoded.ui.visitTargets.single().multiplier)
    }

    @Test
    fun roundTripPreservesGenericGameView() {
        val original = snapshot().copy(
            state = null,
            view = MirrorView(
                title = "INVADERS",
                modeLabel = "TURN 2",
                statusLabel = "2 DARTS LEFT",
                players = listOf(
                    MirrorPlayerView(1, "Ana", 0, "24", "8 kills", active = true, leading = true),
                    MirrorPlayerView(2, "Ben", 1, "12", "4 kills"),
                ),
                currentPlayerId = 1,
                lastHit = DartHit(20, 3, Ring.TREBLE),
                recentHits = listOf(DartHit(20, 3, Ring.TREBLE)),
                highlightNumbers = listOf(20),
                targetLabels = listOf("20", "T19"),
            ),
        )
        val decoded = MirrorCodec.decode(MirrorCodec.encode(original))
        requireNotNull(decoded)
        assertEquals(original.view, decoded.view)
        assertNull(decoded.state)
    }

    @Test
    fun rejectsOtherProtocolVersions() {
        val encoded = MirrorCodec.encode(snapshot().copy(protocolVersion = MIRROR_PROTOCOL_VERSION + 1))
        assertNull(MirrorCodec.decode(encoded))
    }

    @Test
    fun rejectsMalformedAndBlankPayloads() {
        assertNull(MirrorCodec.decode(null))
        assertNull(MirrorCodec.decode(""))
        assertNull(MirrorCodec.decode("{not json"))
        assertNull(MirrorCodec.decode("{}"))
        assertTrue(MirrorCodec.encode(snapshot()).contains("\"SHIELDS\""))
    }
}
