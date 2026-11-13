package com.darts.app

import com.darts.core.DartHit
import com.darts.core.GameRules
import com.darts.core.MirrorSessionInfo
import com.darts.core.MirrorSnapshot
import com.darts.core.MirrorUiState
import com.darts.core.Player
import com.darts.core.Ring
import com.darts.core.ShieldsMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MirrorServerTest {

    private fun snapshot(): MirrorSnapshot {
        val players = listOf(Player(1, "Ana", 0), Player(2, "Ben", 1))
        val state = ShieldsMode.apply(
            ShieldsMode.newGame(players, GameRules()),
            DartHit(20, 3, Ring.TREBLE, x = 0.1f, y = -0.2f),
        ).state
        return MirrorSnapshot(
            seq = 12,
            session = MirrorSessionInfo(
                gameId = "game-1",
                gameType = "SHIELDS",
                gameTitle = "SHIELDS",
                code = "4821",
                hostName = "Test",
            ),
            state = state,
            ui = MirrorUiState(lastTarget = 20, flashTick = 2, visitHits = listOf("T20"), boardStatus = "READY"),
        )
    }

    @Test
    fun requiresApprovalBeforeServingSnapshot() {
        val server = MirrorServer(snapshot().session, preferredPort = 0)
        assertTrue(server.start())
        try {
            val original = snapshot()
            server.publish(original)
            val client = MirrorClient("127.0.0.1", server.port, "4821", "Test")

            assertEquals(MirrorFetchResult.Pending, client.fetch())
            assertEquals(0, server.clientCount)

            server.approve("127.0.0.1")
            val received = client.fetch()
            assertTrue(received is MirrorFetchResult.Success)
            val resolved = (received as MirrorFetchResult.Success).snapshot
            assertEquals(original.seq, resolved.seq)
            assertEquals(original.session, resolved.session)
            assertEquals(original.state, resolved.state)
            assertEquals(original.ui, resolved.ui)
            assertEquals(1, server.clientCount)
        } finally {
            server.stop()
        }
    }

    @Test
    fun reportsPendingDeviceToTheHost() {
        val server = MirrorServer(snapshot().session, preferredPort = 0)
        val latch = CountDownLatch(1)
        var pending: List<MirrorPendingClient> = emptyList()
        server.onPendingChanged = { clients ->
            if (clients.isNotEmpty()) {
                pending = clients
                latch.countDown()
            }
        }
        assertTrue(server.start())
        try {
            server.publish(snapshot())
            assertEquals(
                MirrorFetchResult.Pending,
                MirrorClient("127.0.0.1", server.port, "4821", "Pixel 8").fetch(),
            )
            assertTrue(latch.await(2, TimeUnit.SECONDS))
            assertEquals("127.0.0.1", pending.single().address)
            assertEquals("Pixel 8", pending.single().deviceName)
        } finally {
            server.stop()
        }
    }

    @Test
    fun deniesDeclinedDevice() {
        val server = MirrorServer(snapshot().session, preferredPort = 0)
        assertTrue(server.start())
        try {
            server.publish(snapshot())
            val client = MirrorClient("127.0.0.1", server.port, "4821", "Test")
            assertEquals(MirrorFetchResult.Pending, client.fetch())
            server.deny("127.0.0.1")
            assertEquals(MirrorFetchResult.Denied, client.fetch())
        } finally {
            server.stop()
        }
    }

    @Test
    fun rejectsWrongCode() {
        val server = MirrorServer(snapshot().session, preferredPort = 0)
        assertTrue(server.start())
        try {
            server.publish(snapshot())
            assertEquals(
                MirrorFetchResult.WrongCode,
                MirrorClient("127.0.0.1", server.port, "0000", "Test").fetch(),
            )
        } finally {
            server.stop()
        }
    }

    @Test
    fun parsesHostAndPortTargets() {
        assertEquals("192.168.1.42" to 8787, parseMirrorTarget("192.168.1.42"))
        assertEquals("192.168.1.42" to 9000, parseMirrorTarget("192.168.1.42:9000"))
        assertEquals("darty.local" to 8787, parseMirrorTarget("http://darty.local/"))
        assertEquals("darty.local" to 1234, parseMirrorTarget("http://darty.local:1234"))
        assertNull(parseMirrorTarget(""))
        assertNull(parseMirrorTarget("host:notaport"))
    }
}
