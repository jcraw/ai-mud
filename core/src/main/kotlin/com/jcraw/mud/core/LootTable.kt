package com.jcraw.mud.core

import kotlin.random.Random

/**
 * Represents a single entry in a loot table with weighted probability.
 *
 * @property templateId ID of the item template to drop
 * @property weight Relative weight for drop probability (higher = more likely)
 * @property minQuality Minimum quality roll (1-10)
 * @property maxQuality Maximum quality roll (1-10)
 * @property minQuantity Minimum quantity to drop
 * @property maxQuantity Maximum quantity to drop
 * @property dropChance Probability this entry drops at all (0.0-1.0)
 */
data class LootEntry(
    val templateId: String,
    val weight: Int = 1,
    val minQuality: Int = 1,
    val maxQuality: Int = 10,
    val minQuantity: Int = 1,
    val maxQuantity: Int = 1,
    val dropChance: Double = 1.0
) {
    init {
        require(weight > 0) { "Weight must be positive" }
        require(minQuality in 1..10) { "minQuality must be 1-10" }
        require(maxQuality in 1..10) { "maxQuality must be 1-10" }
        require(minQuality <= maxQuality) { "minQuality must be <= maxQuality" }
        require(minQuantity >= 1) { "minQuantity must be >= 1" }
        require(maxQuantity >= minQuantity) { "maxQuantity must be >= minQuantity" }
        require(dropChance in 0.0..1.0) { "dropChance must be 0.0-1.0" }
    }

    /**
     * Rolls whether this entry drops based on dropChance.
     */
    fun rollDrop(random: Random = Random.Default): Boolean {
        return random.nextDouble() < dropChance
    }

    /**
     * Rolls a random quality value within the entry's range.
     */
    fun rollQuality(random: Random = Random.Default): Int {
        return random.nextInt(minQuality, maxQuality + 1)
    }

    /**
     * Rolls a random quantity value within the entry's range.
     */
    fun rollQuantity(random: Random = Random.Default): Int {
        return random.nextInt(minQuantity, maxQuantity + 1)
    }
}

/**
 * Represents a loot table with weighted entries and drop configuration.
 *
 * @property entries List of loot entries with weights
 * @property guaranteedDrops Minimum number of items to drop (0 = none guaranteed)
 * @property maxDrops Maximum number of items that can drop (-1 = unlimited)
 * @property qualityModifier Flat modifier to add to all quality rolls
 */
data class LootTable(
    val entries: List<LootEntry>,
    val guaranteedDrops: Int = 0,
    val maxDrops: Int = -1,
    val qualityModifier: Int = 0
) {
    init {
        require(entries.isNotEmpty()) { "LootTable must have at least one entry" }
        require(guaranteedDrops >= 0) { "guaranteedDrops must be >= 0" }
        require(maxDrops == -1 || maxDrops >= guaranteedDrops) {
            "maxDrops must be >= guaranteedDrops or -1 for unlimited"
        }
        require(qualityModifier in -5..5) { "qualityModifier must be -5 to 5" }
    }

    /**
     * Total weight of all entries.
     */
    val totalWeight: Int
        get() = entries.sumOf { it.weight }

    /**
     * Selects a random entry based on weighted probability.
     * Returns null if no entries exist (shouldn't happen due to init check).
     */
    fun selectEntry(random: Random = Random.Default): LootEntry? {
        if (entries.isEmpty()) return null

        val roll = random.nextInt(totalWeight)
        var accumulated = 0

        for (entry in entries) {
            accumulated += entry.weight
            if (roll < accumulated) {
                return entry
            }
        }

        // Fallback (shouldn't reach here)
        return entries.last()
    }

    /**
     * Generates a list of item template IDs with quality and quantity based on the loot table rules.
     * Returns list of (templateId, quality, quantity) tuples.
     *
     * @param random Random instance for reproducible tests
     * @return List of drops as (templateId, quality, quantity)
     */
    fun generateDrops(random: Random = Random.Default): List<Triple<String, Int, Int>> {
        val drops = mutableListOf<Triple<String, Int, Int>>()
        val actualMaxDrops = if (maxDrops == -1) entries.size else maxDrops

        // Generate guaranteed drops first
        repeat(guaranteedDrops) {
            val entry = selectEntry(random) ?: return@repeat
            if (entry.rollDrop(random)) {
                val quality = (entry.rollQuality(random) + qualityModifier).coerceIn(1, 10)
                val quantity = entry.rollQuantity(random)
                drops.add(Triple(entry.templateId, quality, quantity))
            }
        }

        // Generate additional drops up to maxDrops
        val remainingSlots = actualMaxDrops - drops.size
        repeat(remainingSlots) {
            val entry = selectEntry(random) ?: return@repeat
            if (entry.rollDrop(random)) {
                val quality = (entry.rollQuality(random) + qualityModifier).coerceIn(1, 10)
                val quantity = entry.rollQuantity(random)
                drops.add(Triple(entry.templateId, quality, quantity))
            }
        }

        return drops
    }

    companion object {
        /**
         * Creates a loot table with common rarity bias (70% common, 20% uncommon, 8% rare, 2% epic).
         */
        fun commonBias(
            commonIds: List<String>,
            uncommonIds: List<String> = emptyList(),
            rareIds: List<String> = emptyList(),
            epicIds: List<String> = emptyList()
        ): LootTable {
            val entries = buildList {
                commonIds.forEach { add(LootEntry(it, weight = 70)) }
                uncommonIds.forEach { add(LootEntry(it, weight = 20)) }
                rareIds.forEach { add(LootEntry(it, weight = 8)) }
                epicIds.forEach { add(LootEntry(it, weight = 2)) }
            }
            return LootTable(entries)
        }

        /**
         * Creates a loot table for boss drops with better quality and rarity.
         */
        fun bossDrop(
            rareIds: List<String>,
            epicIds: List<String>,
            legendaryIds: List<String> = emptyList()
        ): LootTable {
            val entries = buildList {
                rareIds.forEach { add(LootEntry(it, weight = 50, minQuality = 5, maxQuality = 10)) }
                epicIds.forEach { add(LootEntry(it, weight = 35, minQuality = 7, maxQuality = 10)) }
                legendaryIds.forEach { add(LootEntry(it, weight = 15, minQuality = 8, maxQuality = 10)) }
            }
            return LootTable(entries, guaranteedDrops = 1, maxDrops = 3, qualityModifier = 2)
        }
    }
}
