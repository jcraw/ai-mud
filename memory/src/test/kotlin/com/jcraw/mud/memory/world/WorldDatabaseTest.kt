package com.jcraw.mud.memory.world

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.sql.SQLException

/**
 * Integration tests for world database schema and connection management
 * Tests table creation, indices, foreign keys, and clearAll()
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorldDatabaseTest {
    private lateinit var database: WorldDatabase
    private val testDbPath = "test_world.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
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

    @Test
    fun `database connection is created`() {
        val conn = database.getConnection()
        assertNotNull(conn)
        assertFalse(conn.isClosed)
    }

    @Test
    fun `world_seed table exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getTables(null, null, "world_seed", null)
        assertTrue(rs.next())
    }

    @Test
    fun `world_chunks table exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getTables(null, null, "world_chunks", null)
        assertTrue(rs.next())
    }

    @Test
    fun `space_properties table exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getTables(null, null, "space_properties", null)
        assertTrue(rs.next())
    }

    @Test
    fun `world_seed has correct columns`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getColumns(null, null, "world_seed", null)

        val columns = mutableListOf<String>()
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME"))
        }

        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("seed_string"))
        assertTrue(columns.contains("global_lore"))
    }

    @Test
    fun `world_chunks has correct columns`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getColumns(null, null, "world_chunks", null)

        val columns = mutableListOf<String>()
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME"))
        }

        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("level"))
        assertTrue(columns.contains("parent_id"))
        assertTrue(columns.contains("children"))
        assertTrue(columns.contains("lore"))
        assertTrue(columns.contains("biome_theme"))
        assertTrue(columns.contains("size_estimate"))
        assertTrue(columns.contains("mob_density"))
        assertTrue(columns.contains("difficulty_level"))
    }

    @Test
    fun `space_properties has correct columns`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getColumns(null, null, "space_properties", null)

        val columns = mutableListOf<String>()
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME"))
        }

        assertTrue(columns.contains("chunk_id"))
        assertTrue(columns.contains("description"))
        assertTrue(columns.contains("exits"))
        assertTrue(columns.contains("brightness"))
        assertTrue(columns.contains("terrain_type"))
        assertTrue(columns.contains("traps"))
        assertTrue(columns.contains("resources"))
        assertTrue(columns.contains("entities"))
        assertTrue(columns.contains("items_dropped"))
        assertTrue(columns.contains("state_flags"))
    }

    @Test
    fun `idx_chunks_parent index exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getIndexInfo(null, null, "world_chunks", false, false)

        val indices = mutableListOf<String>()
        while (rs.next()) {
            indices.add(rs.getString("INDEX_NAME"))
        }

        assertTrue(indices.any { it.contains("idx_chunks_parent") })
    }

    @Test
    fun `idx_chunks_level index exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getIndexInfo(null, null, "world_chunks", false, false)

        val indices = mutableListOf<String>()
        while (rs.next()) {
            indices.add(rs.getString("INDEX_NAME"))
        }

        assertTrue(indices.any { it.contains("idx_chunks_level") })
    }

    @Test
    fun `idx_space_chunk index exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getIndexInfo(null, null, "space_properties", false, false)

        val indices = mutableListOf<String>()
        while (rs.next()) {
            indices.add(rs.getString("INDEX_NAME"))
        }

        assertTrue(indices.any { it.contains("idx_space_chunk") })
    }

    @Test
    fun `foreign key constraint prevents orphaned space_properties`() {
        val conn = database.getConnection()

        // Try to insert space_properties with non-existent chunk_id
        assertThrows<SQLException> {
            conn.prepareStatement(
                "INSERT INTO space_properties (chunk_id, description, exits, brightness, terrain_type, traps, resources, entities, items_dropped, state_flags) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
            ).use { stmt ->
                stmt.setString(1, "nonexistent_chunk")
                stmt.setString(2, "Test")
                stmt.setString(3, "{}")
                stmt.setInt(4, 50)
                stmt.setString(5, "NORMAL")
                stmt.setString(6, "[]")
                stmt.setString(7, "[]")
                stmt.setString(8, "[]")
                stmt.setString(9, "[]")
                stmt.setString(10, "{}")
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `clearAll removes all data`() {
        val conn = database.getConnection()

        // Insert data
        conn.prepareStatement("INSERT INTO world_seed (id, seed_string, global_lore) VALUES (1, 'test', 'lore')").use { it.executeUpdate() }
        conn.prepareStatement("INSERT INTO world_chunks (id, level, parent_id, children, lore, biome_theme, size_estimate, mob_density, difficulty_level) VALUES ('chunk1', 'WORLD', NULL, '[]', 'lore', 'theme', 10, 0.5, 5)").use { it.executeUpdate() }
        conn.prepareStatement("INSERT INTO space_properties (chunk_id, description, exits, brightness, terrain_type, traps, resources, entities, items_dropped, state_flags) VALUES ('chunk1', 'desc', '{}', 50, 'NORMAL', '[]', '[]', '[]', '[]', '{}')").use { it.executeUpdate() }

        // Clear
        database.clearAll()

        // Verify all tables empty
        conn.prepareStatement("SELECT COUNT(*) FROM world_seed").use { stmt ->
            val rs = stmt.executeQuery()
            rs.next()
            assertEquals(0, rs.getInt(1))
        }

        conn.prepareStatement("SELECT COUNT(*) FROM world_chunks").use { stmt ->
            val rs = stmt.executeQuery()
            rs.next()
            assertEquals(0, rs.getInt(1))
        }

        conn.prepareStatement("SELECT COUNT(*) FROM space_properties").use { stmt ->
            val rs = stmt.executeQuery()
            rs.next()
            assertEquals(0, rs.getInt(1))
        }
    }

    @Test
    fun `reconnection after close works`() {
        database.close()
        val conn = database.getConnection()
        assertNotNull(conn)
        assertFalse(conn.isClosed)
    }

    @Test
    fun `world_seed enforces singleton constraint`() {
        val conn = database.getConnection()

        // Insert first seed
        conn.prepareStatement("INSERT INTO world_seed (id, seed_string, global_lore) VALUES (1, 'seed1', 'lore1')").use { it.executeUpdate() }

        // Try to insert second seed with different ID
        assertThrows<SQLException> {
            conn.prepareStatement("INSERT INTO world_seed (id, seed_string, global_lore) VALUES (2, 'seed2', 'lore2')").use { it.executeUpdate() }
        }
    }
}
