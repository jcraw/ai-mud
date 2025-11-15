package com.jcraw.mud.memory.world

import com.jcraw.mud.core.Pedestal
import com.jcraw.mud.core.PedestalState
import com.jcraw.mud.core.TreasureRoomComponent
import com.jcraw.mud.core.TreasureRoomType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for TreasureRoomRepository
 * Tests database persistence, retrieval, updates, and state management
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreasureRoomRepositoryTest {
    private lateinit var database: WorldDatabase
    private lateinit var repository: SQLiteTreasureRoomRepository
    private val testDbPath = "test_treasure_rooms.db"

    @BeforeAll
    fun setup() {
        database = WorldDatabase(testDbPath)
        repository = SQLiteTreasureRoomRepository(database)
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

    // Test fixtures
    private fun createStarterTreasureRoom(): TreasureRoomComponent {
        return TreasureRoomComponent(
            roomType = TreasureRoomType.STARTER,
            pedestals = listOf(
                Pedestal("veterans_longsword", PedestalState.AVAILABLE, "ancient stone altar", 0),
                Pedestal("shadowcloak", PedestalState.AVAILABLE, "shadowed stone pedestal", 1),
                Pedestal("apprentice_staff", PedestalState.AVAILABLE, "glowing stone shrine", 2),
                Pedestal("enchanted_satchel", PedestalState.AVAILABLE, "sturdy stone stand", 3),
                Pedestal("spellblade", PedestalState.AVAILABLE, "ornate stone dais", 4)
            ),
            currentlyTakenItem = null,
            hasBeenLooted = false,
            biomeTheme = "ancient_abyss"
        )
    }

    // ========== Schema Tests ==========

    @Test
    fun `treasure_rooms table exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getTables(null, null, "treasure_rooms", null)
        assertTrue(rs.next())
    }

    @Test
    fun `pedestals table exists`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getTables(null, null, "pedestals", null)
        assertTrue(rs.next())
    }

    @Test
    fun `treasure_rooms has correct columns`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getColumns(null, null, "treasure_rooms", null)

        val columns = mutableListOf<String>()
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME"))
        }

        assertTrue(columns.contains("space_id"))
        assertTrue(columns.contains("room_type"))
        assertTrue(columns.contains("biome_theme"))
        assertTrue(columns.contains("currently_taken_item"))
        assertTrue(columns.contains("has_been_looted"))
    }

    @Test
    fun `pedestals has correct columns`() {
        val conn = database.getConnection()
        val meta = conn.metaData
        val rs = meta.getColumns(null, null, "pedestals", null)

        val columns = mutableListOf<String>()
        while (rs.next()) {
            columns.add(rs.getString("COLUMN_NAME"))
        }

        assertTrue(columns.contains("id"))
        assertTrue(columns.contains("treasure_room_id"))
        assertTrue(columns.contains("item_template_id"))
        assertTrue(columns.contains("state"))
        assertTrue(columns.contains("pedestal_index"))
        assertTrue(columns.contains("theme_description"))
    }

    // ========== Save Tests ==========

    @Test
    fun `save treasure room succeeds`() {
        val room = createStarterTreasureRoom()
        val result = repository.save(room, "space_123")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `save treasure room persists data`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)
        assertEquals(TreasureRoomType.STARTER, loaded!!.roomType)
        assertEquals("ancient_abyss", loaded.biomeTheme)
        assertNull(loaded.currentlyTakenItem)
        assertFalse(loaded.hasBeenLooted)
        assertEquals(5, loaded.pedestals.size)
    }

    @Test
    fun `save treasure room persists pedestals in order`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)

        assertEquals("veterans_longsword", loaded!!.pedestals[0].itemTemplateId)
        assertEquals("shadowcloak", loaded.pedestals[1].itemTemplateId)
        assertEquals("apprentice_staff", loaded.pedestals[2].itemTemplateId)
        assertEquals("enchanted_satchel", loaded.pedestals[3].itemTemplateId)
        assertEquals("spellblade", loaded.pedestals[4].itemTemplateId)
    }

    @Test
    fun `save overwrites existing treasure room`() {
        val room1 = createStarterTreasureRoom()
        repository.save(room1, "space_123")

        val room2 = room1.copy(
            currentlyTakenItem = "veterans_longsword",
            pedestals = room1.lockPedestalsExcept("veterans_longsword")
        )
        repository.save(room2, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)
        assertEquals("veterans_longsword", loaded!!.currentlyTakenItem)
    }

    // Helper method to expose lockPedestalsExcept for testing
    private fun TreasureRoomComponent.lockPedestalsExcept(exceptItemId: String): List<Pedestal> {
        return this.takeItem(exceptItemId).pedestals
    }

    @Test
    fun `save replaces all pedestals`() {
        val room1 = createStarterTreasureRoom()
        repository.save(room1, "space_123")

        // Modify pedestals
        val room2 = room1.copy(
            pedestals = listOf(
                Pedestal("new_item_1", PedestalState.EMPTY, "new altar 1", 0),
                Pedestal("new_item_2", PedestalState.EMPTY, "new altar 2", 1)
            )
        )
        repository.save(room2, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)
        assertEquals(2, loaded!!.pedestals.size)
        assertEquals("new_item_1", loaded.pedestals[0].itemTemplateId)
        assertEquals("new_item_2", loaded.pedestals[1].itemTemplateId)
    }

    // ========== Find Tests ==========

    @Test
    fun `findBySpaceId returns null for nonexistent space`() {
        val loaded = repository.findBySpaceId("nonexistent").getOrNull()
        assertNull(loaded)
    }

    @Test
    fun `findBySpaceId returns correct room`() {
        val room1 = createStarterTreasureRoom()
        val room2 = createStarterTreasureRoom().copy(biomeTheme = "magma_cave")

        repository.save(room1, "space_123")
        repository.save(room2, "space_456")

        val loaded = repository.findBySpaceId("space_456").getOrNull()
        assertNotNull(loaded)
        assertEquals("magma_cave", loaded!!.biomeTheme)
    }

    // ========== Update Currently Taken Item Tests ==========

    @Test
    fun `updateCurrentlyTakenItem sets item`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val result = repository.updateCurrentlyTakenItem("space_123", "veterans_longsword")
        assertTrue(result.isSuccess)

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertEquals("veterans_longsword", loaded!!.currentlyTakenItem)
    }

    @Test
    fun `updateCurrentlyTakenItem clears item with null`() {
        val room = createStarterTreasureRoom().copy(currentlyTakenItem = "veterans_longsword")
        repository.save(room, "space_123")

        val result = repository.updateCurrentlyTakenItem("space_123", null)
        assertTrue(result.isSuccess)

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNull(loaded!!.currentlyTakenItem)
    }

    @Test
    fun `updateCurrentlyTakenItem fails for nonexistent room`() {
        val result = repository.updateCurrentlyTakenItem("nonexistent", "some_item")
        assertTrue(result.isFailure)
    }

    // ========== Mark As Looted Tests ==========

    @Test
    fun `markAsLooted sets flag`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val result = repository.markAsLooted("space_123")
        assertTrue(result.isSuccess)

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertTrue(loaded!!.hasBeenLooted)
    }

    @Test
    fun `markAsLooted sets all pedestals to EMPTY`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        repository.markAsLooted("space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertTrue(loaded!!.pedestals.all { it.state == PedestalState.EMPTY })
        assertEquals(5, loaded.pedestals.size)
    }

    @Test
    fun `markAsLooted fails for nonexistent room`() {
        val result = repository.markAsLooted("nonexistent")
        assertTrue(result.isFailure)
    }

    // ========== Update Pedestal State Tests ==========

    @Test
    fun `updatePedestalState changes state`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val result = repository.updatePedestalState("space_123", "veterans_longsword", "LOCKED")
        assertTrue(result.isSuccess)

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        val pedestal = loaded!!.pedestals.find { it.itemTemplateId == "veterans_longsword" }
        assertEquals(PedestalState.LOCKED, pedestal?.state)
    }

    @Test
    fun `updatePedestalState fails for nonexistent item`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val result = repository.updatePedestalState("space_123", "nonexistent_item", "LOCKED")
        assertTrue(result.isFailure)
    }

    @Test
    fun `updatePedestalState fails for nonexistent room`() {
        val result = repository.updatePedestalState("nonexistent", "veterans_longsword", "LOCKED")
        assertTrue(result.isFailure)
    }

    // ========== Delete Tests ==========

    @Test
    fun `delete removes treasure room`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val result = repository.delete("space_123")
        assertTrue(result.isSuccess)

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNull(loaded)
    }

    @Test
    fun `delete removes all pedestals`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        repository.delete("space_123")

        // Check pedestals table directly
        val conn = database.getConnection()
        val sql = "SELECT COUNT(*) FROM pedestals WHERE treasure_room_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, "space_123")
            val rs = stmt.executeQuery()
            rs.next()
            assertEquals(0, rs.getInt(1))
        }
    }

    @Test
    fun `delete succeeds even for nonexistent room`() {
        val result = repository.delete("nonexistent")
        assertTrue(result.isSuccess) // Delete is idempotent
    }

    // ========== Find All Tests ==========

    @Test
    fun `findAll returns empty list initially`() {
        val all = repository.findAll().getOrNull()
        assertNotNull(all)
        assertTrue(all!!.isEmpty())
    }

    @Test
    fun `findAll returns all treasure rooms`() {
        val room1 = createStarterTreasureRoom()
        val room2 = createStarterTreasureRoom().copy(biomeTheme = "magma_cave")
        val room3 = createStarterTreasureRoom().copy(biomeTheme = "frozen_depths")

        repository.save(room1, "space_1")
        repository.save(room2, "space_2")
        repository.save(room3, "space_3")

        val all = repository.findAll().getOrNull()
        assertNotNull(all)
        assertEquals(3, all!!.size)

        val themes = all.map { it.second.biomeTheme }.toSet()
        assertTrue(themes.contains("ancient_abyss"))
        assertTrue(themes.contains("magma_cave"))
        assertTrue(themes.contains("frozen_depths"))
    }

    // ========== Roundtrip Tests ==========

    @Test
    fun `roundtrip with item taken`() {
        val room = createStarterTreasureRoom().copy(
            currentlyTakenItem = "shadowcloak"
        )
        repository.save(room, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)
        assertEquals("shadowcloak", loaded!!.currentlyTakenItem)
    }

    @Test
    fun `roundtrip with looted room`() {
        val room = createStarterTreasureRoom().copy(
            hasBeenLooted = true,
            pedestals = createStarterTreasureRoom().pedestals.map { it.copy(state = PedestalState.EMPTY) }
        )
        repository.save(room, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)
        assertTrue(loaded!!.hasBeenLooted)
        assertTrue(loaded.pedestals.all { it.state == PedestalState.EMPTY })
    }

    @Test
    fun `roundtrip preserves all pedestal data`() {
        val room = createStarterTreasureRoom()
        repository.save(room, "space_123")

        val loaded = repository.findBySpaceId("space_123").getOrNull()
        assertNotNull(loaded)

        // Check all pedestals
        for (i in room.pedestals.indices) {
            val original = room.pedestals[i]
            val loadedPedestal = loaded!!.pedestals[i]

            assertEquals(original.itemTemplateId, loadedPedestal.itemTemplateId)
            assertEquals(original.state, loadedPedestal.state)
            assertEquals(original.themeDescription, loadedPedestal.themeDescription)
            assertEquals(original.pedestalIndex, loadedPedestal.pedestalIndex)
        }
    }
}
