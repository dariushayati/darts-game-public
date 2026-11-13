package com.darts.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** User-facing theme choice. [SYSTEM] follows the device light/dark setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Process-wide observable for the selected theme mode. Written by the app-settings
 * store on launch and whenever the user changes it; read by [DartsTheme] to flip the
 * active palette. Lives here so the theme root can react without being threaded through
 * every screen.
 */
object ThemePreference {
    var mode: ThemeMode by mutableStateOf(ThemeMode.SYSTEM)
}

/** Full colour set for a theme. Neutrals flip between light/dark; accents stay shared. */
data class Palette(
    val Background: Color,
    val BackgroundDeep: Color,
    val Surface: Color,
    val SurfaceRaised: Color,
    val Pill: Color,
    val Border: Color,
    val Blue: Color,
    val BlueBright: Color,
    val Green: Color,
    val GreenText: Color,
    val Red: Color,
    val Magenta: Color,
    val Amber: Color,
    val Violet: Color,
    val Lava: Color,
    val LavaBright: Color,
    val Yellow: Color,
    val Mole: Color,
    val MoleDeep: Color,
    val Text: Color,
    val Muted: Color,
    val DartCyan: Color,
    // Semantic surfaces/tints for chrome that must flip between themes.
    val TintNavy: Color,
    val TintLava: Color,
    val TintLavaDeep: Color,
    val TintRingRun: Color,
    val TintInvaders: Color,
    val TintRobinHood: Color,
    val TintWhack: Color,
    val Panel: Color,
    val PanelDeep: Color,
    val ShardTop: Color,
    val ShardBottom: Color,
    val GlowNavy: Color,
    val ChipOff: Color,
    val Bar: Color,
    val Scrim: Color,
    val OverlayCard: Color,
    val OverlayPanel: Color,
    val NamePlate: Color,
)

private val DarkPalette = Palette(
    Background = Color(0xFF05070D),
    BackgroundDeep = Color(0xFF03050A),
    Surface = Color(0xFF0C1220),
    SurfaceRaised = Color(0xFF141C2E),
    Pill = Color(0xFF2C2E36),
    Border = Color(0xFF22304A),
    Blue = Color(0xFF1E88E5),
    BlueBright = Color(0xFF42A5F5),
    Green = Color(0xFF00E676),
    GreenText = Color(0xFF4EE1A0),
    Red = Color(0xFFE53935),
    Magenta = Color(0xFFC2185B),
    Amber = Color(0xFFFFB300),
    Violet = Color(0xFFB388FF),
    Lava = Color(0xFFFF6D00),
    LavaBright = Color(0xFFFFB300),
    Yellow = Color(0xFFFFEB00),
    Mole = Color(0xFFC9873F),
    MoleDeep = Color(0xFF241505),
    Text = Color(0xFFF4F6FA),
    Muted = Color(0xFF97A1B2),
    DartCyan = Color(0xFF29B6F6),
    TintNavy = Color(0xFF08152B),
    TintLava = Color(0xFF1A0600),
    TintLavaDeep = Color(0xFF2A0A00),
    TintRingRun = Color(0xFF04212A),
    TintInvaders = Color(0xFF100A22),
    TintRobinHood = Color(0xFF241A03),
    TintWhack = Color(0xFF241505),
    Panel = Color(0xFF0C0E13),
    PanelDeep = Color(0xFF050505),
    ShardTop = Color(0xFF1A1D24),
    ShardBottom = Color(0xFF0A0C11),
    GlowNavy = Color(0xFF0A1A3A),
    ChipOff = Color(0xFF0F1624),
    Bar = Color(0xFF0A1222),
    Scrim = Color(0xF203050A),
    OverlayCard = Color(0xE60A1018),
    OverlayPanel = Color(0xFF0A1018),
    NamePlate = Color(0xFF07090D),
)

private val LightPalette = Palette(
    Background = Color(0xFFEEF2F8),
    BackgroundDeep = Color(0xFFDFE6F1),
    Surface = Color(0xFFFFFFFF),
    SurfaceRaised = Color(0xFFF2F5FB),
    Pill = Color(0xFFE3E7EF),
    Border = Color(0xFFC4CEE0),
    Blue = Color(0xFF1565C0),
    BlueBright = Color(0xFF1976D2),
    Green = Color(0xFF00A152),
    GreenText = Color(0xFF086B3A),
    Red = Color(0xFFC62828),
    Magenta = Color(0xFFAD1457),
    Amber = Color(0xFFEF8F00),
    Violet = Color(0xFF6A3FB5),
    Lava = Color(0xFFE65100),
    LavaBright = Color(0xFFEF8F00),
    Yellow = Color(0xFFE0A400),
    Mole = Color(0xFFA96A24),
    MoleDeep = Color(0xFF3A2409),
    Text = Color(0xFF0B1018),
    Muted = Color(0xFF55637A),
    DartCyan = Color(0xFF0288D1),
    TintNavy = Color(0xFFDCE6F5),
    TintLava = Color(0xFFF6E1D6),
    TintLavaDeep = Color(0xFFEFD2C2),
    TintRingRun = Color(0xFFD6EEF3),
    TintInvaders = Color(0xFFE4DFF5),
    TintRobinHood = Color(0xFFF6ECD3),
    TintWhack = Color(0xFFF3E7D6),
    Panel = Color(0xFFF2F5FB),
    PanelDeep = Color(0xFFFFFFFF),
    ShardTop = Color(0xFFE8EDF5),
    ShardBottom = Color(0xFFDCE4F0),
    GlowNavy = Color(0xFF9FB6D8),
    ChipOff = Color(0xFFE7ECF4),
    Bar = Color(0xFFFFFFFF),
    Scrim = Color(0xF2E9EEF6),
    OverlayCard = Color(0xF2FFFFFF),
    OverlayPanel = Color(0xFFF2F5FB),
    NamePlate = Color(0xFFE7ECF4),
)

