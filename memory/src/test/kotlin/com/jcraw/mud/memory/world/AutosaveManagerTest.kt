package com.jcraw.mud.memory.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests for AutosaveManager.
 * Validates autosave triggers and coroutine management.
 */
class AutosaveManagerTest {

    private var saveCount = 0
    private val savedStates = mutableListOf<Triple<String, PlayerState, Map<String, SpacePropertiesComponent>>>()

    private val mockPersistence = object : WorldPersistence(mockk(), mockk(), mockk()) {
        override suspend fun saveWorldState(
            worldId: String,
            playerState: PlayerState,
            loadedChunks: Map<String, WorldChunkComponent>,
            loadedSpaces: Map<String, SpacePropertiesComponent>
        ): Result<Unit> {
            saveCount++
            savedStates.add(Triple(worldId, playerState, loadedSpaces))
            return Result.success(Unit)
        }
    }

    private fun mockk(): Any = object {} // Simplified mock

    @BeforeTest
    fun setup() {
        saveCount = 0
        savedStates.clear()
    }

    @Test
    fun `performAutosave calls persistence`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)
        val player = PlayerState("TestPlayer")

        manager.performAutosave("world", player, emptyMap(), emptyMap()).getOrThrow()

        assertEquals(1, saveCount)
        assertEquals("world", savedStates.first().first)
        assertEquals(player, savedStates.first().second)
    }

    @Test
    fun `onPlayerMove triggers autosave after threshold moves`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)
        val player = PlayerState("TestPlayer")

        // Move 4 times - should not trigger autosave
        repeat(4) {
            manager.onPlayerMove("world", player, emptyMap(), emptyMap())
        }
        assertEquals(0, saveCount)

        // 5th move triggers autosave
        manager.onPlayerMove("world", player, emptyMap(), emptyMap())
        assertEquals(1, saveCount)
    }

    @Test
    fun `onPlayerMove resets counter after autosave`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)
        val player = PlayerState("TestPlayer")

        // Trigger first autosave
        repeat(5) {
            manager.onPlayerMove("world", player, emptyMap(), emptyMap())
        }
        assertEquals(1, saveCount)

        // Move 4 more times - should not trigger second autosave yet
        repeat(4) {
            manager.onPlayerMove("world", player, emptyMap(), emptyMap())
        }
        assertEquals(1, saveCount)

        // 5th move after reset triggers second autosave
        manager.onPlayerMove("world", player, emptyMap(), emptyMap())
        assertEquals(2, saveCount)
    }

    @Test
    fun `getMoveCount returns current count`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        assertEquals(0, manager.getMoveCount())

        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())

        assertEquals(2, manager.getMoveCount())
    }

    @Test
    fun `resetMoveCounter clears count`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())
        assertEquals(2, manager.getMoveCount())

        manager.resetMoveCounter()
        assertEquals(0, manager.getMoveCount())
    }

    @Test
    fun `cancelAutosave stops periodic saves`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        manager.startPeriodicAutosave(
            "world",
            { PlayerState("P") },
            { emptyMap() },
            { emptyMap() }
        )

        manager.cancelAutosave()

        // Wait a bit to ensure no autosave happens
        delay(100.milliseconds)
        assertEquals(0, saveCount)
    }

    @Test
    fun `cancelAutosave resets move counter`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("P"), emptyMap(), emptyMap())
        assertEquals(2, manager.getMoveCount())

        manager.cancelAutosave()
        assertEquals(0, manager.getMoveCount())
    }

    @Test
    fun `getAutosaveInterval returns configured interval`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        val interval = manager.getAutosaveInterval()

        assertTrue(interval.inWholeMinutes == 2L)
    }

    @Test
    fun `getMoveThreshold returns configured threshold`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        assertEquals(5, manager.getMoveThreshold())
    }
}
