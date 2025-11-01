package com.jcraw.mud.memory.world

import com.jcraw.mud.core.RespawnComponent
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteRespawnRepository
 * Tests save/load, queries by space/time, timer operations, and edge cases
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteRespawnRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteRespawnRepository
    private val testDbPath = "test_respawn.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteRespawnRepository(database)
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
    fun `save and find respawn component by entity ID`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )

        repository.save(component, "goblin_1", "space_1").getOrThrow()
        val loaded = repository.findByEntityId("goblin_1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(component, loaded?.first)
        assertEquals("space_1", loaded?.second)
    }

    @Test
    fun `find nonexistent entity returns null`() {
        val loaded = repository.findByEntityId("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `update respawn component overwrites existing`() {
        val original = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "orc_template"
        )

        repository.save(original, "orc_1", "space_1").getOrThrow()

        val updated = original.copy(lastKilled = 1000L)
        repository.save(updated, "orc_1", "space_1").getOrThrow()

        val loaded = repository.findByEntityId("orc_1").getOrThrow()
        assertEquals(1000L, loaded?.first?.lastKilled)
    }

    @Test
    fun `save component with different space ID updates location`() {
        val component = RespawnComponent(
            respawnTurns = 300L,
            lastKilled = 0L,
            originalEntityId = "troll_template"
        )

        repository.save(component, "troll_1", "space_1").getOrThrow()
        repository.save(component, "troll_1", "space_2").getOrThrow()

        val loaded = repository.findByEntityId("troll_1").getOrThrow()
        assertEquals("space_2", loaded?.second)
    }

    // === findBySpaceId Tests ===

    @Test
    fun `findBySpaceId returns all components in space`() {
        val comp1 = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )
        val comp2 = RespawnComponent(
            respawnTurns = 600L,
            lastKilled = 0L,
            originalEntityId = "orc_template"
        )

        repository.save(comp1, "mob_1", "space_1").getOrThrow()
        repository.save(comp2, "mob_2", "space_1").getOrThrow()

        val results = repository.findBySpaceId("space_1").getOrThrow()
        assertEquals(2, results.size)
        assertTrue(results.any { it.first == "mob_1" })
        assertTrue(results.any { it.first == "mob_2" })
    }

    @Test
    fun `findBySpaceId returns empty list for nonexistent space`() {
        val results = repository.findBySpaceId("nonexistent").getOrThrow()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findBySpaceId returns empty list for space with no mobs`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )
        repository.save(component, "mob_1", "space_1").getOrThrow()

        val results = repository.findBySpaceId("space_2").getOrThrow()
        assertTrue(results.isEmpty())
    }

    // === findReadyToRespawn Tests ===

    @Test
    fun `findReadyToRespawn returns components past respawn time`() {
        val ready1 = RespawnComponent(
            respawnTurns = 100L,
            lastKilled = 900L,
            originalEntityId = "goblin_template"
        )
        val ready2 = RespawnComponent(
            respawnTurns = 200L,
            lastKilled = 800L,
            originalEntityId = "orc_template"
        )
        val notReady = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 999L,
            originalEntityId = "dragon_template"
        )

        repository.save(ready1, "mob_1", "space_1").getOrThrow()
        repository.save(ready2, "mob_2", "space_1").getOrThrow()
        repository.save(notReady, "mob_3", "space_2").getOrThrow()

        val results = repository.findReadyToRespawn(1000L).getOrThrow()
        assertEquals(2, results.size)
        assertTrue(results.any { it.first == "mob_1" })
        assertTrue(results.any { it.first == "mob_2" })
        assertFalse(results.any { it.first == "mob_3" })
    }

    @Test
    fun `findReadyToRespawn returns empty list when no mobs ready`() {
        val notReady = RespawnComponent(
            respawnTurns = 1000L,
            lastKilled = 100L,
            originalEntityId = "boss_template"
        )

        repository.save(notReady, "boss_1", "space_1").getOrThrow()

        val results = repository.findReadyToRespawn(500L).getOrThrow()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findReadyToRespawn excludes components with lastKilled = 0`() {
        val notKilled = RespawnComponent(
            respawnTurns = 100L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )

        repository.save(notKilled, "mob_1", "space_1").getOrThrow()

        val results = repository.findReadyToRespawn(1000L).getOrThrow()
        assertTrue(results.isEmpty())
    }

    @Test
    fun `findReadyToRespawn includes component exactly at respawn time`() {
        val exactlyReady = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 500L,
            originalEntityId = "troll_template"
        )

        repository.save(exactlyReady, "mob_1", "space_1").getOrThrow()

        val results = repository.findReadyToRespawn(1000L).getOrThrow()
        assertEquals(1, results.size)
    }

    // === markKilled Tests ===

    @Test
    fun `markKilled updates lastKilled timestamp`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )

        repository.save(component, "goblin_1", "space_1").getOrThrow()
        repository.markKilled("goblin_1", 1234L).getOrThrow()

        val loaded = repository.findByEntityId("goblin_1").getOrThrow()
        assertEquals(1234L, loaded?.first?.lastKilled)
    }

    @Test
    fun `markKilled preserves other fields`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "orc_template"
        )

        repository.save(component, "orc_1", "space_1").getOrThrow()
        repository.markKilled("orc_1", 999L).getOrThrow()

        val loaded = repository.findByEntityId("orc_1").getOrThrow()
        assertEquals(500L, loaded?.first?.respawnTurns)
        assertEquals("orc_template", loaded?.first?.originalEntityId)
    }

    @Test
    fun `markKilled on nonexistent entity succeeds silently`() {
        val result = repository.markKilled("nonexistent", 1000L)
        assertTrue(result.isSuccess)
    }

    // === resetTimer Tests ===

    @Test
    fun `resetTimer sets lastKilled to zero`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 1000L,
            originalEntityId = "troll_template"
        )

        repository.save(component, "troll_1", "space_1").getOrThrow()
        repository.resetTimer("troll_1").getOrThrow()

        val loaded = repository.findByEntityId("troll_1").getOrThrow()
        assertEquals(0L, loaded?.first?.lastKilled)
    }

    @Test
    fun `resetTimer preserves other fields`() {
        val component = RespawnComponent(
            respawnTurns = 600L,
            lastKilled = 1500L,
            originalEntityId = "dragon_template"
        )

        repository.save(component, "dragon_1", "space_1").getOrThrow()
        repository.resetTimer("dragon_1").getOrThrow()

        val loaded = repository.findByEntityId("dragon_1").getOrThrow()
        assertEquals(600L, loaded?.first?.respawnTurns)
        assertEquals("dragon_template", loaded?.first?.originalEntityId)
    }

    // === delete Tests ===

    @Test
    fun `delete removes component`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )

        repository.save(component, "goblin_1", "space_1").getOrThrow()
        repository.delete("goblin_1").getOrThrow()

        val loaded = repository.findByEntityId("goblin_1").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `delete nonexistent entity succeeds`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === getAll Tests ===

    @Test
    fun `getAll returns all components`() {
        val comp1 = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )
        val comp2 = RespawnComponent(
            respawnTurns = 600L,
            lastKilled = 1000L,
            originalEntityId = "orc_template"
        )

        repository.save(comp1, "mob_1", "space_1").getOrThrow()
        repository.save(comp2, "mob_2", "space_2").getOrThrow()

        val all = repository.getAll().getOrThrow()
        assertEquals(2, all.size)
        assertTrue(all.containsKey("mob_1"))
        assertTrue(all.containsKey("mob_2"))
    }

    @Test
    fun `getAll returns empty map when no components exist`() {
        val all = repository.getAll().getOrThrow()
        assertTrue(all.isEmpty())
    }

    // === Edge Cases ===

    @Test
    fun `component with zero respawn turns persists correctly`() {
        val component = RespawnComponent(
            respawnTurns = 0L,
            lastKilled = 0L,
            originalEntityId = "instant_respawn"
        )

        repository.save(component, "mob_1", "space_1").getOrThrow()
        val loaded = repository.findByEntityId("mob_1").getOrThrow()

        assertEquals(0L, loaded?.first?.respawnTurns)
    }

    @Test
    fun `component with very large respawn turns persists correctly`() {
        val component = RespawnComponent(
            respawnTurns = Long.MAX_VALUE,
            lastKilled = 0L,
            originalEntityId = "never_respawn"
        )

        repository.save(component, "mob_1", "space_1").getOrThrow()
        val loaded = repository.findByEntityId("mob_1").getOrThrow()

        assertEquals(Long.MAX_VALUE, loaded?.first?.respawnTurns)
    }

    @Test
    fun `component with very long original entity ID persists correctly`() {
        val longId = "template_" + "A".repeat(1000)
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = longId
        )

        repository.save(component, "mob_1", "space_1").getOrThrow()
        val loaded = repository.findByEntityId("mob_1").getOrThrow()

        assertEquals(longId, loaded?.first?.originalEntityId)
    }

    @Test
    fun `multiple components in different spaces maintain independence`() {
        val comp = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_template"
        )

        repository.save(comp, "mob_1", "space_1").getOrThrow()
        repository.save(comp, "mob_2", "space_2").getOrThrow()
        repository.save(comp, "mob_3", "space_3").getOrThrow()

        repository.markKilled("mob_1", 1000L).getOrThrow()

        val loaded1 = repository.findByEntityId("mob_1").getOrThrow()
        val loaded2 = repository.findByEntityId("mob_2").getOrThrow()

        assertEquals(1000L, loaded1?.first?.lastKilled)
        assertEquals(0L, loaded2?.first?.lastKilled)
    }
}
