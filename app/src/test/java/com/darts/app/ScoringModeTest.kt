package com.darts.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoringModeTest {

    @Test
    fun manualHasNoInputConfig() {
        assertNull(scoringConfigFor(ScoringMode.MANUAL, "http://board:3180"))
    }

    @Test
    fun autodartsKeepsTheUrl() {
        val config = scoringConfigFor(ScoringMode.AUTODARTS, "http://board:3180")
        assertEquals(ScoringMode.AUTODARTS, config?.mode)
        assertEquals("http://board:3180", config?.url)
    }

    @Test
    fun cameraUsesTheCameraMode() {
        val config = scoringConfigFor(ScoringMode.CAMERA, "http://board:3180")
        assertEquals(ScoringMode.CAMERA, config?.mode)
    }
}
