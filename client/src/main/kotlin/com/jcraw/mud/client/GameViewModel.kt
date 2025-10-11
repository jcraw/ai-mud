package com.jcraw.mud.client

import com.jcraw.mud.core.CharacterTemplate
import com.jcraw.mud.core.GameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the game client UI.
 * Manages UI state and coordinates with the game client.
 * Follows unidirectional data flow - UI observes state, sends events via methods.
 */
class GameViewModel(
    private var gameClient: GameClient? = null,
    private val apiKey: String? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        // Subscribe to game events if client is connected
        gameClient?.let { client ->
            scope.launch {
                client.observeEvents().collect { event ->
                    handleGameEvent(event)
                }
            }
        }
    }

    /**
     * Select a character template and start the game.
     */
    fun selectCharacter(template: CharacterTemplate) {
        _uiState.update { it.copy(selectedCharacter = template, isLoading = true) }

        // Create real game client with selected character
        try {
            gameClient = EngineGameClient(
                initialTemplate = template,
                apiKey = apiKey,
                dungeonTheme = DungeonTheme.CRYPT, // Could be made selectable
                roomCount = 10
            )

            // Subscribe to new client's events
            scope.launch {
                gameClient?.observeEvents()?.collect { event ->
                    handleGameEvent(event)
                }
            }

            _uiState.update {
                it.copy(
                    screen = UiState.Screen.MainGame,
                    isLoading = false,
                    playerState = gameClient?.getCurrentState()
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = "Failed to start game: ${e.message}"
                )
            }
        }
    }

    /**
     * Send player input to the game engine.
     */
    fun sendInput(input: String) {
        if (input.isBlank()) return

        // Add to history
        val updatedHistory = _uiState.value.inputHistory + input
        _uiState.update { it.copy(inputHistory = updatedHistory, historyIndex = -1) }

        // Echo player action to log
        val playerActionEntry = LogEntry(input, LogEntry.EntryType.PLAYER_ACTION)
        _uiState.update { it.copy(logEntries = it.logEntries + playerActionEntry) }

        // Send to game engine
        scope.launch {
            try {
                gameClient?.sendInput(input)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Error sending input: ${e.message}") }
            }
        }
    }

    /**
     * Navigate input history (up/down arrows).
     * Direction > 0 = up (older), Direction < 0 = down (newer)
     */
    fun navigateHistory(direction: Int): String? {
        val history = _uiState.value.inputHistory
        if (history.isEmpty()) return null

        val currentIndex = _uiState.value.historyIndex
        val newIndex = when {
            // First time navigating up - start from the end
            direction > 0 && currentIndex == -1 -> history.size - 1
            // Navigate to older items
            direction > 0 && currentIndex > 0 -> currentIndex - 1
            // Navigate to newer items
            direction < 0 && currentIndex < history.size - 1 -> currentIndex + 1
            // Stay at current
            else -> currentIndex
        }

        _uiState.update { it.copy(historyIndex = newIndex) }
        return if (newIndex >= 0) history[newIndex] else null
    }

    /**
     * Toggle between dark and light theme.
     */
    fun toggleTheme() {
        _uiState.update {
            val newTheme = if (it.theme == UiState.Theme.DARK) UiState.Theme.LIGHT else UiState.Theme.DARK
            it.copy(theme = newTheme)
        }
    }

    /**
     * Get log content as text for clipboard copy.
     */
    fun getLogAsText(): String {
        return _uiState.value.logEntries.joinToString("\n") { entry ->
            "[${entry.type}] ${entry.text}"
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Handle incoming game events and update UI state.
     */
    private fun handleGameEvent(event: GameEvent) {
        val logEntry = LogEntry.fromGameEvent(event)
        _uiState.update { it.copy(logEntries = it.logEntries + logEntry) }

        // Update player state if status update
        if (event is GameEvent.StatusUpdate) {
            gameClient?.getCurrentState()?.let { playerState ->
                _uiState.update { it.copy(playerState = playerState) }
            }
        }
    }

    /**
     * Cleanup resources.
     */
    fun close() {
        scope.launch {
            gameClient?.close()
        }
    }
}
