package com.jcraw.mud.memory.world

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteWorldSeedRepository
 * Tests singleton pattern, save/load roundtrip, and updates
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteWorldSeedRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteWorldSeedRepository
    private val testDbPath = "test_world_seed.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteWorldSeedRepository(database)
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
    fun `save and get seed`() {
        repository.save("test-seed-123", "Ancient dungeon lore", "space_start").getOrThrow()

        val result = repository.get().getOrThrow()
        assertNotNull(result)
        assertEquals("test-seed-123", result?.seed)
        assertEquals("Ancient dungeon lore", result?.globalLore)
        assertEquals("space_start", result?.startingSpaceId)
    }

    @Test
    fun `get returns null when no seed exists`() {
        val result = repository.get().getOrThrow()
        assertNull(result)
    }

    @Test
    fun `save overwrites existing seed`() {
        repository.save("first-seed", "First lore", "space_one").getOrThrow()
        repository.save("second-seed", "Second lore", "space_two").getOrThrow()

        val result = repository.get().getOrThrow()
        assertNotNull(result)
        assertEquals("second-seed", result?.seed)
        assertEquals("Second lore", result?.globalLore)
        assertEquals("space_two", result?.startingSpaceId)
    }

    @Test
    fun `seed with long lore text persists correctly`() {
        val longLore = "A".repeat(10000) // 10k characters
        repository.save("seed", longLore, null).getOrThrow()

        val result = repository.get().getOrThrow()
        assertEquals(longLore, result?.globalLore)
        assertEquals(10000, result?.globalLore?.length)
    }

    @Test
    fun `seed with special characters persists correctly`() {
        val specialSeed = "seed-with-!@#$%^&*()_+-={}[]|:;<>?,./~`"
        val specialLore = "Lore with unicode: \u00A9 \u2122 \u00AE \u2665 \u266A"

        repository.save(specialSeed, specialLore, "space_special").getOrThrow()

        val result = repository.get().getOrThrow()
        assertEquals(specialSeed, result?.seed)
        assertEquals(specialLore, result?.globalLore)
        assertEquals("space_special", result?.startingSpaceId)
    }

    @Test
    fun `multiple updates preserve singleton constraint`() {
        // Multiple saves should always result in exactly one row
        repository.save("seed1", "lore1", "space1").getOrThrow()
        repository.save("seed2", "lore2", "space2").getOrThrow()
        repository.save("seed3", "lore3", "space3").getOrThrow()

        val conn = database.getConnection()
        conn.prepareStatement("SELECT COUNT(*) FROM world_seed").use { stmt ->
            val rs = stmt.executeQuery()
            rs.next()
            assertEquals(1, rs.getInt(1), "Should only have one row despite multiple saves")
        }

        val result = repository.get().getOrThrow()
        assertEquals("seed3", result?.seed)
        assertEquals("space3", result?.startingSpaceId)
    }

    @Test
    fun `empty seed and lore strings persist correctly`() {
        repository.save("", "", null).getOrThrow()

        val result = repository.get().getOrThrow()
        assertNotNull(result)
        assertEquals("", result?.seed)
        assertEquals("", result?.globalLore)
        assertNull(result?.startingSpaceId)
    }

    @Test
    fun `save and get with whitespace-only lore`() {
        val whitespaceLore = "   \n\t\r   "
        repository.save("seed", whitespaceLore, null).getOrThrow()

        val result = repository.get().getOrThrow()
        assertEquals(whitespaceLore, result?.globalLore)
    }
}
