package com.jcraw.mud.memory.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedRepository
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

    private val mockSeedRepo = object : WorldSeedRepository {
        override fun save(seed: String, globalLore: String, startingSpaceId: String?) = Result.success(Unit)
        override fun get() = Result.success<com.jcraw.mud.core.repository.WorldSeedInfo?>(null)
    }

    private val mockChunkRepo = object : WorldChunkRepository {
        override fun save(chunk: WorldChunkComponent, id: String) = Result.success(Unit)
        override fun findById(id: String) = Result.success<WorldChunkComponent?>(null)
        override fun findByParent(parentId: String) = Result.success(emptyList<Pair<String, WorldChunkComponent>>())
        override fun findAdjacent(currentId: String, direction: String) = Result.success<WorldChunkComponent?>(null)
        override fun delete(id: String) = Result.success(Unit)
        override fun getAll() = Result.success(emptyMap<String, WorldChunkComponent>())
    }

    private val mockSpaceRepo = object : SpacePropertiesRepository {
        override fun save(properties: SpacePropertiesComponent, chunkId: String) = Result.success(Unit)
        override fun findByChunkId(chunkId: String) = Result.success<SpacePropertiesComponent?>(null)
        override fun updateDescription(chunkId: String, description: String) = Result.success(Unit)
        override fun updateFlags(chunkId: String, flags: Map<String, Boolean>) = Result.success(Unit)
        override fun addItems(chunkId: String, items: List<com.jcraw.mud.core.ItemInstance>) = Result.success(Unit)
        override fun delete(chunkId: String) = Result.success(Unit)
    }

    private val mockPersistence = object : WorldPersistence(mockSeedRepo, mockChunkRepo, mockSpaceRepo) {
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

    @BeforeTest
    fun setup() {
        saveCount = 0
        savedStates.clear()
    }

    @Test
    fun `performAutosave calls persistence`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)
        val player = PlayerState("test-id", "TestPlayer", "test-room")

        manager.performAutosave("world", player, emptyMap(), emptyMap()).getOrThrow()

        assertEquals(1, saveCount)
        assertEquals("world", savedStates.first().first)
        assertEquals(player, savedStates.first().second)
    }

    @Test
    fun `onPlayerMove triggers autosave after threshold moves`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)
        val player = PlayerState("test-id", "TestPlayer", "test-room")

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
        val player = PlayerState("test-id", "TestPlayer", "test-room")

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

        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())

        assertEquals(2, manager.getMoveCount())
    }

    @Test
    fun `resetMoveCounter clears count`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())
        assertEquals(2, manager.getMoveCount())

        manager.resetMoveCounter()
        assertEquals(0, manager.getMoveCount())
    }

    @Test
    fun `cancelAutosave stops periodic saves`() = runTest {
        val manager = AutosaveManager(mockPersistence, this)

        manager.startPeriodicAutosave(
            "world",
            { PlayerState("p-id", "P", "test-room") },
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

        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())
        manager.onPlayerMove("world", PlayerState("p-id", "P", "test-room"), emptyMap(), emptyMap())
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
