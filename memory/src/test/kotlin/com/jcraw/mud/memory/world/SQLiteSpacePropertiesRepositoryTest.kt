package com.jcraw.mud.memory.world

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.world.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteSpacePropertiesRepository
 * Tests complex JSON serialization, optimized updates, and full roundtrip
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteSpacePropertiesRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteSpacePropertiesRepository
    private val testDbPath = "test_space_props.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteSpacePropertiesRepository(database)

        // Create a parent chunk to satisfy foreign key
        val conn = database.getConnection()
        conn.prepareStatement(
            "INSERT INTO world_chunks (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setString(1, "chunk1")
            stmt.setString(2, "SPACE")
            stmt.setNull(3, java.sql.Types.VARCHAR)
            stmt.setString(4, "[]")
            stmt.setString(5, "Test lore")
            stmt.setString(6, "Test theme")
            stmt.setInt(7, 1)
            stmt.setDouble(8, 0.5)
            stmt.setInt(9, 5)
            stmt.executeUpdate()
        }
    }

    @AfterAll
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @BeforeEach
    fun resetSpaceProperties() {
        val conn = database.getConnection()
        conn.createStatement().use { it.execute("DELETE FROM space_properties") }
    }

    // === Save/Load Tests ===

    @Test
    fun `save and find space properties by chunk ID`() {
        val props = SpacePropertiesComponent(
            name = "Dark Room",
            description = "A dark room",
            exits = listOf(ExitData("chunk2", "north", "A passage north", emptyList(), false)),
            brightness = 30,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = listOf("entity1", "entity2"),
            itemsDropped = emptyList(),
            stateFlags = mapOf("door_opened" to true)
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(props, loaded)
    }

    @Test
    fun `find nonexistent space returns null`() {
        val loaded = repository.findByChunkId("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `update space properties overwrites existing`() {
        val original = SpacePropertiesComponent(
            name = "Original Room",
            description = "Original",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(original, "chunk1").getOrThrow()

        val updated = original.copy(description = "Updated", brightness = 100)
        repository.save(updated, "chunk1").getOrThrow()

        val loaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals("Updated", loaded?.description)
        assertEquals(100, loaded?.brightness)
    }

    // === Exits Serialization Tests ===

    @Test
    fun `exits list serializes correctly`() {
        val exits = listOf(
            ExitData("target1", "north", "North exit", emptyList(), false),
            ExitData("target2", "south", "Hidden south", listOf(Condition.SkillCheck("Perception", 10)), true),
            ExitData("target3", "up", "Climb up", listOf(Condition.ItemRequired("climbing_gear")), false)
        )

        val props = SpacePropertiesComponent(
            name = "Test Room",
            description = "Test",
            exits = exits,
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(3, loaded?.exits?.size)
        assertEquals("target1", loaded?.exits?.get(0)?.targetId)
        assertEquals("target2", loaded?.exits?.get(1)?.targetId)
        assertEquals("target3", loaded?.exits?.get(2)?.targetId)
    }

    @Test
    fun `empty exits list persists correctly`() {
        val props = SpacePropertiesComponent(
            name = "No Exits Room",
            description = "No exits",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertNotNull(loaded)
        assertTrue(loaded?.exits?.isEmpty() == true)
    }

    // === Terrain Type Tests ===

    @Test
    fun `all terrain types persist correctly`() {
        TerrainType.entries.forEach { terrain ->
            val props = SpacePropertiesComponent(
                name = "Test $terrain Room",
                description = "Test $terrain",
                exits = emptyList(),
                brightness = 50,
                terrainType = terrain,
                traps = emptyList(),
                resources = emptyList(),
                entities = emptyList(),
                itemsDropped = emptyList(),
                stateFlags = emptyMap()
            )

            database.clearAll()
            // Re-create chunk
            val conn = database.getConnection()
            conn.prepareStatement(
                "INSERT INTO world_chunks (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, "chunk1")
                stmt.setString(2, "SPACE")
                stmt.setNull(3, java.sql.Types.VARCHAR)
                stmt.setString(4, "[]")
                stmt.setString(5, "Test lore")
                stmt.setString(6, "Test theme")
                stmt.setInt(7, 1)
                stmt.setDouble(8, 0.5)
                stmt.setInt(9, 5)
                stmt.executeUpdate()
            }

            repository.save(props, "chunk1").getOrThrow()
            val loaded = repository.findByChunkId("chunk1").getOrThrow()

            assertEquals(terrain, loaded?.terrainType)
        }
    }

    // === Traps Serialization Tests ===

    @Test
    fun `traps list serializes correctly`() {
        val traps = listOf(
            TrapData("trap1", "pit", 10, false),
            TrapData("trap2", "poison_dart", 15, false)
        )

        val props = SpacePropertiesComponent(
            name = "Trapped Room",
            description = "Trapped room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = traps,
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(2, loaded?.traps?.size)
        assertEquals("trap1", loaded?.traps?.get(0)?.id)
        assertEquals("pit", loaded?.traps?.get(0)?.type)
        assertEquals(10, loaded?.traps?.get(0)?.difficulty)
    }

    // === Resources Serialization Tests ===

    @Test
    fun `resources list serializes correctly`() {
        val resources = listOf(
            ResourceNode("res1", "iron_ore", 5, 100),
            ResourceNode("res2", "herb_patch", 10, null)
        )

        val props = SpacePropertiesComponent(
            name = "Resource Area",
            description = "Resource-rich area",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = resources,
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(2, loaded?.resources?.size)
        assertEquals("iron_ore", loaded?.resources?.get(0)?.templateId)
        assertEquals(5, loaded?.resources?.get(0)?.quantity)
        assertEquals(100, loaded?.resources?.get(0)?.respawnTime)
        assertNull(loaded?.resources?.get(1)?.respawnTime)
    }

    // === Entities Serialization Tests ===

    @Test
    fun `entities list serializes correctly`() {
        val entities = listOf("entity1", "entity2", "entity3")

        val props = SpacePropertiesComponent(
            name = "Populated Room",
            description = "Populated room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = entities,
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(3, loaded?.entities?.size)
        assertTrue(loaded?.entities?.contains("entity1") == true)
    }

    // === ItemsDropped Serialization Tests ===

    @Test
    fun `itemsDropped list serializes correctly`() {
        val items = listOf(
            ItemInstance("item1", "sword_template", 5, null, 1),
            ItemInstance("item2", "potion_template", 3, 10, 2)
        )

        val props = SpacePropertiesComponent(
            name = "Loot Room",
            description = "Room with loot",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = items,
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(2, loaded?.itemsDropped?.size)
        assertEquals("item1", loaded?.itemsDropped?.get(0)?.id)
        assertEquals(5, loaded?.itemsDropped?.get(0)?.quality)
        assertEquals(10, loaded?.itemsDropped?.get(1)?.charges)
    }

    // === StateFlags Serialization Tests ===

    @Test
    fun `stateFlags map serializes correctly`() {
        val flags = mapOf(
            "door_opened" to true,
            "lever_pulled" to false,
            "boss_defeated" to true
        )

        val props = SpacePropertiesComponent(
            name = "Stateful Room",
            description = "Stateful room",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = flags
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(3, loaded?.stateFlags?.size)
        assertEquals(true, loaded?.stateFlags?.get("door_opened"))
        assertEquals(false, loaded?.stateFlags?.get("lever_pulled"))
    }

    @Test
    fun `safe zone and treasure flags persist`() {
        val props = SpacePropertiesComponent(
            name = "Treasure Vault",
            description = "Glittering vault",
            exits = emptyList(),
            brightness = 70,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap(),
            isSafeZone = true,
            isTreasureRoom = true
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertTrue(loaded?.isSafeZone == true)
        assertTrue(loaded?.isTreasureRoom == true)
    }

    // === Optimized Update Tests ===

    @Test
    fun `updateDescription updates only description`() {
        val props = SpacePropertiesComponent(
            name = "Original Room",
            description = "Original",
            exits = listOf(ExitData("target", "north", "Exit", emptyList(), false)),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = listOf("entity1"),
            itemsDropped = emptyList(),
            stateFlags = mapOf("flag" to true)
        )

        repository.save(props, "chunk1").getOrThrow()
        repository.updateDescription("chunk1", "New description").getOrThrow()

        val loaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals("New description", loaded?.description)
        assertEquals(1, loaded?.exits?.size) // Exits unchanged
        assertEquals(1, loaded?.entities?.size) // Entities unchanged
    }

    @Test
    fun `updateFlags updates only flags`() {
        val props = SpacePropertiesComponent(
            name = "Test Room",
            description = "Test",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = mapOf("old_flag" to true)
        )

        repository.save(props, "chunk1").getOrThrow()
        repository.updateFlags("chunk1", mapOf("new_flag" to false)).getOrThrow()

        val loaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals(1, loaded?.stateFlags?.size)
        assertFalse(loaded?.stateFlags?.containsKey("old_flag") == true)
        assertEquals(false, loaded?.stateFlags?.get("new_flag"))
    }

    @Test
    fun `addItems appends to existing items`() {
        val props = SpacePropertiesComponent(
            name = "Test Room",
            description = "Test",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = listOf(ItemInstance("item1", "template1", 5, null, 1)),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()

        val newItems = listOf(ItemInstance("item2", "template2", 7, null, 1))
        repository.addItems("chunk1", newItems).getOrThrow()

        val loaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals(2, loaded?.itemsDropped?.size)
        assertTrue(loaded?.itemsDropped?.any { it.id == "item1" } == true)
        assertTrue(loaded?.itemsDropped?.any { it.id == "item2" } == true)
    }

    // === Delete Tests ===

    @Test
    fun `delete removes space properties`() {
        val props = SpacePropertiesComponent(
            name = "Delete Me",
            description = "Delete me",
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        repository.delete("chunk1").getOrThrow()

        val loaded = repository.findByChunkId("chunk1").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `delete nonexistent space succeeds`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === Edge Cases ===

    @Test
    fun `brightness extremes persist correctly`() {
        val darkProps = SpacePropertiesComponent(
            name = "Pitch Black",
            description = "Pitch black",
            exits = emptyList(),
            brightness = 0,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(darkProps, "chunk1").getOrThrow()
        val darkLoaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals(0, darkLoaded?.brightness)

        database.clearAll()
        // Re-create chunk
        val conn = database.getConnection()
        conn.prepareStatement(
            "INSERT INTO world_chunks (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { stmt ->
            stmt.setString(1, "chunk1")
            stmt.setString(2, "SPACE")
            stmt.setNull(3, java.sql.Types.VARCHAR)
            stmt.setString(4, "[]")
            stmt.setString(5, "Test lore")
            stmt.setString(6, "Test theme")
            stmt.setInt(7, 1)
            stmt.setDouble(8, 0.5)
            stmt.setInt(9, 5)
            stmt.executeUpdate()
        }

        val brightProps = darkProps.copy(brightness = 100, description = "Bright")
        repository.save(brightProps, "chunk1").getOrThrow()
        val brightLoaded = repository.findByChunkId("chunk1").getOrThrow()
        assertEquals(100, brightLoaded?.brightness)
    }

    @Test
    fun `very long description persists correctly`() {
        val longDesc = "A".repeat(10000)
        val props = SpacePropertiesComponent(
            name = "Long Description",
            description = longDesc,
            exits = emptyList(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )

        repository.save(props, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertEquals(10000, loaded?.description?.length)
    }

    @Test
    fun `complex nested data persists correctly`() {
        val complexProps = SpacePropertiesComponent(
            name = "Complex Room",
            description = "Complex room with everything",
            exits = listOf(
                ExitData("t1", "north", "Hidden", listOf(Condition.SkillCheck("Perception", 15)), true),
                ExitData("t2", "south", "Open", emptyList(), false),
                ExitData("t3", "up", "Locked", listOf(Condition.ItemRequired("key")), false)
            ),
            brightness = 75,
            terrainType = TerrainType.DIFFICULT,
            traps = listOf(TrapData("trap1", "spike", 12, false)),
            resources = listOf(ResourceNode("res1", "gold_vein", 3, 200)),
            entities = listOf("mob1", "mob2", "npc1"),
            itemsDropped = listOf(
                ItemInstance("drop1", "sword", 8, null, 1),
                ItemInstance("drop2", "potion", 5, 10, 3)
            ),
            stateFlags = mapOf("explored" to true, "trap_triggered" to false)
        )

        repository.save(complexProps, "chunk1").getOrThrow()
        val loaded = repository.findByChunkId("chunk1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(3, loaded?.exits?.size)
        assertEquals(1, loaded?.traps?.size)
        assertEquals(1, loaded?.resources?.size)
        assertEquals(3, loaded?.entities?.size)
        assertEquals(2, loaded?.itemsDropped?.size)
        assertEquals(2, loaded?.stateFlags?.size)
    }
}
