package com.darts.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeContrastTest {
    private val darkInk = Color(0xFF07090D)

    @Test
    fun darkThemeBlueUsesDarkInk() {
        assertEquals(darkInk, DartsPalette.onColor(Color(0xFF1E88E5)))
    }

    @Test
    fun lightThemeBlueUsesWhiteInk() {
        assertEquals(Color.White, DartsPalette.onColor(Color(0xFF1565C0)))
    }

    @Test
    fun darkThemeRedUsesDarkInk() {
        assertEquals(darkInk, DartsPalette.onColor(Color(0xFFE53935)))
    }

    @Test
    fun brightAmberUsesDarkInk() {
        assertEquals(darkInk, DartsPalette.onColor(Color(0xFFFFB300)))
    }
}
