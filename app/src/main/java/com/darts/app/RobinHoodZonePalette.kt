package com.darts.app

import androidx.compose.ui.graphics.Color

/** Colours used to tint each Robin Hood scoring band on the board. */
data class BoardZonePalette(
    val double: Color,
    val largeSingle: Color,
    val treble: Color,
    val smallSingle: Color,
    val outerBull: Color,
    val innerBull: Color,
)

val RobinHoodZonePalette = BoardZonePalette(
    double = Color(0xFF29B6F6),
    largeSingle = Color(0xFF66BB6A),
    treble = Color(0xFFFFA000),
    smallSingle = Color(0xFFAB47BC),
    outerBull = Color(0xFFFFEB3B),
    innerBull = Color(0xFFEF5350),
)
