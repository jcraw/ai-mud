@file:Suppress("MagicNumber")

package com.jcraw.mud.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraw.mud.client.LogEntry

private val CombatLogColor = Color(0xFFCF6679)
private val QuestLogColor = Color(0xFFD4AF37)

@Composable
fun GameLogWindow(
    logEntries: List<LogEntry>,
    colors: ColorScheme,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = colors.background,
        tonalElevation = 1.dp
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(logEntries) { entry ->
                    LogEntryText(entry = entry, colors = colors)
                }
            }
        }
    }
}

@Composable
fun LogEntryText(entry: LogEntry, colors: ColorScheme) {
    val style = logEntryStyle(entry.type, colors)
    Text(
        text = when (entry.type) {
            LogEntry.EntryType.PLAYER_ACTION -> "> ${entry.text}"
            else -> entry.text
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = style.first,
            fontStyle = style.second,
            fontWeight = style.third,
            lineHeight = 24.sp
        )
    )
}

private fun logEntryStyle(
    type: LogEntry.EntryType,
    colors: ColorScheme
): Triple<Color, FontStyle, FontWeight> = when (type) {
    LogEntry.EntryType.NARRATIVE -> Triple(colors.onBackground, FontStyle.Normal, FontWeight.Normal)
    LogEntry.EntryType.PLAYER_ACTION -> Triple(colors.primary, FontStyle.Italic, FontWeight.Bold)
    LogEntry.EntryType.COMBAT -> Triple(CombatLogColor, FontStyle.Normal, FontWeight.Bold)
    LogEntry.EntryType.SYSTEM -> Triple(colors.secondary, FontStyle.Italic, FontWeight.Normal)
    LogEntry.EntryType.QUEST -> Triple(QuestLogColor, FontStyle.Normal, FontWeight.Bold)
    LogEntry.EntryType.STATUS -> Triple(colors.tertiary, FontStyle.Normal, FontWeight.Normal)
}