object DartsPalette {
    // Backed by snapshot state so reads inside composables (and [DrawScope]) subscribe
    // and repaint when the theme flips, without resetting the composing subtree's state.
    private val activeState = mutableStateOf<Palette>(DarkPalette)

    /** Called by [DartsTheme] before content composes for the current frame. */
    var active: Palette
        get() = activeState.value
        set(value) {
            activeState.value = value
        }

    private val p: Palette get() = activeState.value

    /**
     * Returns the ink that has the strongest contrast against [background].
     * Keeps button labels readable as the palette swaps between light and dark.
     */
    fun onColor(background: Color): Color {
        val backgroundLuminance = background.luminance()
        val light = contrastRatio(backgroundLuminance, Color.White.luminance())
        val dark = contrastRatio(backgroundLuminance, Color(0xFF07090D).luminance())
        return if (dark >= light) Color(0xFF07090D) else Color.White
    }

    private fun contrastRatio(a: Float, b: Float): Float =
        (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)

    val Background: Color get() = p.Background
    val BackgroundDeep: Color get() = p.BackgroundDeep
    val Surface: Color get() = p.Surface
    val SurfaceRaised: Color get() = p.SurfaceRaised
    val Pill: Color get() = p.Pill
    val Border: Color get() = p.Border
    val Blue: Color get() = p.Blue
    val BlueBright: Color get() = p.BlueBright
    val Green: Color get() = p.Green
    val GreenText: Color get() = p.GreenText
    val Red: Color get() = p.Red
    val Magenta: Color get() = p.Magenta
    val Amber: Color get() = p.Amber
    val Violet: Color get() = p.Violet
    val Lava: Color get() = p.Lava
    val LavaBright: Color get() = p.LavaBright
    val Yellow: Color get() = p.Yellow
    val Mole: Color get() = p.Mole
    val MoleDeep: Color get() = p.MoleDeep
    val Text: Color get() = p.Text
    val Muted: Color get() = p.Muted
    val DartCyan: Color get() = p.DartCyan
    val TintNavy: Color get() = p.TintNavy
    val TintLava: Color get() = p.TintLava
    val TintLavaDeep: Color get() = p.TintLavaDeep
    val TintRingRun: Color get() = p.TintRingRun
    val TintInvaders: Color get() = p.TintInvaders
    val TintRobinHood: Color get() = p.TintRobinHood
    val TintWhack: Color get() = p.TintWhack
    val Panel: Color get() = p.Panel
    val PanelDeep: Color get() = p.PanelDeep
    val ShardTop: Color get() = p.ShardTop
    val ShardBottom: Color get() = p.ShardBottom
    val GlowNavy: Color get() = p.GlowNavy
    val ChipOff: Color get() = p.ChipOff
    val Bar: Color get() = p.Bar
    val Scrim: Color get() = p.Scrim
    val OverlayCard: Color get() = p.OverlayCard
    val OverlayPanel: Color get() = p.OverlayPanel
    val NamePlate: Color get() = p.NamePlate
}

val PlayerColors: List<Color> = listOf(
    Color(0xFF29B6F6),
    Color(0xFFFFB300),
    Color(0xFFAB47BC),
    Color(0xFF66BB6A),
    Color(0xFFEF5350),
    Color(0xFFFF80AB),
)

private fun buildScheme(palette: Palette, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = palette.Blue,
            onPrimary = Color.White,
            secondary = palette.Green,
            background = palette.Background,
            onBackground = palette.Text,
            surface = palette.Surface,
            onSurface = palette.Text,
            surfaceVariant = palette.SurfaceRaised,
            onSurfaceVariant = palette.Muted,
        )
    } else {
        lightColorScheme(
            primary = palette.Blue,
            onPrimary = Color.White,
            secondary = palette.Green,
            background = palette.Background,
            onBackground = palette.Text,
            surface = palette.Surface,
            onSurface = palette.Text,
            surfaceVariant = palette.SurfaceRaised,
            onSurfaceVariant = palette.Muted,
        )
    }

@Composable
fun DartsTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemePreference.mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val palette = if (dark) DarkPalette else LightPalette
    DartsPalette.active = palette
    MaterialTheme(colorScheme = buildScheme(palette, dark), content = content)
}

fun playerColor(index: Int): Color = PlayerColors[Math.floorMod(index, PlayerColors.size)]
