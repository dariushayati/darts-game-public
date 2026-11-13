package com.darts.app

import androidx.compose.ui.graphics.Color

/** Add a new entry here and a matching branch in [DartsApp]. */
enum class GameType(
    val title: String,
    val tagline: String,
    private val accentRole: AccentRole,
    val isTraining: Boolean = false,
    val trainingMeta: String? = null,
) {
    SHIELDS(
        title = "SHIELDS",
        tagline = "Claim sectors. Three shields lock them in. Opponents knock them off.",
        accentRole = AccentRole.GREEN,
    ),
    INVADERS(
        title = "INVADERS",
        tagline = "Three aliens on every number. Doubles and trebles hit harder.",
        accentRole = AccentRole.VIOLET,
    ),
    LAVA(
        title = "LAVA",
        tagline = "The board is lava. Throw darts and avoid the burn.",
        accentRole = AccentRole.LAVA,
    ),
    RING_RUN(
        title = "RING RUN",
        tagline = "Four rounds. Shrink the ring. Finish on the bull.",
        accentRole = AccentRole.YELLOW,
    ),
    ROBIN_HOOD(
        title = "ROBIN HOOD",
        tagline = "Aim for the bull. Every ring closer to the centre scores more.",
        accentRole = AccentRole.AMBER,
    ),
    WHACK_A_MOLE(
        title = "WHACK A MOLE",
        tagline = "A random list of numbers. Whack them in order — first to clear the list wins.",
        accentRole = AccentRole.MOLE,
    ),
    CRICKET(
        title = "CRICKET",
        tagline = "Close 20 to 15 and the bull with three marks. Score on targets your rivals leave open.",
        accentRole = AccentRole.CRICKET,
    ),
    X01(
        title = "X01",
        tagline = "Race to zero. 501, 301 or 121 with custom in/out and leg rules.",
        accentRole = AccentRole.X01,
    ),
    GUNSLINGER(
        title = "GUNSLINGER",
        tagline = "Wait for the beeps, then hit the random number with all three darts before the clock drains your score.",
        accentRole = AccentRole.RED,
    ),
    AROUND_THE_CLOCK(
        title = "AROUND THE CLOCK",
        tagline = "Hit 1 to 20 in order, then finish on the bull.",
        accentRole = AccentRole.TRAINING,
        isTraining = true,
        trainingMeta = "1–6 PLAYERS · 3 DARTS PER TURN",
    ),
    CHECKOUT(
        title = "CHECKOUT",
        tagline = "Hit common 2- or 3-dart finishes, one checkout at a time.",
        accentRole = AccentRole.CHECKOUT,
        isTraining = true,
        trainingMeta = "SOLO · EASY / HARD · 10 CHECKOUTS",
    ),
    ;

    /** Resolved live from the active theme so it flips with light/dark mode. */
    val accent: Color
        get() = when (accentRole) {
            AccentRole.GREEN -> DartsPalette.Green
            AccentRole.VIOLET -> DartsPalette.Violet
            AccentRole.LAVA -> DartsPalette.Lava
            AccentRole.YELLOW -> DartsPalette.Yellow
            AccentRole.AMBER -> DartsPalette.Amber
            AccentRole.MOLE -> DartsPalette.Mole
            AccentRole.CRICKET -> DartsPalette.DartCyan
            AccentRole.X01 -> DartsPalette.Blue
            AccentRole.RED -> DartsPalette.Red
            AccentRole.TRAINING -> DartsPalette.DartCyan
            AccentRole.CHECKOUT -> DartsPalette.Amber
        }

    private enum class AccentRole {
        GREEN,
        VIOLET,
        LAVA,
        YELLOW,
        AMBER,
        MOLE,
        CRICKET,
        X01,
        RED,
        TRAINING,
        CHECKOUT,
    }
}
