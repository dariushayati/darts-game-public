package com.darts.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchRecordTest {

    private val record = MatchRecord(
        id = "abc",
        game = "SHIELDS",
        gameTitle = "SHIELDS",
        finishedAt = 1_700_000_000_000,
        winnerId = 1,
        endReason = "Board complete",
        players = listOf(
            MatchPlayerRecord(0, "Ann", 0, 5, 12, "3 locked"),
            MatchPlayerRecord(1, "Bob", 1, 7, 15, "5 locked"),
        ),
    )

    @Test
    fun `history round-trips through json`() {
        val decoded = MatchHistoryCodec.decode(MatchHistoryCodec.encode(listOf(record)))
        assertEquals(listOf(record), decoded)
    }

    @Test
    fun `winner resolves to the player name`() {
        assertEquals("Bob", record.winnerName())
        assertNull(record.copy(winnerId = null).winnerName())
    }

    @Test
    fun `missing and malformed history decodes to an empty list`() {
        assertTrue(MatchHistoryCodec.decode(null).isEmpty())
        assertTrue(MatchHistoryCodec.decode("").isEmpty())
        assertTrue(MatchHistoryCodec.decode("not json").isEmpty())
        assertTrue(MatchHistoryCodec.decode("{\"unexpected\":true}").isEmpty())
    }
}
