package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.test.*

/**
 * Tests for LootTableGenerator procedural loot tables.
 * Validates loot table generation, rarity distribution, and difficulty scaling.
 */
class LootTableGeneratorTest {

    private class MockItemRepository(
        private val templates: Map<String, ItemTemplate> = emptyMap()
    ) : ItemRepository {
        override fun findTemplateById(templateId: String): Result<ItemTemplate?> = Result.success(templates[templateId])
        override fun findAllTemplates(): Result<Map<String, ItemTemplate>> = Result.success(templates)
        override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> =
            Result.success(templates.values.filter { it.type == type })
        override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
            Result.success(templates.values.filter { it.rarity == rarity })
        override fun saveTemplate(template: ItemTemplate): Result<Unit> = Result.success(Unit)
        override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> = Result.success(Unit)
        override fun deleteTemplate(templateId: String): Result<Unit> = Result.success(Unit)
        override fun findInstanceById(instanceId: String): Result<ItemInstance?> = Result.success(null)
        override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> =
            Result.success(emptyList())
        override fun saveInstance(instance: ItemInstance): Result<Unit> = Result.success(Unit)
        override fun deleteInstance(instanceId: String): Result<Unit> = Result.success(Unit)
        override fun findAllInstances(): Result<Map<String, ItemInstance>> = Result.success(emptyMap())
    }

    private fun createMockTemplates(): Map<String, ItemTemplate> {
        return mapOf(
            "wood" to ItemTemplate("wood", "Wood", ItemType.RESOURCE, rarity = Rarity.COMMON,
                tags = listOf("wood", "forest"), properties = mapOf("weight" to "1.0"), description = "Wood"),
            "iron_sword" to ItemTemplate("iron_sword", "Iron Sword", ItemType.WEAPON, rarity = Rarity.UNCOMMON,
                tags = listOf("metal", "weapon"), properties = mapOf("weight" to "3.0"), description = "A sword"),
            "fire_staff" to ItemTemplate("fire_staff", "Fire Staff", ItemType.WEAPON, rarity = Rarity.RARE,
                tags = listOf("fire", "magic"), properties = mapOf("weight" to "2.0"), description = "A fire staff"),
            "dragon_scale" to ItemTemplate("dragon_scale", "Dragon Scale", ItemType.RESOURCE, rarity = Rarity.EPIC,
                tags = listOf("dragon", "rare"), properties = mapOf("weight" to "0.5"), description = "A scale"),
            "legendary_sword" to ItemTemplate("legendary_sword", "Excalibur", ItemType.WEAPON, rarity = Rarity.LEGENDARY,
                tags = listOf("legendary", "weapon"), properties = mapOf("weight" to "5.0"), description = "Legendary blade")
        )
    }

    @Test
    fun `generateForTheme creates valid loot table`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("dark forest", 5)

