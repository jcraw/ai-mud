package com.jcraw.mud.memory.world

import com.jcraw.mud.core.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for SQLiteCorpseRepository
 * Tests save/load, queries by player/space, decay mechanics, and JSON serialization
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteCorpseRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteCorpseRepository
    private val testDbPath = "test_corpses.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteCorpseRepository(database)
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

    // === Helper Functions ===

    private fun createSampleInventory(): InventoryComponent {
        return InventoryComponent(
            items = listOf(
                ItemInstance(id = "item1", templateId = "potion_health", quantity = 3),
                ItemInstance(id = "item2", templateId = "sword_iron", quality = 7)
            ),
            gold = 100,
            capacityWeight = 50.0
        )
    }

    private fun createSampleEquipment(): List<ItemInstance> {
        return listOf(
            ItemInstance(id = "equip1", templateId = "armor_leather", quality = 6),
            ItemInstance(id = "equip2", templateId = "sword_iron", quality = 7)
        )
    }

    private fun createSampleCorpse(
        id: String = "corpse_1",
        playerId: String = "player1",
        spaceId: String = "space_1",
        decayTimer: Long = 2000L,
        looted: Boolean = false
    ): CorpseData {
        return CorpseData(
            id = id,
            playerId = playerId,
            spaceId = spaceId,
            inventory = createSampleInventory(),
            equipment = createSampleEquipment(),
            gold = 150,
            decayTimer = decayTimer,
            looted = looted
        )
    }

    // === Save/Load Tests ===

    @Test
    fun `save and find corpse by ID`() {
        val corpse = createSampleCorpse()

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(corpse.id, loaded?.id)
        assertEquals(corpse.playerId, loaded?.playerId)
        assertEquals(corpse.spaceId, loaded?.spaceId)
        assertEquals(corpse.gold, loaded?.gold)
        assertEquals(corpse.decayTimer, loaded?.decayTimer)
        assertEquals(corpse.looted, loaded?.looted)
    }

    @Test
    fun `find nonexistent corpse returns null`() {
        val loaded = repository.findById("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `update corpse overwrites existing`() {
        val original = createSampleCorpse()
        repository.save(original).getOrThrow()

        val updated = original.copy(gold = 500, looted = true)
        repository.save(updated).getOrThrow()

        val loaded = repository.findById("corpse_1").getOrThrow()
        assertEquals(500, loaded?.gold)
        assertTrue(loaded?.looted == true)
    }

    @Test
    fun `corpse with empty inventory persists correctly`() {
        val corpse = createSampleCorpse().copy(
            inventory = InventoryComponent(items = emptyList(), gold = 0)
        )

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertTrue(loaded?.inventory?.items?.isEmpty() == true)
    }

    @Test
    fun `corpse with empty equipment persists correctly`() {
        val corpse = createSampleCorpse().copy(equipment = emptyList())

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertTrue(loaded?.equipment?.isEmpty() == true)
    }

    // === findByPlayerId Tests ===

    @Test
    fun `findByPlayerId returns all player corpses`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", playerId = "player1", decayTimer = 1000L)
        val corpse2 = createSampleCorpse(id = "corpse_2", playerId = "player1", decayTimer = 2000L)
        val corpse3 = createSampleCorpse(id = "corpse_3", playerId = "player2", decayTimer = 1500L)

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()
        repository.save(corpse3).getOrThrow()

        val results = repository.findByPlayerId("player1").getOrThrow()
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "corpse_1" })
        assertTrue(results.any { it.id == "corpse_2" })
    }

    @Test
    fun `findByPlayerId returns corpses ordered by decay timer`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", playerId = "player1", decayTimer = 2000L)
        val corpse2 = createSampleCorpse(id = "corpse_2", playerId = "player1", decayTimer = 1000L)
        val corpse3 = createSampleCorpse(id = "corpse_3", playerId = "player1", decayTimer = 1500L)

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()
        repository.save(corpse3).getOrThrow()

        val results = repository.findByPlayerId("player1").getOrThrow()
        assertEquals(3, results.size)
        assertEquals("corpse_2", results[0].id) // 1000L - oldest first
        assertEquals("corpse_3", results[1].id) // 1500L
        assertEquals("corpse_1", results[2].id) // 2000L
    }

    @Test
    fun `findByPlayerId returns empty list for player with no corpses`() {
        val results = repository.findByPlayerId("nonexistent").getOrThrow()
        assertTrue(results.isEmpty())
    }

    // === findBySpaceId Tests ===

    @Test
    fun `findBySpaceId returns all corpses in space`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", spaceId = "space_1")
        val corpse2 = createSampleCorpse(id = "corpse_2", spaceId = "space_1")
        val corpse3 = createSampleCorpse(id = "corpse_3", spaceId = "space_2")

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()
        repository.save(corpse3).getOrThrow()

        val results = repository.findBySpaceId("space_1").getOrThrow()
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "corpse_1" })
        assertTrue(results.any { it.id == "corpse_2" })
    }

    @Test
    fun `findBySpaceId returns corpses ordered by decay timer`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", spaceId = "space_1", decayTimer = 3000L)
        val corpse2 = createSampleCorpse(id = "corpse_2", spaceId = "space_1", decayTimer = 1000L)

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()

        val results = repository.findBySpaceId("space_1").getOrThrow()
        assertEquals("corpse_2", results[0].id) // 1000L first
        assertEquals("corpse_1", results[1].id) // 3000L second
    }

    @Test
    fun `findBySpaceId returns empty list for empty space`() {
        val results = repository.findBySpaceId("nonexistent").getOrThrow()
        assertTrue(results.isEmpty())
    }

    // === findDecayed Tests ===

    @Test
    fun `findDecayed returns corpses past decay time`() {
        val decayed1 = createSampleCorpse(id = "corpse_1", decayTimer = 500L)
        val decayed2 = createSampleCorpse(id = "corpse_2", decayTimer = 800L)
        val notDecayed = createSampleCorpse(id = "corpse_3", decayTimer = 1500L)

        repository.save(decayed1).getOrThrow()
        repository.save(decayed2).getOrThrow()
        repository.save(notDecayed).getOrThrow()

        val results = repository.findDecayed(1000L).getOrThrow()
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "corpse_1" })
        assertTrue(results.any { it.id == "corpse_2" })
    }

    @Test
    fun `findDecayed includes corpses exactly at decay time`() {
        val exactlyDecayed = createSampleCorpse(id = "corpse_1", decayTimer = 1000L)

        repository.save(exactlyDecayed).getOrThrow()

        val results = repository.findDecayed(1000L).getOrThrow()
        assertEquals(1, results.size)
    }

    @Test
    fun `findDecayed returns empty list when no corpses decayed`() {
        val notDecayed = createSampleCorpse(id = "corpse_1", decayTimer = 2000L)

        repository.save(notDecayed).getOrThrow()

        val results = repository.findDecayed(1000L).getOrThrow()
        assertTrue(results.isEmpty())
    }

    // === markLooted Tests ===

    @Test
    fun `markLooted sets looted flag to true`() {
        val corpse = createSampleCorpse(looted = false)

        repository.save(corpse).getOrThrow()
        repository.markLooted("corpse_1").getOrThrow()

        val loaded = repository.findById("corpse_1").getOrThrow()
        assertTrue(loaded?.looted == true)
    }

    @Test
    fun `markLooted preserves other fields`() {
        val corpse = createSampleCorpse(looted = false)

        repository.save(corpse).getOrThrow()
        repository.markLooted("corpse_1").getOrThrow()

        val loaded = repository.findById("corpse_1").getOrThrow()
        assertEquals(corpse.id, loaded?.id)
        assertEquals(corpse.playerId, loaded?.playerId)
        assertEquals(corpse.spaceId, loaded?.spaceId)
        assertEquals(corpse.gold, loaded?.gold)
    }

    @Test
    fun `markLooted on nonexistent corpse succeeds silently`() {
        val result = repository.markLooted("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === delete Tests ===

    @Test
    fun `delete removes corpse`() {
        val corpse = createSampleCorpse()

        repository.save(corpse).getOrThrow()
        repository.delete("corpse_1").getOrThrow()

        val loaded = repository.findById("corpse_1").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `delete nonexistent corpse succeeds`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === deleteBySpaceId Tests ===

    @Test
    fun `deleteBySpaceId removes all corpses in space`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", spaceId = "space_1")
        val corpse2 = createSampleCorpse(id = "corpse_2", spaceId = "space_1")
        val corpse3 = createSampleCorpse(id = "corpse_3", spaceId = "space_2")

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()
        repository.save(corpse3).getOrThrow()

        repository.deleteBySpaceId("space_1").getOrThrow()

        val space1Corpses = repository.findBySpaceId("space_1").getOrThrow()
        val space2Corpses = repository.findBySpaceId("space_2").getOrThrow()

        assertTrue(space1Corpses.isEmpty())
        assertEquals(1, space2Corpses.size)
    }

    @Test
    fun `deleteBySpaceId on empty space succeeds`() {
        val result = repository.deleteBySpaceId("nonexistent")
        assertTrue(result.isSuccess)
    }

    // === getAll Tests ===

    @Test
    fun `getAll returns all corpses`() {
        val corpse1 = createSampleCorpse(id = "corpse_1")
        val corpse2 = createSampleCorpse(id = "corpse_2")

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()

        val all = repository.getAll().getOrThrow()
        assertEquals(2, all.size)
        assertTrue(all.containsKey("corpse_1"))
        assertTrue(all.containsKey("corpse_2"))
    }

    @Test
    fun `getAll returns empty map when no corpses exist`() {
        val all = repository.getAll().getOrThrow()
        assertTrue(all.isEmpty())
    }

    // === JSON Serialization Tests ===

    @Test
    fun `inventory with multiple items serializes correctly`() {
        val inventory = InventoryComponent(
            items = listOf(
                ItemInstance(id = "item1", templateId = "sword", quality = 10),
                ItemInstance(id = "item2", templateId = "potion", quantity = 5),
                ItemInstance(id = "item3", templateId = "scroll", charges = 3)
            ),
            gold = 250
        )
        val corpse = createSampleCorpse().copy(inventory = inventory)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(3, loaded?.inventory?.items?.size)
        assertEquals(250, loaded?.inventory?.gold)
    }

    @Test
    fun `equipment list serializes correctly`() {
        val equipment = listOf(
            ItemInstance(id = "equip1", templateId = "helm_steel", quality = 8),
            ItemInstance(id = "equip2", templateId = "boots_leather", quality = 6),
            ItemInstance(id = "equip3", templateId = "ring_power", quality = 10)
        )
        val corpse = createSampleCorpse().copy(equipment = equipment)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(3, loaded?.equipment?.size)
        assertTrue(loaded?.equipment?.any { it.templateId == "helm_steel" } == true)
    }

    // === Edge Cases ===

    @Test
    fun `corpse with zero gold persists correctly`() {
        val corpse = createSampleCorpse().copy(gold = 0)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(0, loaded?.gold)
    }

    @Test
    fun `corpse with very large gold amount persists correctly`() {
        val corpse = createSampleCorpse().copy(gold = Int.MAX_VALUE)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(Int.MAX_VALUE, loaded?.gold)
    }

    @Test
    fun `corpse with zero decay timer persists correctly`() {
        val corpse = createSampleCorpse().copy(decayTimer = 0L)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(0L, loaded?.decayTimer)
    }

    @Test
    fun `corpse with very large decay timer persists correctly`() {
        val corpse = createSampleCorpse().copy(decayTimer = Long.MAX_VALUE)

        repository.save(corpse).getOrThrow()
        val loaded = repository.findById("corpse_1").getOrThrow()

        assertEquals(Long.MAX_VALUE, loaded?.decayTimer)
    }

    @Test
    fun `multiple corpses for same player maintain independence`() {
        val corpse1 = createSampleCorpse(id = "corpse_1", playerId = "player1").copy(gold = 100)
        val corpse2 = createSampleCorpse(id = "corpse_2", playerId = "player1").copy(gold = 200)

        repository.save(corpse1).getOrThrow()
        repository.save(corpse2).getOrThrow()

        repository.markLooted("corpse_1").getOrThrow()

        val loaded1 = repository.findById("corpse_1").getOrThrow()
        val loaded2 = repository.findById("corpse_2").getOrThrow()

        assertTrue(loaded1?.looted == true)
        assertFalse(loaded2?.looted == true)
    }
}
