package com.jcraw.mud.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LootTableTest {

    // LootEntry validation tests
    @Test
    fun `LootEntry requires positive weight`() {
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", weight = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", weight = -1)
        }
    }

    @Test
    fun `LootEntry requires valid quality range`() {
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", minQuality = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", maxQuality = 11)
        }
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", minQuality = 5, maxQuality = 3)
        }
    }

    @Test
    fun `LootEntry requires valid quantity range`() {
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", minQuantity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", minQuantity = 5, maxQuantity = 3)
        }
    }

    @Test
    fun `LootEntry requires valid dropChance range`() {
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", dropChance = -0.1)
        }
        assertFailsWith<IllegalArgumentException> {
            LootEntry("item1", dropChance = 1.1)
        }
    }

    // LootEntry roll tests
    @Test
    fun `rollDrop respects dropChance`() {
        val alwaysDrop = LootEntry("item1", dropChance = 1.0)
        val neverDrop = LootEntry("item2", dropChance = 0.0)

        assertTrue(alwaysDrop.rollDrop())
        assertFalse(neverDrop.rollDrop())
    }

    @Test
    fun `rollQuality returns value in range`() {
        val entry = LootEntry("item1", minQuality = 3, maxQuality = 7)
        val random = Random(42)

        repeat(100) {
            val quality = entry.rollQuality(random)
            assertTrue(quality in 3..7)
        }
    }

    @Test
    fun `rollQuantity returns value in range`() {
        val entry = LootEntry("item1", minQuantity = 2, maxQuantity = 5)
        val random = Random(42)

        repeat(100) {
            val quantity = entry.rollQuantity(random)
            assertTrue(quantity in 2..5)
        }
    }

    // LootTable validation tests
    @Test
    fun `LootTable requires at least one entry`() {
        assertFailsWith<IllegalArgumentException> {
            LootTable(entries = emptyList())
        }
    }

    @Test
    fun `LootTable requires valid guaranteedDrops`() {
        val entries = listOf(LootEntry("item1"))
        assertFailsWith<IllegalArgumentException> {
            LootTable(entries, guaranteedDrops = -1)
        }
    }

    @Test
    fun `LootTable requires maxDrops greater than guaranteedDrops`() {
        val entries = listOf(LootEntry("item1"))
        assertFailsWith<IllegalArgumentException> {
            LootTable(entries, guaranteedDrops = 5, maxDrops = 3)
        }
    }

    @Test
    fun `LootTable requires valid qualityModifier range`() {
        val entries = listOf(LootEntry("item1"))
        assertFailsWith<IllegalArgumentException> {
            LootTable(entries, qualityModifier = 6)
        }
        assertFailsWith<IllegalArgumentException> {
            LootTable(entries, qualityModifier = -6)
        }
    }

    // LootTable totalWeight tests
    @Test
    fun `totalWeight sums all entry weights`() {
        val table = LootTable(
            entries = listOf(
                LootEntry("item1", weight = 10),
                LootEntry("item2", weight = 20),
                LootEntry("item3", weight = 30)
            )
        )
        assertEquals(60, table.totalWeight)
    }

    // LootTable selectEntry tests
    @Test
    fun `selectEntry returns weighted random entry`() {
        val table = LootTable(
            entries = listOf(
                LootEntry("common", weight = 70),
                LootEntry("rare", weight = 30)
            )
        )
        val random = Random(42)

        // Sample 100 times and verify distribution roughly matches weights
        val results = mutableMapOf<String, Int>()
        repeat(100) {
            val entry = table.selectEntry(random)
            assertNotNull(entry)
            results[entry.templateId] = results.getOrDefault(entry.templateId, 0) + 1
        }

        // Common should appear more than rare
        assertTrue(results["common"]!! > results["rare"]!!)
    }

    // LootTable generateDrops tests
    @Test
    fun `generateDrops respects guaranteedDrops`() {
        val table = LootTable(
            entries = listOf(LootEntry("item1", dropChance = 1.0)),
            guaranteedDrops = 2,
            maxDrops = 3
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        assertTrue(drops.size >= 2)
    }

    @Test
    fun `generateDrops respects maxDrops`() {
        val table = LootTable(
            entries = listOf(LootEntry("item1", dropChance = 1.0)),
            guaranteedDrops = 0,
            maxDrops = 3
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        assertTrue(drops.size <= 3)
    }

    @Test
    fun `generateDrops applies qualityModifier`() {
        val table = LootTable(
            entries = listOf(LootEntry("item1", minQuality = 5, maxQuality = 5, dropChance = 1.0)),
            guaranteedDrops = 1,
            qualityModifier = 2
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        assertEquals(1, drops.size)
        val (templateId, quality, quantity) = drops.first()
        assertEquals("item1", templateId)
        assertEquals(7, quality) // 5 + 2 modifier
        assertEquals(1, quantity)
    }

    @Test
    fun `generateDrops clamps quality to valid range`() {
        val table = LootTable(
            entries = listOf(LootEntry("item1", minQuality = 10, maxQuality = 10, dropChance = 1.0)),
            guaranteedDrops = 1,
            qualityModifier = 3 // Would exceed 10
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        val (_, quality, _) = drops.first()
        assertEquals(10, quality) // Clamped to max
    }

    @Test
    fun `generateDrops respects dropChance`() {
        val table = LootTable(
            entries = listOf(LootEntry("item1", dropChance = 0.0)),
            guaranteedDrops = 3,
            maxDrops = 5
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        assertEquals(0, drops.size) // No drops due to 0% chance
    }

    @Test
    fun `generateDrops returns quantity from entry`() {
        val table = LootTable(
            entries = listOf(
                LootEntry("item1", minQuantity = 3, maxQuantity = 3, dropChance = 1.0)
            ),
            guaranteedDrops = 1
        )
        val random = Random(42)

        val drops = table.generateDrops(random)
        val (_, _, quantity) = drops.first()
        assertEquals(3, quantity)
    }

    // Companion factory method tests
    @Test
    fun `commonBias creates weighted table`() {
        val table = LootTable.commonBias(
            commonIds = listOf("common1", "common2"),
            uncommonIds = listOf("uncommon1"),
            rareIds = listOf("rare1")
        )

        assertEquals(4, table.entries.size)
        // Common entries should have higher weight
        val commonEntry = table.entries.find { it.templateId == "common1" }
        val rareEntry = table.entries.find { it.templateId == "rare1" }
        assertNotNull(commonEntry)
        assertNotNull(rareEntry)
        assertTrue(commonEntry.weight > rareEntry.weight)
    }

    @Test
    fun `bossDrop creates high-quality table`() {
        val table = LootTable.bossDrop(
            rareIds = listOf("rare1"),
            epicIds = listOf("epic1"),
            legendaryIds = listOf("legendary1")
        )

        assertEquals(3, table.entries.size)
        assertTrue(table.guaranteedDrops > 0)
        assertTrue(table.qualityModifier > 0)

        // Verify quality ranges are elevated
        val rareEntry = table.entries.find { it.templateId == "rare1" }
        assertNotNull(rareEntry)
        assertTrue(rareEntry.minQuality >= 5)
    }

    @Test
    fun `bossDrop allows empty legendary list`() {
        val table = LootTable.bossDrop(
            rareIds = listOf("rare1"),
            epicIds = listOf("epic1")
        )

        assertEquals(2, table.entries.size)
    }
}