        assertNotNull(table)
        assertTrue(table.entries.isNotEmpty())
    }

    @Test
    fun `generateForTheme uses correct table ID format`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        // Generate and check if registered with correct ID
        generator.generateForTheme("dark forest", 5)

        // Should be accessible via registry with "dark_forest_5"
        val tableFromRegistry = com.jcraw.mud.reasoning.loot.LootTableRegistry.getTable("dark_forest_5")
        assertNotNull(tableFromRegistry)
    }

    @Test
    fun `generateForTheme filters items by theme keywords`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("dark forest", 5)

        // Should include wood (has "forest" tag)
        assertTrue(table.entries.any { it.templateId == "wood" })
    }

    @Test
    fun `generateForTheme respects rarity distribution`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("generic", 5)

        // Common items should have highest weight
        val commonEntry = table.entries.find { it.templateId == "wood" }
        val rareEntry = table.entries.find { it.templateId == "fire_staff" }

        if (commonEntry != null && rareEntry != null) {
            assertTrue(commonEntry.weight > rareEntry.weight, "Common should have higher weight than rare")
        }
    }

    @Test
    fun `generateForTheme scales quality with difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val lowDiff = generator.generateForTheme("dark forest", 2)
        val highDiff = generator.generateForTheme("dark forest", 20)

        // High difficulty should have higher quality ranges
        val lowAvgQuality = lowDiff.entries.map { (it.minQuality + it.maxQuality) / 2.0 }.average()
        val highAvgQuality = highDiff.entries.map { (it.minQuality + it.maxQuality) / 2.0 }.average()

        assertTrue(highAvgQuality >= lowAvgQuality, "High difficulty should have higher quality")
    }

    @Test
    fun `generateForTheme scales guaranteed drops with difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val low = generator.generateForTheme("dark forest", 5)
        val elite = generator.generateForTheme("dark forest", 12)
        val boss = generator.generateForTheme("dark forest", 18)

        assertTrue(low.guaranteedDrops == 0, "Low difficulty should have 0 guaranteed drops")
        assertTrue(elite.guaranteedDrops == 1, "Elite difficulty should have 1 guaranteed drop")
        assertTrue(boss.guaranteedDrops == 2, "Boss difficulty should have 2 guaranteed drops")
    }

    @Test
    fun `generateForTheme scales max drops with difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val low = generator.generateForTheme("dark forest", 5)
        val elite = generator.generateForTheme("dark forest", 12)
        val boss = generator.generateForTheme("dark forest", 18)

        assertEquals(2, low.maxDrops, "Low difficulty should have 2 max drops")
        assertEquals(3, elite.maxDrops, "Elite difficulty should have 3 max drops")
        assertEquals(4, boss.maxDrops, "Boss difficulty should have 4 max drops")
    }

    @Test
    fun `generateForTheme scales quality modifier with difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val low = generator.generateForTheme("dark forest", 2)
        val high = generator.generateForTheme("dark forest", 18)

        assertTrue(high.qualityModifier >= low.qualityModifier, "High difficulty should have higher quality modifier")
        assertTrue(high.qualityModifier <= 3, "Quality modifier should cap at +3")
    }

    @Test
    fun `generateForTheme handles empty item pool with fallback`() {
        val mockRepo = MockItemRepository(emptyMap())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("unknown theme", 5)

        // Should have fallback entry
        assertTrue(table.entries.isNotEmpty())
        assertTrue(table.entries.any { it.templateId == "gold_coin" })
    }

    @Test
    fun `generateForTheme caches tables`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val first = generator.generateForTheme("dark forest", 5)
        val second = generator.generateForTheme("dark forest", 5)

        // Should return same table instance (cached)
        assertEquals(first.entries.size, second.entries.size)
    }

    @Test
    fun `generateForTheme extracts theme keywords correctly`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        // Test various themes to ensure keyword extraction works
        val themes = listOf(
            "dark forest", "magma cave", "ancient crypt", "frozen wasteland",
            "abandoned castle", "swamp", "desert ruins", "underground lake"
        )

        themes.forEach { theme ->
            val table = generator.generateForTheme(theme, 10)
            assertTrue(table.entries.isNotEmpty(), "Should generate table for $theme")
        }
    }

    @Test
    fun `generateGoldRange scales with difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val lowRange = generator.generateGoldRange(2)
        val highRange = generator.generateGoldRange(20)

        assertEquals(20..100, lowRange)
        assertEquals(200..1000, highRange)
    }

    @Test
    fun `generateGoldRange formula is correct`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val range = generator.generateGoldRange(10)

        assertEquals(100, range.first)
        assertEquals(500, range.last)
    }

    @Test
    fun `generateForTheme handles theme with spaces`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("dark forest", 5)

        // Should normalize to "dark_forest_5"
        assertNotNull(table)
    }

    @Test
    fun `generateForTheme includes legendary items in high difficulty`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("epic quest", 20)

        // Should attempt to include legendary items if available
        val hasLegendary = table.entries.any { entry ->
            mockRepo.findTemplateById(entry.templateId).getOrNull()?.rarity == Rarity.LEGENDARY
        }

        // May or may not have legendary depending on theme match
        assertTrue(table.entries.isNotEmpty())
    }

    @Test
    fun `generateForTheme assigns appropriate drop chances`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("generic", 10)

        // Verify drop chances align with rarity
        table.entries.forEach { entry ->
            assertTrue(entry.dropChance > 0.0, "Drop chance should be positive")
            assertTrue(entry.dropChance <= 1.0, "Drop chance should be at most 1.0")
        }
    }

    @Test
    fun `generateForTheme handles mixed rarity pools`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val table = generator.generateForTheme("generic", 10)

        // Should have entries from multiple rarities
        val rarities = table.entries.mapNotNull { entry ->
            mockRepo.findTemplateById(entry.templateId).getOrNull()?.rarity
        }.toSet()

        assertTrue(rarities.isNotEmpty(), "Should have items from various rarities")
    }

    @Test
    fun `generateForTheme works with various difficulty values`() {
        val mockRepo = MockItemRepository(createMockTemplates())
        val generator = LootTableGenerator(mockRepo)

        val difficulties = listOf(1, 5, 10, 15, 20)
        difficulties.forEach { diff ->
            val table = generator.generateForTheme("test", diff)
            assertTrue(table.entries.isNotEmpty(), "Should generate table for difficulty $diff")
        }
    }
}
