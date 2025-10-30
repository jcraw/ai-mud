package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.core.repository.WorldChunkRepository
import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.TerrainType
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for RespawnManager.
 * Validates mob respawn logic while preserving player changes.
 */
class RespawnManagerTest {

    private val testChunks = mutableMapOf<String, WorldChunkComponent>()
    private val testSpaces = mutableMapOf<String, SpacePropertiesComponent>()

    private val mockChunkRepo = object : WorldChunkRepository {
        override suspend fun save(chunk: WorldChunkComponent, id: String) =
            Result.success(Unit).also { testChunks[id] = chunk }

        override suspend fun findById(id: String) = Result.success(testChunks[id])
        override suspend fun findByParent(parentId: String) = Result.success(emptyList<WorldChunkComponent>())
        override suspend fun delete(id: String) = Result.success(Unit)
        override suspend fun getAll() = Result.success(testChunks.values.toList())
    }

    private val mockSpaceRepo = object : SpacePropertiesRepository {
        override suspend fun save(properties: SpacePropertiesComponent, chunkId: String) =
            Result.success(Unit).also { testSpaces[chunkId] = properties }

        override suspend fun findByChunkId(chunkId: String) = Result.success(testSpaces[chunkId])
        override suspend fun updateDescription(chunkId: String, desc: String) = Result.success(Unit)
        override suspend fun updateFlags(chunkId: String, flags: Map<String, Boolean>) = Result.success(Unit)
        override suspend fun addItems(chunkId: String, items: List<com.jcraw.mud.core.ItemInstance>) = Result.success(Unit)
        override suspend fun delete(chunkId: String) = Result.success(Unit)
    }

    private val mockMobSpawner = object : MobSpawner(mockk(), mockk()) {
        override fun spawnEntities(theme: String, mobDensity: Double, difficulty: Int, spaceSize: Int): List<Entity.NPC> {
            return listOf(
                Entity.NPC(
                    id = "mob_new",
                    name = "Respawned Goblin",
                    description = "A newly spawned goblin",
                    health = 20,
                    baseStats = mapOf(),
                    isHostile = true,
                    lootTableId = null,
                    goldDrop = 5
                )
            )
        }
    }

    private val mockDungeonInit = object : DungeonInitializer(mockk(), mockk(), mockk(), mockk(), mockk(), mockk()) {
        override suspend fun initializeDeepDungeon(seed: String): Result<String> {
            return Result.success("space_start")
        }
    }

    private val manager = RespawnManager(mockChunkRepo, mockSpaceRepo, mockMobSpawner, mockDungeonInit)

    private fun mockk(): Any = object {} // Simplified mock

    @BeforeTest
    fun setup() {
        testChunks.clear()
        testSpaces.clear()
    }

    @Test
    fun `respawnWorld processes SPACE level chunks`() = runBlocking {
        // Create hierarchy: WORLD -> SPACE
        val spaceChunk = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            parentId = "world",
            children = emptyList(),
            lore = "Test lore",
            biomeTheme = "dark forest",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )
        testChunks["space_1"] = spaceChunk
        testChunks["world"] = spaceChunk.copy(level = ChunkLevel.WORLD, children = listOf("space_1"))

        val oldMob = Entity.NPC("mob_old", "Old Goblin", "desc", 10, mapOf(), true, null, 5)
        val space = SpacePropertiesComponent(
            description = "A room",
            exits = emptyMap(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = listOf(oldMob),
            itemsDropped = emptyList(),
            stateFlags = mapOf("flag" to true)
        )
        testSpaces["space_1"] = space

        val count = manager.respawnWorld("world").getOrThrow()

        assertEquals(1, count)
        val respawned = testSpaces["space_1"]!!
        assertEquals(1, respawned.entities.size)
        assertEquals("mob_new", respawned.entities.first().id)
        // Verify flags preserved
        assertEquals(mapOf("flag" to true), respawned.stateFlags)
    }

