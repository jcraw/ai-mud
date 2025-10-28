package com.jcraw.mud.memory.item

import com.jcraw.mud.core.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * Integration tests for item persistence (database + repository)
 * Tests roundtrip save/load, queries, and bulk operations
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ItemDatabaseTest {
    private lateinit var database: ItemDatabase
    private lateinit var repository: SQLiteItemRepository
    private val testDbPath = "test_items.db"
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeAll
    fun setup() {
        database = ItemDatabase(testDbPath)
        repository = SQLiteItemRepository(database)
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

    // === Template Tests ===

    @Test
    fun `save and load template`() {
        val template = ItemTemplate(
            id = "test_sword",
            name = "Test Sword",
            type = ItemType.WEAPON,
            tags = listOf("sharp", "metal"),
            properties = mapOf("damage" to "10", "weight" to "2.5"),
            rarity = Rarity.COMMON,
            description = "A test sword",
            equipSlot = EquipSlot.HANDS_MAIN
        )

        repository.saveTemplate(template).getOrThrow()
        val loaded = repository.findTemplateById("test_sword").getOrThrow()

        assertNotNull(loaded)
        assertEquals(template, loaded)
    }

    @Test
    fun `find nonexistent template returns null`() {
        val loaded = repository.findTemplateById("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `save multiple templates with bulk operation`() {
        val templates = listOf(
            ItemTemplate(
                id = "sword1",
                name = "Sword 1",
                type = ItemType.WEAPON,
                description = "Sword 1",
                rarity = Rarity.COMMON
            ),
            ItemTemplate(
                id = "sword2",
                name = "Sword 2",
                type = ItemType.WEAPON,
                description = "Sword 2",
                rarity = Rarity.UNCOMMON
            ),
            ItemTemplate(
                id = "potion1",
                name = "Potion 1",
                type = ItemType.CONSUMABLE,
                description = "Potion 1",
                rarity = Rarity.COMMON
            )
        )

        repository.saveTemplates(templates).getOrThrow()
        val allTemplates = repository.findAllTemplates().getOrThrow()

        assertEquals(3, allTemplates.size)
        assertTrue(allTemplates.containsKey("sword1"))
        assertTrue(allTemplates.containsKey("sword2"))
        assertTrue(allTemplates.containsKey("potion1"))
    }

    @Test
    fun `find templates by type`() {
        repository.saveTemplates(
            listOf(
                ItemTemplate(id = "sword", name = "Sword", type = ItemType.WEAPON, description = "A sword"),
                ItemTemplate(id = "armor", name = "Armor", type = ItemType.ARMOR, description = "Armor"),
                ItemTemplate(id = "potion", name = "Potion", type = ItemType.CONSUMABLE, description = "Potion")
            )
        ).getOrThrow()

        val weapons = repository.findTemplatesByType(ItemType.WEAPON).getOrThrow()
        assertEquals(1, weapons.size)
        assertEquals("sword", weapons[0].id)

        val consumables = repository.findTemplatesByType(ItemType.CONSUMABLE).getOrThrow()
        assertEquals(1, consumables.size)
        assertEquals("potion", consumables[0].id)
    }

    @Test
    fun `find templates by rarity`() {
        repository.saveTemplates(
            listOf(
                ItemTemplate(
                    id = "common_item",
                    name = "Common",
                    type = ItemType.WEAPON,
                    description = "Common",
                    rarity = Rarity.COMMON
                ),
                ItemTemplate(
                    id = "rare_item",
                    name = "Rare",
                    type = ItemType.WEAPON,
                    description = "Rare",
                    rarity = Rarity.RARE
                ),
                ItemTemplate(
                    id = "legendary_item",
                    name = "Legendary",
                    type = ItemType.WEAPON,
                    description = "Legendary",
                    rarity = Rarity.LEGENDARY
                )
            )
        ).getOrThrow()

        val rareItems = repository.findTemplatesByRarity(Rarity.RARE).getOrThrow()
        assertEquals(1, rareItems.size)
        assertEquals("rare_item", rareItems[0].id)
    }

    @Test
    fun `update template overwrites existing`() {
        val original = ItemTemplate(
            id = "test_item",
            name = "Original Name",
            type = ItemType.WEAPON,
            description = "Original"
        )
        repository.saveTemplate(original).getOrThrow()

        val updated = original.copy(name = "Updated Name", description = "Updated")
        repository.saveTemplate(updated).getOrThrow()

        val loaded = repository.findTemplateById("test_item").getOrThrow()
        assertEquals("Updated Name", loaded?.name)
        assertEquals("Updated", loaded?.description)
    }

    @Test
    fun `delete template removes it`() {
        val template = ItemTemplate(
            id = "to_delete",
            name = "Delete Me",
            type = ItemType.WEAPON,
            description = "Will be deleted"
        )
        repository.saveTemplate(template).getOrThrow()

        repository.deleteTemplate("to_delete").getOrThrow()

        val loaded = repository.findTemplateById("to_delete").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `template with null equipSlot persists correctly`() {
        val template = ItemTemplate(
            id = "consumable",
            name = "Potion",
            type = ItemType.CONSUMABLE,
            description = "Not equippable",
            equipSlot = null
        )

        repository.saveTemplate(template).getOrThrow()
        val loaded = repository.findTemplateById("consumable").getOrThrow()

        assertNotNull(loaded)
        assertNull(loaded?.equipSlot)
    }

    @Test
    fun `template with complex properties persists correctly`() {
        val template = ItemTemplate(
            id = "complex_item",
            name = "Complex Item",
            type = ItemType.WEAPON,
            description = "Has many properties",
            properties = mapOf(
                "damage" to "15",
                "weight" to "3.5",
                "skill_bonus_strength" to "2",
                "skill_bonus_agility" to "1",
                "durability" to "100"
            ),
            tags = listOf("sharp", "metal", "enchanted", "rare")
        )

        repository.saveTemplate(template).getOrThrow()
        val loaded = repository.findTemplateById("complex_item").getOrThrow()

        assertNotNull(loaded)
        assertEquals(5, loaded?.properties?.size)
        assertEquals("15", loaded?.properties?.get("damage"))
        assertEquals("3.5", loaded?.properties?.get("weight"))
        assertEquals(4, loaded?.tags?.size)
        assertTrue(loaded?.tags?.contains("enchanted") == true)
    }

    // === Instance Tests ===

    @Test
    fun `save and load instance`() {
        val instance = ItemInstance(
            id = "instance1",
            templateId = "sword_template",
            quality = 7,
            charges = 10,
            quantity = 1
        )

        repository.saveInstance(instance).getOrThrow()
        val loaded = repository.findInstanceById("instance1").getOrThrow()

        assertNotNull(loaded)
        assertEquals(instance, loaded)
    }

    @Test
    fun `find nonexistent instance returns null`() {
        val loaded = repository.findInstanceById("nonexistent").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `instance with null charges persists correctly`() {
        val instance = ItemInstance(
            id = "no_charges",
            templateId = "armor_template",
            quality = 5,
            charges = null,
            quantity = 1
        )

        repository.saveInstance(instance).getOrThrow()
        val loaded = repository.findInstanceById("no_charges").getOrThrow()

        assertNotNull(loaded)
        assertNull(loaded?.charges)
    }

    @Test
    fun `find instances by template`() {
        repository.saveInstance(
            ItemInstance(id = "i1", templateId = "sword", quality = 5, quantity = 1)
        ).getOrThrow()
        repository.saveInstance(
            ItemInstance(id = "i2", templateId = "sword", quality = 8, quantity = 1)
        ).getOrThrow()
        repository.saveInstance(
            ItemInstance(id = "i3", templateId = "potion", quality = 5, quantity = 3)
        ).getOrThrow()

        val swordInstances = repository.findInstancesByTemplate("sword").getOrThrow()
        assertEquals(2, swordInstances.size)
        assertTrue(swordInstances.any { it.id == "i1" })
        assertTrue(swordInstances.any { it.id == "i2" })

        val potionInstances = repository.findInstancesByTemplate("potion").getOrThrow()
        assertEquals(1, potionInstances.size)
        assertEquals("i3", potionInstances[0].id)
    }

    @Test
    fun `update instance overwrites existing`() {
        val original = ItemInstance(
            id = "update_me",
            templateId = "sword",
            quality = 5,
            quantity = 1
        )
        repository.saveInstance(original).getOrThrow()

        val updated = original.copy(quality = 9, quantity = 3)
        repository.saveInstance(updated).getOrThrow()

        val loaded = repository.findInstanceById("update_me").getOrThrow()
        assertEquals(9, loaded?.quality)
        assertEquals(3, loaded?.quantity)
    }

    @Test
    fun `delete instance removes it`() {
        val instance = ItemInstance(
            id = "delete_me",
            templateId = "sword",
            quality = 5,
            quantity = 1
        )
        repository.saveInstance(instance).getOrThrow()

        repository.deleteInstance("delete_me").getOrThrow()

        val loaded = repository.findInstanceById("delete_me").getOrThrow()
        assertNull(loaded)
    }

    @Test
    fun `find all instances returns all`() {
        repository.saveInstance(
            ItemInstance(id = "i1", templateId = "sword", quality = 5, quantity = 1)
        ).getOrThrow()
        repository.saveInstance(
            ItemInstance(id = "i2", templateId = "potion", quality = 5, quantity = 2)
        ).getOrThrow()
        repository.saveInstance(
            ItemInstance(id = "i3", templateId = "armor", quality = 7, quantity = 1)
        ).getOrThrow()

        val allInstances = repository.findAllInstances().getOrThrow()
        assertEquals(3, allInstances.size)
        assertTrue(allInstances.containsKey("i1"))
        assertTrue(allInstances.containsKey("i2"))
        assertTrue(allInstances.containsKey("i3"))
    }

    @Test
    fun `high quality instance persists correctly`() {
        val instance = ItemInstance(
            id = "high_quality",
            templateId = "legendary_sword",
            quality = 10,
            quantity = 1
        )

        repository.saveInstance(instance).getOrThrow()
        val loaded = repository.findInstanceById("high_quality").getOrThrow()

        assertEquals(10, loaded?.quality)
    }

    @Test
    fun `large quantity stack persists correctly`() {
        val instance = ItemInstance(
            id = "big_stack",
            templateId = "arrows",
            quality = 5,
            quantity = 999
        )

        repository.saveInstance(instance).getOrThrow()
        val loaded = repository.findInstanceById("big_stack").getOrThrow()

        assertEquals(999, loaded?.quantity)
    }

    // === Integration Tests ===

    @Test
    fun `load templates from JSON file`() {
        // Load the actual item_templates.json
        val jsonStream = this::class.java.classLoader.getResourceAsStream("item_templates.json")
        assertNotNull(jsonStream, "item_templates.json not found in resources")

        val jsonContent = jsonStream!!.bufferedReader().use { it.readText() }
        val templates = json.decodeFromString<List<ItemTemplate>>(jsonContent)

        assertTrue(templates.size >= 50, "Should have at least 50 templates")
        repository.saveTemplates(templates).getOrThrow()

        val allTemplates = repository.findAllTemplates().getOrThrow()
        assertEquals(templates.size, allTemplates.size)

        // Verify a few specific templates
        val ironSword = repository.findTemplateById("iron_sword").getOrThrow()
        assertNotNull(ironSword)
        assertEquals("Iron Sword", ironSword?.name)
        assertEquals(ItemType.WEAPON, ironSword?.type)

        val healthPotion = repository.findTemplateById("health_potion").getOrThrow()
        assertNotNull(healthPotion)
        assertEquals(ItemType.CONSUMABLE, healthPotion?.type)
    }

    @Test
    fun `full lifecycle - create instances from templates`() {
        // Save a template
        val swordTemplate = ItemTemplate(
            id = "iron_sword",
            name = "Iron Sword",
            type = ItemType.WEAPON,
            properties = mapOf("damage" to "10", "weight" to "2.5"),
            description = "A basic sword",
            equipSlot = EquipSlot.HANDS_MAIN
        )
        repository.saveTemplate(swordTemplate).getOrThrow()

        // Create instances
        val instance1 = ItemInstance(templateId = "iron_sword", quality = 5, quantity = 1)
        val instance2 = ItemInstance(templateId = "iron_sword", quality = 8, quantity = 1)

        repository.saveInstance(instance1).getOrThrow()
        repository.saveInstance(instance2).getOrThrow()

        // Query instances
        val instances = repository.findInstancesByTemplate("iron_sword").getOrThrow()
        assertEquals(2, instances.size)

        // Verify we can load both template and instances
        val template = repository.findTemplateById("iron_sword").getOrThrow()
        assertNotNull(template)
        instances.forEach { instance ->
            val loaded = repository.findInstanceById(instance.id).getOrThrow()
            assertNotNull(loaded)
            assertEquals("iron_sword", loaded?.templateId)
        }
    }
}
