package com.jcraw.mud.client

import com.jcraw.mud.core.CharacterTemplate
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import kotlinx.datetime.Clock

/**
 * Immutable UI state for the game client.
 * Follows unidirectional data flow pattern.
 */
data class UiState(
    val screen: Screen = Screen.CharacterSelection,
    val selectedCharacter: CharacterTemplate? = null,
    val playerState: PlayerState? = null,
    val logEntries: List<LogEntry> = emptyList(),
    val inputHistory: List<String> = emptyList(),
    val historyIndex: Int = -1,
    val theme: Theme = Theme.DARK,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    sealed class Screen {
        object CharacterSelection : Screen()
        object MainGame : Screen()
    }

    enum class Theme {
        DARK, LIGHT
    }
}

/**
 * Represents a single entry in the game log.
 */
data class LogEntry(
    val text: String,
    val type: EntryType,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
) {
    enum class EntryType {
        NARRATIVE,      // System narrative text
        PLAYER_ACTION,  // Player's commands
        COMBAT,         // Combat-specific messages
        SYSTEM,         // System messages (errors, help)
        QUEST,          // Quest-related messages
        STATUS          // Status updates
    }

    companion object {
        fun fromGameEvent(event: GameEvent): LogEntry {
            return when (event) {
                is GameEvent.Narrative -> LogEntry(event.text, EntryType.NARRATIVE, event.timestamp)
                is GameEvent.PlayerAction -> LogEntry(event.text, EntryType.PLAYER_ACTION, event.timestamp)
                is GameEvent.Combat -> LogEntry(event.text, EntryType.COMBAT, event.timestamp)
                is GameEvent.System -> LogEntry(event.text, EntryType.SYSTEM, event.timestamp)
                is GameEvent.Quest -> LogEntry(event.text, EntryType.QUEST, event.timestamp)
                is GameEvent.StatusUpdate -> {
                    val parts = mutableListOf<String>()
                    event.hp?.let { parts.add("HP: $it/${event.maxHp ?: "?"}") }
                    event.location?.let { parts.add("Location: $it") }
                    LogEntry(parts.joinToString(" | "), EntryType.STATUS, event.timestamp)
                }
            }
        }
    }
}
