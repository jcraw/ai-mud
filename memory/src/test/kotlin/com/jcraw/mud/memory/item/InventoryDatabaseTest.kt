package com.jcraw.mud.memory.item

import com.jcraw.mud.core.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for inventory persistence (database + repository)
 * Tests roundtrip save/load and inventory operations
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InventoryDatabaseTest {
    private lateinit var database: ItemDatabase
    private lateinit var repository: SQLiteInventoryRepository
    private val testDbPath = "test_inventories.db"

    @BeforeAll
    fun setup() {
        database = ItemDatabase(testDbPath)
        repository = SQLiteInventoryRepository(database)
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
    fun `save and load empty inventory`() {
        val inventory = InventoryComponent()

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(0, loaded?.items?.size)
        assertEquals(0, loaded?.equipped?.size)
        assertEquals(0, loaded?.gold)
        assertEquals(50.0, loaded?.capacityWeight)
    }

    @Test
    fun `find nonexistent inventory returns null`() {
        val loaded = repository.findByEntityId("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `save and load inventory with items`() {
        val item1 = ItemInstance(id = "i1", templateId = "sword", quality = 5, quantity = 1)
        val item2 = ItemInstance(id = "i2", templateId = "potion", quality = 5, quantity = 3)
        val inventory = InventoryComponent(
            items = listOf(item1, item2),
            gold = 100,
            capacityWeight = 75.0
        )

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(2, loaded?.items?.size)
        assertEquals(100, loaded?.gold)
        assertEquals(75.0, loaded?.capacityWeight)

        // Verify items are preserved
        val loadedItem1 = loaded?.items?.find { it.id == "i1" }
        assertNotNull(loadedItem1)
        assertEquals("sword", loadedItem1?.templateId)
        assertEquals(5, loadedItem1?.quality)

        val loadedItem2 = loaded?.items?.find { it.id == "i2" }
        assertNotNull(loadedItem2)
        assertEquals(3, loadedItem2?.quantity)
    }

    @Test
    fun `save and load inventory with equipped items`() {
        val sword = ItemInstance(id = "sword1", templateId = "iron_sword", quality = 7, quantity = 1)
        val armor = ItemInstance(id = "armor1", templateId = "plate_armor", quality = 6, quantity = 1)

        val inventory = InventoryComponent(
            items = listOf(sword, armor),
            equipped = mapOf(
                EquipSlot.HANDS_MAIN to sword,
                EquipSlot.CHEST to armor
            ),
            gold = 250,
            capacityWeight = 100.0
        )

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(2, loaded?.items?.size)
        assertEquals(2, loaded?.equipped?.size)

        // Verify equipped items
        val equippedSword = loaded?.equipped?.get(EquipSlot.HANDS_MAIN)
        assertNotNull(equippedSword)
        assertEquals("sword1", equippedSword?.id)
        assertEquals("iron_sword", equippedSword?.templateId)

        val equippedArmor = loaded?.equipped?.get(EquipSlot.CHEST)
        assertNotNull(equippedArmor)
        assertEquals("armor1", equippedArmor?.id)
    }

    @Test
    fun `update inventory overwrites existing`() {
        val original = InventoryComponent(gold = 50, capacityWeight = 50.0)
        repository.save("player1", original).getOrThrow()

        val updated = InventoryComponent(
            items = listOf(ItemInstance(templateId = "sword", quality = 5, quantity = 1)),
            gold = 200,
            capacityWeight = 80.0
        )
        repository.save("player1", updated).getOrThrow()

        val loaded = repository.findByEntityId("player1").getOrThrow()
        assertEquals(1, loaded?.items?.size)
        assertEquals(200, loaded?.gold)
        assertEquals(80.0, loaded?.capacityWeight)
    }

    @Test
    fun `delete inventory removes it`() {
        val inventory = InventoryComponent(gold = 100)
        repository.save("player1", inventory).getOrThrow()

        repository.delete("player1").getOrThrow()

        val loaded = repository.findByEntityId("player1").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `update gold only`() {
        val inventory = InventoryComponent(
            items = listOf(ItemInstance(templateId = "sword", quality = 5, quantity = 1)),
            gold = 100,
            capacityWeight = 75.0
        )
        repository.save("player1", inventory).getOrThrow()

        repository.updateGold("player1", 500).getOrThrow()

        val loaded = repository.findByEntityId("player1").getOrThrow()
        assertEquals(500, loaded?.gold)
        assertEquals(1, loaded?.items?.size) // Items should be unchanged
        assertEquals(75.0, loaded?.capacityWeight)
    }

    @Test
    fun `update capacity only`() {
        val inventory = InventoryComponent(
            items = listOf(ItemInstance(templateId = "sword", quality = 5, quantity = 1)),
            gold = 100,
            capacityWeight = 50.0
        )
        repository.save("player1", inventory).getOrThrow()

        repository.updateCapacity("player1", 150.0).getOrThrow()

        val loaded = repository.findByEntityId("player1").getOrThrow()
        assertEquals(150.0, loaded?.capacityWeight)
        assertEquals(100, loaded?.gold) // Gold should be unchanged
        assertEquals(1, loaded?.items?.size)
    }

    @Test
    fun `update gold for nonexistent inventory fails`() {
        val result = repository.updateGold("nonexistent", 100)
        assertTrue(result.isFailure)
    }

    @Test
    fun `update capacity for nonexistent inventory fails`() {
        val result = repository.updateCapacity("nonexistent", 100.0)
        assertTrue(result.isFailure)
    }

    @Test
    fun `find all inventories returns all`() {
        repository.save("player1", InventoryComponent(gold = 100)).getOrThrow()
        repository.save("player2", InventoryComponent(gold = 200)).getOrThrow()
        repository.save("npc1", InventoryComponent(gold = 50)).getOrThrow()

        val allInventories = repository.findAll().getOrThrow()
        assertEquals(3, allInventories.size)
        assertTrue(allInventories.containsKey("player1"))
        assertTrue(allInventories.containsKey("player2"))
        assertTrue(allInventories.containsKey("npc1"))

        assertEquals(100, allInventories["player1"]?.gold)
        assertEquals(200, allInventories["player2"]?.gold)
        assertEquals(50, allInventories["npc1"]?.gold)
    }

    @Test
    fun `inventory with many items persists correctly`() {
        val items = (1..20).map { i ->
            ItemInstance(
                id = "item$i",
                templateId = "template$i",
                quality = (i % 10) + 1,
                quantity = i
            )
        }

        val inventory = InventoryComponent(
            items = items,
            gold = 1000,
            capacityWeight = 200.0
        )

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        assertEquals(20, loaded?.items?.size)
        loaded?.items?.forEachIndexed { index, item ->
            assertEquals("item${index + 1}", item.id)
            assertEquals(index + 1, item.quantity)
        }
    }

    @Test
    fun `inventory with all equipment slots filled`() {
        val items = listOf(
            ItemInstance(id = "mainhand", templateId = "sword", quality = 5, quantity = 1),
            ItemInstance(id = "offhand", templateId = "shield", quality = 5, quantity = 1),
            ItemInstance(id = "head", templateId = "helmet", quality = 5, quantity = 1),
            ItemInstance(id = "chest", templateId = "armor", quality = 5, quantity = 1),
            ItemInstance(id = "legs", templateId = "pants", quality = 5, quantity = 1),
            ItemInstance(id = "feet", templateId = "boots", quality = 5, quantity = 1),
            ItemInstance(id = "back", templateId = "cloak", quality = 5, quantity = 1),
            ItemInstance(id = "acc1", templateId = "ring1", quality = 5, quantity = 1),
            ItemInstance(id = "acc2", templateId = "ring2", quality = 5, quantity = 1),
            ItemInstance(id = "acc3", templateId = "amulet", quality = 5, quantity = 1),
            ItemInstance(id = "acc4", templateId = "bracelet", quality = 5, quantity = 1)
        )

        val equipped = mapOf(
            EquipSlot.HANDS_MAIN to items[0],
            EquipSlot.HANDS_OFF to items[1],
            EquipSlot.HEAD to items[2],
            EquipSlot.CHEST to items[3],
            EquipSlot.LEGS to items[4],
            EquipSlot.FEET to items[5],
            EquipSlot.BACK to items[6],
            EquipSlot.ACCESSORY_1 to items[7],
            EquipSlot.ACCESSORY_2 to items[8],
            EquipSlot.ACCESSORY_3 to items[9],
            EquipSlot.ACCESSORY_4 to items[10]
        )

        val inventory = InventoryComponent(
            items = items,
            equipped = equipped,
            gold = 500
        )

        repository.save("fully_equipped_player", inventory).getOrThrow()
        val loaded = repository.findByEntityId("fully_equipped_player").getOrThrow()

        assertEquals(11, loaded?.items?.size)
        assertEquals(11, loaded?.equipped?.size)

        // Verify all slots are filled
        assertNotNull(loaded?.equipped?.get(EquipSlot.HANDS_MAIN))
        assertNotNull(loaded?.equipped?.get(EquipSlot.HANDS_OFF))
        assertNotNull(loaded?.equipped?.get(EquipSlot.HEAD))
        assertNotNull(loaded?.equipped?.get(EquipSlot.CHEST))
        assertNotNull(loaded?.equipped?.get(EquipSlot.LEGS))
        assertNotNull(loaded?.equipped?.get(EquipSlot.FEET))
        assertNotNull(loaded?.equipped?.get(EquipSlot.BACK))
        assertNotNull(loaded?.equipped?.get(EquipSlot.ACCESSORY_1))
        assertNotNull(loaded?.equipped?.get(EquipSlot.ACCESSORY_2))
        assertNotNull(loaded?.equipped?.get(EquipSlot.ACCESSORY_3))
        assertNotNull(loaded?.equipped?.get(EquipSlot.ACCESSORY_4))
    }

    @Test
    fun `inventory with two-handed weapon persists correctly`() {
        val greatsword = ItemInstance(
            id = "greatsword1",
            templateId = "greatsword",
            quality = 8,
            quantity = 1
        )

        val inventory = InventoryComponent(
            items = listOf(greatsword),
            equipped = mapOf(EquipSlot.HANDS_BOTH to greatsword),
            gold = 100
        )

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        assertEquals(1, loaded?.equipped?.size)
        assertNotNull(loaded?.equipped?.get(EquipSlot.HANDS_BOTH))
        assertEquals("greatsword1", loaded?.equipped?.get(EquipSlot.HANDS_BOTH)?.id)
    }

    @Test
    fun `inventory with items having charges persists correctly`() {
        val wand = ItemInstance(
            id = "wand1",
            templateId = "magic_wand",
            quality = 5,
            charges = 20,
            quantity = 1
        )

        val inventory = InventoryComponent(items = listOf(wand))

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        val loadedWand = loaded?.items?.first()
        assertEquals(20, loadedWand?.charges)
    }

    @Test
    fun `inventory with high quality items persists correctly`() {
        val legendaryItem = ItemInstance(
            id = "legendary1",
            templateId = "dragon_sword",
            quality = 10,
            quantity = 1
        )

        val poorItem = ItemInstance(
            id = "poor1",
            templateId = "rusty_dagger",
            quality = 1,
            quantity = 1
        )

        val inventory = InventoryComponent(items = listOf(legendaryItem, poorItem))

        repository.save("player1", inventory).getOrThrow()
        val loaded = repository.findByEntityId("player1").getOrThrow()

        val legendary = loaded?.items?.find { it.id == "legendary1" }
        assertEquals(10, legendary?.quality)

        val poor = loaded?.items?.find { it.id == "poor1" }
        assertEquals(1, poor?.quality)
    }

    @Test
    fun `inventory with maximum gold persists correctly`() {
        val inventory = InventoryComponent(gold = Int.MAX_VALUE)

        repository.save("rich_player", inventory).getOrThrow()
        val loaded = repository.findByEntityId("rich_player").getOrThrow()

        assertEquals(Int.MAX_VALUE, loaded?.gold)
    }

    @Test
    fun `inventory with large capacity persists correctly`() {
        val inventory = InventoryComponent(capacityWeight = 1000.0)

        repository.save("strong_player", inventory).getOrThrow()
        val loaded = repository.findByEntityId("strong_player").getOrThrow()

        assertEquals(1000.0, loaded?.capacityWeight)
    }

    @Test
    fun `multiple entities with inventories persist independently`() {
        val inv1 = InventoryComponent(
            items = listOf(ItemInstance(templateId = "sword", quality = 5, quantity = 1)),
            gold = 100
        )
        val inv2 = InventoryComponent(
            items = listOf(ItemInstance(templateId = "potion", quality = 5, quantity = 5)),
            gold = 50
        )
        val inv3 = InventoryComponent(gold = 1000, capacityWeight = 200.0)

        repository.save("player1", inv1).getOrThrow()
        repository.save("player2", inv2).getOrThrow()
        repository.save("merchant1", inv3).getOrThrow()

        val loaded1 = repository.findByEntityId("player1").getOrThrow()
        val loaded2 = repository.findByEntityId("player2").getOrThrow()
        val loaded3 = repository.findByEntityId("merchant1").getOrThrow()

        assertEquals(100, loaded1?.gold)
        assertEquals(1, loaded1?.items?.size)
        assertEquals("sword", loaded1?.items?.first()?.templateId)

        assertEquals(50, loaded2?.gold)
        assertEquals(1, loaded2?.items?.size)
        assertEquals(5, loaded2?.items?.first()?.quantity)

        assertEquals(1000, loaded3?.gold)
        assertEquals(0, loaded3?.items?.size)
        assertEquals(200.0, loaded3?.capacityWeight)
    }
}
