package com.jcraw.mud.client

import com.jcraw.mud.core.CharacterTemplate
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class GameViewModelTest {

    @Test
    fun `initial state is character selection screen`() = runTest {
        val viewModel = GameViewModel()
        val state = viewModel.uiState.first()

        assertTrue(state.screen is UiState.Screen.CharacterSelection)
        assertNull(state.selectedCharacter)
        assertTrue(state.logEntries.isEmpty())
    }

    @Test
    fun `selecting character transitions to main game screen`() = runTest {
        val viewModel = GameViewModel()

        viewModel.selectCharacter(CharacterTemplate.WARRIOR)

        val state = viewModel.uiState.first()
        assertTrue(state.screen is UiState.Screen.MainGame)
        assertEquals(CharacterTemplate.WARRIOR, state.selectedCharacter)
    }

    @Test
    fun `sending input adds to history and log`() = runTest {
        val viewModel = GameViewModel()

        viewModel.sendInput("look around")

        val state = viewModel.uiState.first()
        assertTrue(state.inputHistory.contains("look around"))
        assertTrue(state.logEntries.any { it.text == "look around" && it.type == LogEntry.EntryType.PLAYER_ACTION })
    }

    @Test
    fun `theme toggle switches between dark and light`() = runTest {
        val viewModel = GameViewModel()
        val initialState = viewModel.uiState.first()
        val initialTheme = initialState.theme

        viewModel.toggleTheme()

        val newState = viewModel.uiState.first()
        assertNotEquals(initialTheme, newState.theme)
    }

    @Test
    fun `log entry conversion from game events`() {
        val narrativeEvent = GameEvent.Narrative("Test narrative")
        val narrativeEntry = LogEntry.fromGameEvent(narrativeEvent)
        assertEquals("Test narrative", narrativeEntry.text)
        assertEquals(LogEntry.EntryType.NARRATIVE, narrativeEntry.type)

        val combatEvent = GameEvent.Combat("Combat text", damage = 10)
        val combatEntry = LogEntry.fromGameEvent(combatEvent)
        assertEquals("Combat text", combatEntry.text)
        assertEquals(LogEntry.EntryType.COMBAT, combatEntry.type)

        val questEvent = GameEvent.Quest("Quest text", questId = "q1")
        val questEntry = LogEntry.fromGameEvent(questEvent)
        assertEquals("Quest text", questEntry.text)
        assertEquals(LogEntry.EntryType.QUEST, questEntry.type)
    }

    @Test
    fun `history navigation works correctly`() = runTest {
        val viewModel = GameViewModel()

        viewModel.sendInput("first")
        viewModel.sendInput("second")
        viewModel.sendInput("third")

        // Navigate up (should get "third")
        var result = viewModel.navigateHistory(1)
        assertEquals("third", result)

        // Navigate up again (should get "second")
        result = viewModel.navigateHistory(1)
        assertEquals("second", result)

        // Navigate down (should get "third")
        result = viewModel.navigateHistory(-1)
        assertEquals("third", result)
    }

    @Test
    fun `blank input is not sent`() = runTest {
        val viewModel = GameViewModel()

        viewModel.sendInput("   ")

        val state = viewModel.uiState.first()
        assertTrue(state.inputHistory.isEmpty())
    }
}
