package com.darts.app

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCalloutPolicyTest {
    @Test
    fun `numbers are spoken as words`() {
        assertEquals("one", numberCallout(1))
        assertEquals("eleven", numberCallout(11))
        assertEquals("seventeen", numberCallout(17))
        assertEquals("twenty", numberCallout(20))
    }

    @Test
    fun `bull and unknown values fall back safely`() {
        assertEquals("bull", numberCallout(25))
        assertEquals("0", numberCallout(0))
        assertEquals("99", numberCallout(99))
    }
}
