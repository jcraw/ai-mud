package com.jcraw.mud.memory.world

import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.world.ChunkLevel
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteWorldChunkRepository
 * Tests save/load, hierarchy queries, JSON serialization, and error handling
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteWorldChunkRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteWorldChunkRepository
    private val testDbPath = "test_world_chunks.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteWorldChunkRepository(database)
    }

    @AfterAll
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @BeforeEach
    fun resetDatabase() {
        database.clearAll()
    }

    // === Save/Load Tests ===

    @Test
    fun `save and find chunk by ID`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = listOf("child1", "child2"),
            lore = "Ancient world lore",
            biomeTheme = "Dark Fantasy",
            sizeEstimate = 100,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "world_root").getOrThrow()
        val loaded = repository.findById("world_root").getOrThrow()

        assertNotNull(loaded)
        assertEquals(chunk, loaded)
    }

    @Test
    fun `find nonexistent chunk returns null`() {
        val loaded = repository.findById("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `save chunk with null parent ID`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "Root lore",
            biomeTheme = "Root theme",
            sizeEstimate = 1000,
            mobDensity = 0.0,
            difficultyLevel = 1
        )

        repository.save(chunk, "root").getOrThrow()
        val loaded = repository.findById("root").getOrThrow()

        assertNotNull(loaded)
        assertNull(loaded?.parentId)
    }

    @Test
    fun `save chunk with parent ID`() {
        val parent = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = listOf("child1"),
            lore = "Parent",
            biomeTheme = "Theme",
            sizeEstimate = 100,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        val child = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "parent",
            children = emptyList(),
            lore = "Child",
            biomeTheme = "Theme",
            sizeEstimate = 50,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(parent, "parent").getOrThrow()
        repository.save(child, "child1").getOrThrow()

        val loadedChild = repository.findById("child1").getOrThrow()
        assertEquals("parent", loadedChild?.parentId)
    }

    @Test
    fun `update chunk overwrites existing`() {
        val original = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Original lore",
            biomeTheme = "Original theme",
            sizeEstimate = 10,
            mobDensity = 0.1,
            difficultyLevel = 1
        )

        repository.save(original, "zone1").getOrThrow()

        val updated = original.copy(lore = "Updated lore", difficultyLevel = 10)
        repository.save(updated, "zone1").getOrThrow()

        val loaded = repository.findById("zone1").getOrThrow()
        assertEquals("Updated lore", loaded?.lore)
        assertEquals(10, loaded?.difficultyLevel)
    }

    // === JSON Serialization Tests ===

    @Test
    fun `children list serializes correctly`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = null,
            children = listOf("zone1", "zone2", "zone3"),
            lore = "Test",
            biomeTheme = "Test",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "region1").getOrThrow()
        val loaded = repository.findById("region1").getOrThrow()

        assertEquals(3, loaded?.children?.size)
        assertTrue(loaded?.children?.contains("zone1") == true)
        assertTrue(loaded?.children?.contains("zone2") == true)
        assertTrue(loaded?.children?.contains("zone3") == true)
    }

    @Test
    fun `empty children list persists correctly`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            parentId = null,
            children = emptyList(),
            lore = "Test",
            biomeTheme = "Test",
            sizeEstimate = 1,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "space1").getOrThrow()
        val loaded = repository.findById("space1").getOrThrow()

        assertNotNull(loaded)
        assertTrue(loaded?.children?.isEmpty() == true)
    }

    // === Adjacency Tests ===

    @Test
    fun `adjacency map persists`() {
        val origin = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Origin",
            biomeTheme = "Hub",
            sizeEstimate = 5,
            mobDensity = 0.2,
            difficultyLevel = 2,
            adjacency = mapOf("north" to "zone_north")
        )

        val neighbor = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "North zone",
            biomeTheme = "Frost",
            sizeEstimate = 5,
            mobDensity = 0.3,
            difficultyLevel = 3
        )

        repository.save(origin, "zone_origin").getOrThrow()
        repository.save(neighbor, "zone_north").getOrThrow()

        val loadedOrigin = repository.findById("zone_origin").getOrThrow()
        assertEquals("zone_north", loadedOrigin?.adjacency?.get("north"))
    }

    @Test
    fun `findAdjacent returns neighbor when tracked`() {
        val origin = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Origin",
            biomeTheme = "Hub",
            sizeEstimate = 5,
            mobDensity = 0.2,
            difficultyLevel = 2
        ).withAdjacency("North", "zone_north")

        val neighbor = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "North zone",
            biomeTheme = "Frost",
            sizeEstimate = 5,
            mobDensity = 0.3,
            difficultyLevel = 3
        )

        repository.save(origin, "zone_origin").getOrThrow()
        repository.save(neighbor, "zone_north").getOrThrow()

        val adjacent = repository.findAdjacent("zone_origin", "north").getOrThrow()
        assertEquals(neighbor, adjacent)
    }

    @Test
    fun `findAdjacent returns null when no adjacency recorded`() {
        val origin = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Origin",
            biomeTheme = "Hub",
            sizeEstimate = 5,
            mobDensity = 0.2,
            difficultyLevel = 2
        )

        repository.save(origin, "zone_origin").getOrThrow()

        val adjacent = repository.findAdjacent("zone_origin", "east").getOrThrow()
        assertNull(adjacent)
    }

    // === Hierarchy Query Tests ===

    @Test
    fun `findByParent returns all children`() {
        val parent = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = listOf("r1", "r2"),
            lore = "World",
            biomeTheme = "Theme",
            sizeEstimate = 100,
            mobDensity = 0.5,
            difficultyLevel = 1
        )

        val child1 = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "world",
            children = emptyList(),
            lore = "Region 1",
            biomeTheme = "Theme",
            sizeEstimate = 50,
            mobDensity = 0.5,
            difficultyLevel = 3
        )

        val child2 = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "world",
            children = emptyList(),
            lore = "Region 2",
            biomeTheme = "Theme",
            sizeEstimate = 50,
            mobDensity = 0.5,
            difficultyLevel = 3
        )

        repository.save(parent, "world").getOrThrow()
        repository.save(child1, "r1").getOrThrow()
        repository.save(child2, "r2").getOrThrow()

        val children = repository.findByParent("world").getOrThrow()
        assertEquals(2, children.size)
        assertTrue(children.any { it.first == "r1" })
        assertTrue(children.any { it.first == "r2" })
    }

    @Test
    fun `findByParent returns empty list for nonexistent parent`() {
        val children = repository.findByParent("nonexistent").getOrThrow()
        assertTrue(children.isEmpty())
    }

    @Test
    fun `findByParent returns empty list for childless parent`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            parentId = null,
            children = emptyList(),
            lore = "Space",
            biomeTheme = "Theme",
            sizeEstimate = 1,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "space1").getOrThrow()

        val children = repository.findByParent("space1").getOrThrow()
        assertTrue(children.isEmpty())
    }

    // === Delete Tests ===

    @Test
    fun `delete removes chunk`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Delete me",
            biomeTheme = "Theme",
            sizeEstimate = 10,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "to_delete").getOrThrow()
        repository.delete("to_delete").getOrThrow()

        val loaded = repository.findById("to_delete").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `delete nonexistent chunk succeeds`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === getAll Tests ===

    @Test
    fun `getAll returns all chunks`() {
        val chunk1 = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = "World 1",
            biomeTheme = "Theme 1",
            sizeEstimate = 100,
            mobDensity = 0.5,
            difficultyLevel = 1
        )

        val chunk2 = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "world1",
            children = emptyList(),
            lore = "Region 1",
            biomeTheme = "Theme 1",
            sizeEstimate = 50,
            mobDensity = 0.5,
            difficultyLevel = 3
        )

        repository.save(chunk1, "world1").getOrThrow()
        repository.save(chunk2, "region1").getOrThrow()

        val all = repository.getAll().getOrThrow()
        assertEquals(2, all.size)
        assertTrue(all.containsKey("world1"))
        assertTrue(all.containsKey("region1"))
    }

    @Test
    fun `getAll returns empty map when no chunks exist`() {
        val all = repository.getAll().getOrThrow()
        assertTrue(all.isEmpty())
    }

    // === Edge Cases ===

    @Test
    fun `chunk with zero mob density persists correctly`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Peaceful zone",
            biomeTheme = "Safe Haven",
            sizeEstimate = 20,
            mobDensity = 0.0,
            difficultyLevel = 1
        )

        repository.save(chunk, "peaceful").getOrThrow()
        val loaded = repository.findById("peaceful").getOrThrow()

        assertEquals(0.0, loaded?.mobDensity)
    }

    @Test
    fun `chunk with max mob density persists correctly`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = null,
            children = emptyList(),
            lore = "Dangerous zone",
            biomeTheme = "Death Trap",
            sizeEstimate = 20,
            mobDensity = 1.0,
            difficultyLevel = 20
        )

        repository.save(chunk, "dangerous").getOrThrow()
        val loaded = repository.findById("dangerous").getOrThrow()

        assertEquals(1.0, loaded?.mobDensity)
    }

    @Test
    fun `chunk with very long lore persists correctly`() {
        val longLore = "A".repeat(10000)
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = emptyList(),
            lore = longLore,
            biomeTheme = "Theme",
            sizeEstimate = 100,
            mobDensity = 0.5,
            difficultyLevel = 5
        )

        repository.save(chunk, "long_lore").getOrThrow()
        val loaded = repository.findById("long_lore").getOrThrow()

        assertEquals(10000, loaded?.lore?.length)
    }

    @Test
    fun `all chunk levels persist correctly`() {
        ChunkLevel.entries.forEachIndexed { index, level ->
            val chunk = WorldChunkComponent(
                level = level,
                parentId = null,
                children = emptyList(),
                lore = "Level $level",
                biomeTheme = "Theme",
                sizeEstimate = 10,
                mobDensity = 0.5,
                difficultyLevel = 5
            )

            repository.save(chunk, "chunk_$index").getOrThrow()
            val loaded = repository.findById("chunk_$index").getOrThrow()

            assertEquals(level, loaded?.level)
        }
    }

    @Test
    fun `findAdjacent returns null (not implemented yet)`() {
        val result = repository.findAdjacent("chunk1", "north").getOrThrow()
        assertNull(result)
    }
}
