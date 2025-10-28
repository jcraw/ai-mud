package com.jcraw.mud.reasoning.loot

import com.jcraw.mud.core.LootEntry
import com.jcraw.mud.core.LootTable

/**
 * Registry for predefined loot tables that can be referenced by ID.
 * NPCs and features reference loot tables by ID string.
 */
object LootTableRegistry {
    private val tables = mutableMapOf<String, LootTable>()

    init {
        registerDefaultTables()
    }

    /**
     * Get loot table by ID, returns null if not found.
     */
    fun getTable(id: String): LootTable? = tables[id]

    /**
     * Register a new loot table with given ID.
     * Overwrites existing table if ID already exists.
     */
    fun registerTable(id: String, table: LootTable) {
        tables[id] = table
    }

    /**
     * Clear all registered tables (useful for testing).
     */
    fun clear() {
        tables.clear()
        registerDefaultTables()
    }

    /**
     * Register default loot tables for common mob archetypes.
     */
    private fun registerDefaultTables() {
        // Goblin drops (common mob)
        registerTable("goblin_common", LootTable(
            entries = listOf(
                LootEntry("rusty_sword", weight = 30, minQuality = 1, maxQuality = 4, dropChance = 0.3),
                LootEntry("leather_armor", weight = 20, minQuality = 1, maxQuality = 3, dropChance = 0.2),
                LootEntry("health_potion", weight = 40, minQuality = 3, maxQuality = 6, dropChance = 0.5),
                LootEntry("iron_ore", weight = 10, minQuality = 1, maxQuality = 5, dropChance = 0.1)
            ),
            guaranteedDrops = 0,
            maxDrops = 2
        ))

        // Skeleton drops (common undead)
        registerTable("skeleton_common", LootTable(
            entries = listOf(
                LootEntry("bone_fragments", weight = 50, minQuality = 2, maxQuality = 5, minQuantity = 1, maxQuantity = 3),
                LootEntry("rusty_sword", weight = 20, minQuality = 1, maxQuality = 3, dropChance = 0.2),
                LootEntry("ancient_coin", weight = 30, minQuality = 3, maxQuality = 7, dropChance = 0.3)
            ),
            guaranteedDrops = 1,
            maxDrops = 2
        ))

        // Orc warrior drops (elite mob)
        registerTable("orc_elite", LootTable(
            entries = listOf(
                LootEntry("iron_sword", weight = 25, minQuality = 4, maxQuality = 8),
                LootEntry("chainmail_armor", weight = 20, minQuality = 5, maxQuality = 9),
                LootEntry("health_potion", weight = 35, minQuality = 5, maxQuality = 8, minQuantity = 1, maxQuantity = 2),
                LootEntry("iron_ore", weight = 20, minQuality = 3, maxQuality = 7, minQuantity = 1, maxQuantity = 3)
            ),
            guaranteedDrops = 1,
            maxDrops = 3
        ))

        // Dragon drops (boss)
        registerTable("dragon_boss", LootTable.bossDrop(
            rareIds = listOf("dragon_scale_armor", "enchanted_sword"),
            epicIds = listOf("ring_of_fire_resistance", "amulet_of_strength"),
            legendaryIds = listOf("dragonbane_sword")
        ))

        // Treasure chest (standard)
        registerTable("chest_standard", LootTable(
            entries = listOf(
                LootEntry("health_potion", weight = 40, minQuality = 4, maxQuality = 8, minQuantity = 1, maxQuantity = 3),
                LootEntry("iron_sword", weight = 20, minQuality = 5, maxQuality = 9),
                LootEntry("leather_armor", weight = 20, minQuality = 5, maxQuality = 9),
                LootEntry("gold_bar", weight = 20, minQuality = 5, maxQuality = 10, minQuantity = 1, maxQuantity = 5)
            ),
            guaranteedDrops = 2,
            maxDrops = 4
        ))

        // Mining node (iron)
        registerTable("mining_iron", LootTable(
            entries = listOf(
                LootEntry("iron_ore", weight = 80, minQuality = 3, maxQuality = 8, minQuantity = 1, maxQuantity = 3),
                LootEntry("coal", weight = 20, minQuality = 2, maxQuality = 6, minQuantity = 1, maxQuantity = 2)
            ),
            guaranteedDrops = 1,
            maxDrops = 2
        ))

        // Mining node (gold)
        registerTable("mining_gold", LootTable(
            entries = listOf(
                LootEntry("gold_ore", weight = 70, minQuality = 4, maxQuality = 9, minQuantity = 1, maxQuantity = 2),
                LootEntry("precious_gems", weight = 30, minQuality = 5, maxQuality = 10, dropChance = 0.3)
            ),
            guaranteedDrops = 1,
            maxDrops = 2
        ))

        // Herb patch (common)
        registerTable("herbs_common", LootTable(
            entries = listOf(
                LootEntry("healing_herb", weight = 60, minQuality = 3, maxQuality = 7, minQuantity = 1, maxQuantity = 3),
                LootEntry("mana_herb", weight = 30, minQuality = 3, maxQuality = 7, minQuantity = 1, maxQuantity = 2),
                LootEntry("rare_flower", weight = 10, minQuality = 5, maxQuality = 9, dropChance = 0.2)
            ),
            guaranteedDrops = 1,
            maxDrops = 2
        ))

        // Boss chest (rare loot)
        registerTable("chest_boss", LootTable.bossDrop(
            rareIds = listOf("enchanted_sword", "plate_armor", "health_elixir"),
            epicIds = listOf("ring_of_protection", "amulet_of_wisdom"),
            legendaryIds = listOf("crown_of_kings", "staff_of_power")
        ))
    }

    /**
     * Get all registered table IDs.
     */
    fun getAllTableIds(): Set<String> = tables.keys.toSet()

    /**
     * Check if a table ID exists.
     */
    fun hasTable(id: String): Boolean = tables.containsKey(id)
}
