package com.jcraw.mud.memory.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.repository.WorldSeedInfo
import com.jcraw.mud.core.repository.WorldSeedRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.TerrainType
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for WorldPersistence.
 * Validates save/load operations and state integrity.
 */
class WorldPersistenceTest {

    private val testSeed = mutableListOf<WorldSeedInfo?>()
    private val testChunks = mutableMapOf<String, WorldChunkComponent>()
    private val testSpaces = mutableMapOf<String, SpacePropertiesComponent>()

    private val mockSeedRepo = object : WorldSeedRepository {
        override fun save(seed: String, globalLore: String, startingSpaceId: String?) =
            Result.success(Unit).also {
                testSeed.clear()
                testSeed.add(WorldSeedInfo(seed, globalLore, startingSpaceId))
            }

        override fun get() = Result.success(testSeed.firstOrNull())
    }

    private val mockChunkRepo = object : WorldChunkRepository {
        override fun save(chunk: WorldChunkComponent, id: String) =
            Result.success(Unit).also { testChunks[id] = chunk }

        override fun findById(id: String) = Result.success(testChunks[id])
        override fun findByParent(parentId: String) = Result.success(emptyList<Pair<String, WorldChunkComponent>>())
        override fun findAdjacent(currentId: String, direction: String) = Result.success(null)
        override fun delete(id: String) = Result.success(Unit)
        override fun getAll() = Result.success(testChunks)
    }

    private val mockSpaceRepo = object : SpacePropertiesRepository {
        override fun save(properties: SpacePropertiesComponent, chunkId: String) =
            Result.success(Unit).also { testSpaces[chunkId] = properties }

        override fun findByChunkId(chunkId: String) = Result.success(testSpaces[chunkId])
        override fun updateDescription(chunkId: String, description: String) = Result.success(Unit)
        override fun updateFlags(chunkId: String, flags: Map<String, Boolean>) = Result.success(Unit)
        override fun addItems(chunkId: String, items: List<com.jcraw.mud.core.ItemInstance>) = Result.success(Unit)
        override fun delete(chunkId: String) = Result.success(Unit)
    }

    private val persistence = WorldPersistence(mockSeedRepo, mockChunkRepo, mockSpaceRepo)

    @BeforeTest
    fun setup() {
        testSeed.clear()
        testChunks.clear()
        testSpaces.clear()
    }

    @Test
    fun `saveWorldState persists chunks and spaces`() = runBlocking {
        val chunk = WorldChunkComponent(ChunkLevel.SPACE, "world", emptyList(), "lore", "cave", 5, 0.5, 3)
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "A test room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        persistence.saveWorldState(
            worldId = "world",
            playerState = PlayerState("player1", "Player", "room1"),
            loadedChunks = mapOf("chunk_1" to chunk),
            loadedSpaces = mapOf("space_1" to space)
        ).getOrThrow()

        assertEquals(chunk, testChunks["chunk_1"])
        assertEquals(space, testSpaces["space_1"])
    }

    @Test
    fun `loadWorldState retrieves global lore and space`() = runBlocking {
        testSeed.add(WorldSeedInfo("seed123", "Ancient dungeon lore", "space_start"))
        val space = SpacePropertiesComponent(
            name = "Starting Chamber",
            description = "The starting room",
            exits = emptyList(),
            brightness = 100,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
        testSpaces["space_start"] = space

        val (lore, loadedSpace) = persistence.loadWorldState("space_start").getOrThrow()!!

        assertEquals("Ancient dungeon lore", lore)
        assertEquals(space, loadedSpace)
    }

    @Test
    fun `loadWorldState returns null when no world exists`() = runBlocking {
        val result = persistence.loadWorldState("space_1").getOrThrow()

        assertNull(result)
    }

    @Test
    fun `saveSpace performs incremental save`() = runBlocking {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "A test room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        persistence.saveSpace(space, "space_1").getOrThrow()

        assertEquals(space, testSpaces["space_1"])
    }

    @Test
    fun `loadChunk retrieves chunk by ID`() = runBlocking {
        val chunk = WorldChunkComponent(ChunkLevel.ZONE, "region", emptyList(), "lore", "forest", 20, 0.4, 7)
        testChunks["chunk_1"] = chunk

        val loaded = persistence.loadChunk("chunk_1").getOrThrow()

        assertEquals(chunk, loaded)
    }

    @Test
    fun `loadSpace retrieves space by chunk ID`() = runBlocking {
        val space = SpacePropertiesComponent(
            name = "Test Room",
            description = "A test room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
        testSpaces["space_1"] = space

        val loaded = persistence.loadSpace("space_1").getOrThrow()

        assertEquals(space, loaded)
    }

    @Test
    fun `prefetchAdjacentSpaces loads multiple spaces`() = runBlocking {
        val space1 = SpacePropertiesComponent(
            name = "Test Room 1",
            description = "First test room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
        val space2 = SpacePropertiesComponent(
            name = "Test Room 2",
            description = "Second test room",
            exits = emptyList(),
            brightness = 60,
            terrainType = TerrainType.DIFFICULT,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
        testSpaces["space_1"] = space1
        testSpaces["space_2"] = space2

        val prefetched = persistence.prefetchAdjacentSpaces(
            currentSpaceId = "space_current",
            exitTargetIds = listOf("space_1", "space_2", "space_missing")
        ).getOrThrow()

        assertEquals(2, prefetched.size)
        assertEquals(space1, prefetched["space_1"])
        assertEquals(space2, prefetched["space_2"])
    }

    @Test
    fun `saveWorldSeed stores seed and lore`() = runBlocking {
        persistence.saveWorldSeed("seed123", "Epic dungeon lore").getOrThrow()

        val seedInfo = testSeed.first()!!
        assertEquals("seed123", seedInfo.seed)
        assertEquals("Epic dungeon lore", seedInfo.globalLore)
        assertNull(seedInfo.startingSpaceId)
    }

    @Test
    fun `getWorldSeed retrieves stored seed`() = runBlocking {
        testSeed.add(WorldSeedInfo("seed456", "Ancient ruins lore", "space_start"))

        val seedInfo = persistence.getWorldSeed().getOrThrow()!!

        assertEquals("seed456", seedInfo.seed)
        assertEquals("Ancient ruins lore", seedInfo.globalLore)
        assertEquals("space_start", seedInfo.startingSpaceId)
    }

    @Test
    fun `getWorldSeed returns null when not initialized`() = runBlocking {
        val result = persistence.getWorldSeed().getOrThrow()

        assertNull(result)
    }
}
