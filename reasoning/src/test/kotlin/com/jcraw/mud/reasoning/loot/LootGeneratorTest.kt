package com.jcraw.mud.reasoning.loot

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LootGeneratorTest {

    // Mock repository for testing
    private val mockRepository = object : ItemRepository {
        private val templates = mapOf(
            "sword" to ItemTemplate(
                id = "sword",
                name = "Iron Sword",
                type = ItemType.WEAPON,
                rarity = Rarity.COMMON,
                description = "A basic iron sword",
                equipSlot = EquipSlot.HANDS_MAIN,
                properties = mapOf("damage" to "10", "weight" to "2.5")
            ),
            "potion" to ItemTemplate(
                id = "potion",
                name = "Health Potion",
                type = ItemType.CONSUMABLE,
                rarity = Rarity.COMMON,
                description = "Restores health",
                properties = mapOf("healing" to "20", "weight" to "0.5", "charges" to "1")
            ),
            "pickaxe" to ItemTemplate(
                id = "pickaxe",
                name = "Iron Pickaxe",
                type = ItemType.TOOL,
                rarity = Rarity.COMMON,
                description = "For mining",
                equipSlot = EquipSlot.HANDS_MAIN,
                properties = mapOf("durability" to "50", "weight" to "3.0")
            ),
            "spell_book" to ItemTemplate(
                id = "spell_book",
                name = "Fireball Tome",
                type = ItemType.SPELL_BOOK,
                rarity = Rarity.RARE,
                description = "Teaches Fireball spell",
                properties = mapOf("charges" to "5", "weight" to "1.0")
            ),
            "ore" to ItemTemplate(
                id = "ore",
                name = "Iron Ore",
                type = ItemType.RESOURCE,
                rarity = Rarity.COMMON,
                description = "Raw iron ore",
                properties = mapOf("weight" to "1.5")
            )
        )

        override fun findTemplateById(templateId: String): Result<ItemTemplate?> {
            return Result.success(templates[templateId])
        }

        override fun findAllTemplates(): Result<Map<String, ItemTemplate>> {
            return Result.success(templates)
        }

        override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> {
            return Result.success(templates.values.filter { it.type == type })
        }

        override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> {
            return Result.success(templates.values.filter { it.rarity == rarity })
        }

        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
        override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
        override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
        override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> = Result.success(emptyList())
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
        override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
    }

    private val generator = LootGenerator(mockRepository)

    // Basic generation tests
    @Test
    fun `generateLoot creates instances from table`() {
        val table = LootTable(
            entries = listOf(LootEntry("sword", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        assertTrue(result.isSuccess)

        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals("sword", items.first().templateId)
    }

    @Test
    fun `generateLoot returns empty list when no drops`() {
        val table = LootTable(
            entries = listOf(LootEntry("sword", dropChance = 0.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        assertTrue(result.isSuccess)

        val items = result.getOrThrow()
        assertEquals(0, items.size)
    }

    @Test
    fun `generateLoot handles missing template gracefully`() {
        val table = LootTable(
            entries = listOf(LootEntry("nonexistent", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        assertTrue(result.isSuccess)

        val items = result.getOrThrow()
        assertEquals(0, items.size) // Skips nonexistent items
    }

    // Source modifier tests
    @Test
    fun `generateLoot applies source quality modifier`() {
        val table = LootTable(
            entries = listOf(LootEntry("sword", minQuality = 5, maxQuality = 5, dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        // Boss source adds +2 quality
        val result = generator.generateLoot(table, LootSource.BOSS, random)
        assertTrue(result.isSuccess)

        val items = result.getOrThrow()
        assertEquals(1, items.size)
        assertEquals(7, items.first().quality) // 5 + 2
    }

    @Test
    fun `generateLoot uses different modifiers per source`() {
        val table = LootTable(
            entries = listOf(LootEntry("sword", minQuality = 5, maxQuality = 5, dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        // Test different sources
        val commonMob = generator.generateLoot(table, LootSource.COMMON_MOB, random).getOrThrow().first()
        val eliteMob = generator.generateLoot(table, LootSource.ELITE_MOB, random).getOrThrow().first()
        val boss = generator.generateLoot(table, LootSource.BOSS, random).getOrThrow().first()

        assertEquals(5, commonMob.quality) // +0
        assertEquals(6, eliteMob.quality) // +1
        assertEquals(7, boss.quality) // +2
    }

    // Quest item tests
    @Test
    fun `generateQuestItem creates item with specified quality`() {
        val result = generator.generateQuestItem("sword", quality = 8)
        assertTrue(result.isSuccess)

        val item = result.getOrThrow()
        assertEquals("sword", item.templateId)
        assertEquals(8, item.quality)
        assertEquals(1, item.quantity)
    }

    @Test
    fun `generateQuestItem defaults to quality 7`() {
        val result = generator.generateQuestItem("sword")
        assertTrue(result.isSuccess)

        val item = result.getOrThrow()
        assertEquals(7, item.quality)
    }

    @Test
    fun `generateQuestItem supports quantity`() {
        val result = generator.generateQuestItem("ore", quantity = 5)
        assertTrue(result.isSuccess)

        val item = result.getOrThrow()
        assertEquals(5, item.quantity)
    }

    @Test
    fun `generateQuestItem fails for missing template`() {
        val result = generator.generateQuestItem("nonexistent")
        assertTrue(result.isFailure)
    }

    // Charges tests
    @Test
    fun `generateLoot sets charges for consumables`() {
        val table = LootTable(
            entries = listOf(LootEntry("potion", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        val items = result.getOrThrow()

        assertEquals(1, items.size)
        assertEquals(1, items.first().charges) // From template
    }

    @Test
    fun `generateLoot sets charges for tools`() {
        val table = LootTable(
            entries = listOf(LootEntry("pickaxe", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        val items = result.getOrThrow()

        assertEquals(1, items.size)
        assertEquals(50, items.first().charges) // Durability from template
    }

    @Test
    fun `generateLoot sets charges for spell books`() {
        val table = LootTable(
            entries = listOf(LootEntry("spell_book", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        val items = result.getOrThrow()

        assertEquals(1, items.size)
        assertEquals(5, items.first().charges) // From template
    }

    @Test
    fun `generateLoot does not set charges for non-chargeable items`() {
        val table = LootTable(
            entries = listOf(LootEntry("sword", dropChance = 1.0)),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.COMMON_MOB, random)
        val items = result.getOrThrow()

        assertEquals(1, items.size)
        assertNull(items.first().charges)
    }

    // Gold drop tests
    @Test
    fun `generateGoldDrop applies source multiplier`() {
        val random = Random(42)

        val commonGold = generator.generateGoldDrop(100, LootSource.COMMON_MOB, random)
        val bossGold = generator.generateGoldDrop(100, LootSource.BOSS, random)

        // Boss should give more gold (3x multiplier)
        assertTrue(bossGold > commonGold)
    }

    @Test
    fun `generateGoldDrop applies variance`() {
        val random = Random(42)

        // Generate multiple times to see variance
        val amounts = List(10) {
            generator.generateGoldDrop(100, LootSource.COMMON_MOB, random)
        }

        // Should have some variance (not all the same)
        assertTrue(amounts.distinct().size > 1)
    }

    @Test
    fun `generateGoldDrop never returns zero or negative`() {
        val random = Random(42)

        repeat(100) {
            val amount = generator.generateGoldDrop(1, LootSource.COMMON_MOB, random)
            assertTrue(amount >= 1)
        }
    }

    // Companion factory tests
    @Test
    fun `createCommonMobTable has appropriate drop settings`() {
        val table = LootGenerator.createCommonMobTable(
            commonDrops = listOf("sword"),
            uncommonDrops = listOf("potion")
        )

        assertEquals(2, table.entries.size)
        assertEquals(0, table.guaranteedDrops)
        assertEquals(2, table.maxDrops)
    }

    @Test
    fun `createEliteMobTable has better drops`() {
        val table = LootGenerator.createEliteMobTable(
            commonDrops = listOf("sword"),
            uncommonDrops = listOf("potion"),
            rareDrops = listOf("spell_book")
        )

        assertEquals(3, table.entries.size)
        assertTrue(table.guaranteedDrops > 0)
        assertTrue(table.maxDrops >= table.guaranteedDrops)

        // Elite mobs should have better quality ranges
        val rareEntry = table.entries.find { it.templateId == "spell_book" }
        assertNotNull(rareEntry)
        assertTrue(rareEntry.minQuality >= 5)
    }

    @Test
    fun `createChestTable guarantees drops`() {
        val table = LootGenerator.createChestTable(
            guaranteedItems = listOf("sword", "potion"),
            bonusItems = listOf("ore")
        )

        assertEquals(3, table.entries.size)
        assertEquals(2, table.guaranteedDrops)

        // Guaranteed items should have 100% drop chance
        val guaranteedEntry = table.entries.first()
        assertEquals(1.0, guaranteedEntry.dropChance)

        // Bonus items should have lower drop chance
        val bonusEntry = table.entries.last()
        assertTrue(bonusEntry.dropChance < 1.0)
    }

    @Test
    fun `createChestTable works with empty bonus items`() {
        val table = LootGenerator.createChestTable(
            guaranteedItems = listOf("sword")
        )

        assertEquals(1, table.entries.size)
        assertEquals(1, table.guaranteedDrops)
    }

    // Integration tests
    @Test
    fun `full loot generation flow with multiple items`() {
        val table = LootTable(
            entries = listOf(
                LootEntry("sword", weight = 50, dropChance = 1.0),
                LootEntry("potion", weight = 50, minQuantity = 2, maxQuantity = 3, dropChance = 1.0)
            ),
            guaranteedDrops = 2,
            maxDrops = 2
        )
        val random = Random(42)

        val result = generator.generateLoot(table, LootSource.ELITE_MOB, random)
        assertTrue(result.isSuccess)

        val items = result.getOrThrow()
        assertEquals(2, items.size)

        // Verify all items have valid properties
        items.forEach { item ->
            assertTrue(item.quality in 1..10)
            assertTrue(item.quantity >= 1)
            assertNotNull(item.templateId)
        }
    }
}