    @Test
    fun `respawnWorld preserves state flags and items`() = runBlocking {
        val spaceChunk = WorldChunkComponent(ChunkLevel.SPACE, "world", emptyList(), "lore", "cave", 5, 0.3, 3)
        testChunks["space_1"] = spaceChunk
        testChunks["world"] = spaceChunk.copy(level = ChunkLevel.WORLD, children = listOf("space_1"))

        val item = com.jcraw.mud.core.ItemInstance("item_1", "sword", 5, null, 1)
        val space = SpacePropertiesComponent(
            "Room", emptyMap(), 50, TerrainType.NORMAL, emptyList(), emptyList(),
            listOf(Entity.NPC("mob", "Orc", "desc", 15, mapOf(), true, null, 10)),
            listOf(item),
            mapOf("treasure_found" to true, "trap_triggered" to true)
        )
        testSpaces["space_1"] = space

        manager.respawnWorld("world").getOrThrow()

        val respawned = testSpaces["space_1"]!!
        assertEquals(listOf(item), respawned.itemsDropped)
        assertEquals(mapOf("treasure_found" to true, "trap_triggered" to true), respawned.stateFlags)
    }

    @Test
    fun `clearSpaceEntities removes all mobs`() = runBlocking {
        val space = SpacePropertiesComponent(
            "Room", emptyMap(), 50, TerrainType.NORMAL, emptyList(), emptyList(),
            listOf(
                Entity.NPC("mob1", "Goblin", "desc", 10, mapOf(), true, null, 5),
                Entity.NPC("mob2", "Orc", "desc", 20, mapOf(), true, null, 10)
            ),
            emptyList(),
            emptyMap()
        )
        testSpaces["space_1"] = space

        val result = manager.clearSpaceEntities("space_1").getOrThrow()

        assertTrue(result.entities.isEmpty())
    }

    @Test
    fun `respawnSpaceEntities generates new mobs`() = runBlocking {
        val space = SpacePropertiesComponent(
            "Room", emptyMap(), 50, TerrainType.NORMAL, emptyList(), emptyList(),
            emptyList(), emptyList(), emptyMap()
        )
        testSpaces["space_1"] = space

        val result = manager.respawnSpaceEntities("space_1", "crypt", 0.5, 10, 20).getOrThrow()

        assertEquals(1, result.entities.size)
        assertEquals("mob_new", result.entities.first().id)
    }

    @Test
    fun `createFreshStart calls dungeon initializer`() = runBlocking {
        val result = manager.createFreshStart("test_seed").getOrThrow()

        assertEquals("space_start", result)
    }

    @Test
    fun `respawnWorld handles missing space gracefully`() = runBlocking {
        val spaceChunk = WorldChunkComponent(ChunkLevel.SPACE, "world", emptyList(), "lore", "cave", 5, 0.3, 3)
        testChunks["space_1"] = spaceChunk
        testChunks["world"] = spaceChunk.copy(level = ChunkLevel.WORLD, children = listOf("space_1"))
        // Don't add space to testSpaces

        val count = manager.respawnWorld("world").getOrThrow()

        assertEquals(0, count) // Should not crash, just skip missing space
    }

    @Test
    fun `respawnWorld handles nested hierarchy`() = runBlocking {
        val worldChunk = WorldChunkComponent(ChunkLevel.WORLD, null, listOf("region_1"), "lore", "dungeon", 100, 0.5, 5)
        val regionChunk = WorldChunkComponent(ChunkLevel.REGION, "world", listOf("zone_1"), "lore", "upper", 50, 0.5, 5)
        val zoneChunk = WorldChunkComponent(ChunkLevel.ZONE, "region_1", listOf("subzone_1"), "lore", "entrance", 20, 0.5, 5)
        val subzoneChunk = WorldChunkComponent(ChunkLevel.SUBZONE, "zone_1", listOf("space_1"), "lore", "hall", 10, 0.5, 5)
        val spaceChunk = WorldChunkComponent(ChunkLevel.SPACE, "subzone_1", emptyList(), "lore", "room", 5, 0.5, 5)

        testChunks["world"] = worldChunk
        testChunks["region_1"] = regionChunk
        testChunks["zone_1"] = zoneChunk
        testChunks["subzone_1"] = subzoneChunk
        testChunks["space_1"] = spaceChunk

        val space = SpacePropertiesComponent(
            "Room", emptyMap(), 50, TerrainType.NORMAL, emptyList(), emptyList(),
            listOf(Entity.NPC("old", "Old", "desc", 10, mapOf(), true, null, 5)),
            emptyList(), emptyMap()
        )
        testSpaces["space_1"] = space

        val count = manager.respawnWorld("world").getOrThrow()

        assertEquals(1, count)
    }
}
