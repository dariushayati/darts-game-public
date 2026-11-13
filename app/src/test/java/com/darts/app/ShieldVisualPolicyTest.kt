package com.darts.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ShieldVisualPolicyTest {
    @Test
    fun `only the selected shield pops on a hit`() {
        assertEquals(1.22f, ShieldVisualPolicy.flashScale(20, lastTarget = 20, flash = 1f), 0.001f)
        assertEquals(1f, ShieldVisualPolicy.flashScale(19, lastTarget = 20, flash = 1f), 0.001f)
    }

    @Test
    fun `no pop when there is no selected target`() {
        assertEquals(1f, ShieldVisualPolicy.flashScale(20, lastTarget = null, flash = 1f), 0.001f)
        assertEquals(1f, ShieldVisualPolicy.flashScale(20, lastTarget = 20, flash = 0f), 0.001f)
    }

    @Test
    fun `shield fills a third per shield and caps at full`() {
        assertEquals(0f, ShieldVisualPolicy.fillFraction(0), 0.001f)
        assertEquals(1f / 3f, ShieldVisualPolicy.fillFraction(1), 0.001f)
        assertEquals(2f / 3f, ShieldVisualPolicy.fillFraction(2), 0.001f)
        assertEquals(1f, ShieldVisualPolicy.fillFraction(3), 0.001f)
        assertEquals(1f, ShieldVisualPolicy.fillFraction(5), 0.001f)
    }

    @Test
    fun `animated fill fraction clamps to full`() {
        assertEquals(0.5f, ShieldVisualPolicy.fillFraction(1.5f), 0.001f)
        assertEquals(1f, ShieldVisualPolicy.fillFraction(9f), 0.001f)
    }
}
