package com.darts.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darts.core.MatchRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local match history. Rows open the match detail; swiping a row left asks to
 * delete it (the swipe itself never deletes — the dialog's DELETE does).
 */
@Composable
fun MatchHistoryScreen(
    matches: List<MatchRecord>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<MatchRecord?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        DartsPalette.BackgroundDeep,
                        DartsPalette.Background,
                        DartsPalette.TintNavy,
                    ),
                ),
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = LayoutPolicy.MENU_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "STATS",
                color = DartsPalette.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Text(
                text = "MATCH HISTORY",
                color = DartsPalette.Text,
                fontSize = 34.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text(
                text = if (matches.isEmpty()) {
                    "Finished games are saved here on this device."
                } else {
                    "${matches.size} ${if (matches.size == 1) "match" else "matches"} · stored on this device"
                },
                color = DartsPalette.Muted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(4.dp))

            if (matches.isEmpty()) {
                Text(
                    text = "No matches yet — play a game and it will appear here.",
                    color = DartsPalette.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DartsPalette.Surface)
                        .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(matches, key = { it.id }) { record ->
                        SwipeableMatchRow(
                            record = record,
                            onClick = { onOpen(record.id) },
                            onRequestDelete = { pendingDelete = record },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = DartsPalette.Surface,
            titleContentColor = DartsPalette.Text,
            textContentColor = DartsPalette.Muted,
            title = {
                Text("DELETE MATCH?", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
            },
            text = {
                Text("${record.gameTitle} · ${record.players.joinToString(" · ") { it.name }}")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(record.id)
                        pendingDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DartsPalette.Red,
                    ),
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDelete = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = DartsPalette.Muted,
                    ),
                ) {
                    Text("CANCEL")
                }
            },
        )
    }
}

@Composable
private fun SwipeableMatchRow(
    record: MatchRecord,
    onClick: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onRequestDelete()
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(DartsPalette.Red.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        text = "DELETE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                    )
                }
            }
        },
    ) {
        MatchRow(record = record, onClick = onClick)
    }
}

@Composable
private fun MatchRow(record: MatchRecord, onClick: () -> Unit) {
    val accent = record.accent()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DartsPalette.Surface)
            .border(1.dp, DartsPalette.Border, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(38.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = record.gameTitle,
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = record.finishedAt.shortDate(),
                    color = DartsPalette.Muted,
                    fontSize = 10.sp,
                )
            }
            Text(
                text = record.players.joinToString(" · ") { it.name },
                color = DartsPalette.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = record.outcomeLine(),
                color = DartsPalette.Muted,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        Text(
            text = "›",
            color = DartsPalette.BlueBright,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

internal fun MatchRecord.accent(): Color =
    GameType.entries.firstOrNull { it.name == game }?.accent ?: DartsPalette.Blue

internal fun MatchRecord.outcomeLine(): String {
    val winner = winnerName()
    return when {
        winner != null && endReason != null -> "$winner won · $endReason"
        winner != null -> "$winner won"
        endReason != null -> "No winner · $endReason"
        else -> "No winner"
    }
}

internal fun Long.shortDate(): String =
    SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(this))

internal fun Long.longDate(): String =
    SimpleDateFormat("d MMMM yyyy · HH:mm", Locale.getDefault()).format(Date(this))
