package com.darts.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class BottomTab { GAMES, TRAINING, STATS, AUTODARTS, SETTINGS }

@Composable
fun BottomNav(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = DartsPalette.BlueBright,
        selectedTextColor = DartsPalette.Text,
        indicatorColor = DartsPalette.Blue.copy(alpha = 0.20f),
        unselectedIconColor = DartsPalette.Muted,
        unselectedTextColor = DartsPalette.Muted,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DartsPalette.Surface),
    ) {
        HorizontalDivider(color = DartsPalette.Border, thickness = 1.dp)
        NavigationBar(
            containerColor = DartsPalette.Surface,
            windowInsets = WindowInsets.navigationBars.only(WindowInsetsSides.Bottom),
        ) {
            NavigationBarItem(
                selected = selected == BottomTab.GAMES,
                onClick = { onSelect(BottomTab.GAMES) },
                icon = { Icon(Icons.Default.Home, contentDescription = "Games") },
                label = {
                    Text(
                        text = "GAMES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                },
                colors = colors,
            )
            NavigationBarItem(
                selected = selected == BottomTab.TRAINING,
                onClick = { onSelect(BottomTab.TRAINING) },
                icon = { TrainingGlyph() },
                label = {
                    Text(
                        text = "TRAINING",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                },
                colors = colors,
            )
            NavigationBarItem(
                selected = selected == BottomTab.STATS,
                onClick = { onSelect(BottomTab.STATS) },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Stats") },
                label = {
                    Text(
                        text = "STATS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                },
                colors = colors,
            )
            NavigationBarItem(
                selected = selected == BottomTab.AUTODARTS,
                onClick = { onSelect(BottomTab.AUTODARTS) },
                icon = { TargetGlyph() },
                label = {
                    Text(
                        text = "AUTODARTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                },
                colors = colors,
            )
            NavigationBarItem(
                selected = selected == BottomTab.SETTINGS,
                onClick = { onSelect(BottomTab.SETTINGS) },
                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                label = {
                    Text(
                        text = "SETTINGS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    )
                },
                colors = colors,
            )
        }
    }
}

@Composable
fun BottomNavRail(selected: BottomTab, onSelect: (BottomTab) -> Unit) {
    val colors = NavigationRailItemDefaults.colors(
        selectedIconColor = DartsPalette.BlueBright,
        selectedTextColor = DartsPalette.Text,
        indicatorColor = DartsPalette.Blue.copy(alpha = 0.20f),
        unselectedIconColor = DartsPalette.Muted,
        unselectedTextColor = DartsPalette.Muted,
    )
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = DartsPalette.Surface,
        windowInsets = WindowInsets(0, 0, 0, 0),
    ) {
        NavigationRailItem(
            selected = selected == BottomTab.GAMES,
            onClick = { onSelect(BottomTab.GAMES) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Games") },
            label = {
                Text(
                    text = "GAMES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            },
            colors = colors,
        )
        NavigationRailItem(
            selected = selected == BottomTab.TRAINING,
            onClick = { onSelect(BottomTab.TRAINING) },
            icon = { TrainingGlyph() },
            label = {
                Text(
                    text = "TRAINING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            },
            colors = colors,
        )
        NavigationRailItem(
            selected = selected == BottomTab.STATS,
            onClick = { onSelect(BottomTab.STATS) },
            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Stats") },
            label = {
                Text(
                    text = "STATS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            },
            colors = colors,
        )
        NavigationRailItem(
            selected = selected == BottomTab.AUTODARTS,
            onClick = { onSelect(BottomTab.AUTODARTS) },
            icon = { TargetGlyph() },
            label = {
                Text(
                    text = "AUTODARTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            },
            colors = colors,
        )
        NavigationRailItem(
            selected = selected == BottomTab.SETTINGS,
            onClick = { onSelect(BottomTab.SETTINGS) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = {
                Text(
                    text = "SETTINGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            },
            colors = colors,
        )
    }
}

@Composable
private fun TargetGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(color, radius = size.minDimension / 2f - stroke / 2f, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(color, radius = size.minDimension / 4f, style = Stroke(stroke, cap = StrokeCap.Round))
        drawCircle(color, radius = size.minDimension * 0.10f)
    }
}

@Composable
private fun TrainingGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.20f, size.height * 0.80f),
            end = Offset(size.width * 0.80f, size.height * 0.20f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val tip = Path().apply {
            moveTo(size.width * 0.80f, size.height * 0.20f)
            lineTo(size.width * 0.64f, size.height * 0.24f)
            lineTo(size.width * 0.76f, size.height * 0.36f)
            close()
        }
        drawPath(tip, color)
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.54f),
            end = Offset(size.width * 0.40f, size.height * 0.32f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
