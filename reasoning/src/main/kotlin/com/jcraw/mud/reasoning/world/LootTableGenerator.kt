package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.LootEntry
import com.jcraw.mud.core.LootTable
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.loot.LootTableRegistry

/**
 * Generates procedural loot tables for themes and difficulty levels.
 * Extends LootTableRegistry with on-demand generation for infinite themes.
 *
 * Rarity distribution follows guidelines:
 * - COMMON: 50%
 * - UNCOMMON: 30%
 * - RARE: 15%
 * - EPIC: 4%
 * - LEGENDARY: 1%
 */
class LootTableGenerator(
    private val itemRepository: ItemRepository
) {
    /**
     * Generate loot table for a theme and difficulty level.
     * Queries ItemRepository for theme-appropriate items.
     * Registers table in LootTableRegistry with ID: {theme}_{difficulty}
     */
    fun generateForTheme(theme: String, difficulty: Int): LootTable {
        val tableId = "${theme.lowercase().replace(" ", "_")}_$difficulty"

        // Check if table already exists
        LootTableRegistry.getTable(tableId)?.let { return it }

        // Query all templates for filtering
        val allTemplates = itemRepository.findAllTemplates().getOrNull() ?: emptyMap()

        // Filter items by theme matching (simple keyword matching)
        val themeKeywords = extractThemeKeywords(theme)
        val themeItems = allTemplates.values.filter { template ->
            themeKeywords.any { keyword ->
                template.name.lowercase().contains(keyword) ||
                        template.description.lowercase().contains(keyword) ||
                        template.tags.any { tag -> tag.lowercase().contains(keyword) }
            }
        }

        // If no theme-specific items, use general loot
        val itemPool = if (themeItems.isNotEmpty()) themeItems else allTemplates.values.toList()

        // Separate items by rarity
        val commonItems = itemPool.filter { it.rarity == Rarity.COMMON }
        val uncommonItems = itemPool.filter { it.rarity == Rarity.UNCOMMON }
        val rareItems = itemPool.filter { it.rarity == Rarity.RARE }
        val epicItems = itemPool.filter { it.rarity == Rarity.EPIC }
        val legendaryItems = itemPool.filter { it.rarity == Rarity.LEGENDARY }

        // Build loot entries with rarity weights
        val entries = mutableListOf<LootEntry>()

        // Common items (50% weight total)
        commonItems.take(5).forEach { template ->
            entries.add(
                LootEntry(
                    templateId = template.id,
                    weight = 50 / 5.coerceAtLeast(1),
                    minQuality = scaleQuality(difficulty, 1),
                    maxQuality = scaleQuality(difficulty, 5),
                    dropChance = 0.5
                )
            )
        }

        // Uncommon items (30% weight total)
        uncommonItems.take(3).forEach { template ->
            entries.add(
                LootEntry(
                    templateId = template.id,
                    weight = 30 / 3.coerceAtLeast(1),
                    minQuality = scaleQuality(difficulty, 3),
                    maxQuality = scaleQuality(difficulty, 7),
                    dropChance = 0.3
                )
            )
        }

        // Rare items (15% weight total)
        rareItems.take(2).forEach { template ->
            entries.add(
                LootEntry(
                    templateId = template.id,
                    weight = 15 / 2.coerceAtLeast(1),
                    minQuality = scaleQuality(difficulty, 5),
                    maxQuality = scaleQuality(difficulty, 9),
                    dropChance = 0.15
                )
            )
        }

        // Epic items (4% weight total)
        epicItems.take(1).forEach { template ->
            entries.add(
                LootEntry(
                    templateId = template.id,
                    weight = 4,
                    minQuality = scaleQuality(difficulty, 7),
                    maxQuality = 10,
                    dropChance = 0.04
                )
            )
        }

        // Legendary items (1% weight total)
        legendaryItems.take(1).forEach { template ->
            entries.add(
                LootEntry(
                    templateId = template.id,
                    weight = 1,
                    minQuality = 9,
                    maxQuality = 10,
                    dropChance = 0.01
                )
            )
        }

        // Fallback if no items available (shouldn't happen in practice)
        if (entries.isEmpty()) {
            entries.add(
                LootEntry(
                    templateId = "gold_coin",
                    weight = 100,
                    minQuality = 1,
                    maxQuality = 5,
                    minQuantity = 1,
                    maxQuantity = 10
                )
            )
        }

        // Create loot table with difficulty-scaled parameters
        val guaranteedDrops = when {
            difficulty >= 15 -> 2 // Boss-level
            difficulty >= 10 -> 1 // Elite
            else -> 0 // Common
        }

        val maxDrops = when {
            difficulty >= 15 -> 4
            difficulty >= 10 -> 3
            else -> 2
        }

        val table = LootTable(
            entries = entries,
            guaranteedDrops = guaranteedDrops,
            maxDrops = maxDrops,
            qualityModifier = (difficulty / 5).coerceAtMost(3) // +0 to +3 based on difficulty
        )

        // Register in registry
        LootTableRegistry.registerTable(tableId, table)

        return table
    }

    /**
     * Extract keywords from theme for item matching.
     */
    private fun extractThemeKeywords(theme: String): List<String> {
        val normalized = theme.lowercase()
        val words = normalized.split(" ", "-", "_")

        // Add theme-specific synonyms
        val keywords = words.toMutableList()
        when {
            "forest" in normalized || "wood" in normalized -> {
                keywords.addAll(listOf("wood", "leaf", "tree", "nature", "green"))
            }
            "fire" in normalized || "magma" in normalized || "lava" in normalized -> {
                keywords.addAll(listOf("fire", "flame", "magma", "lava", "obsidian", "burn"))
            }
            "ice" in normalized || "frost" in normalized || "frozen" in normalized -> {
                keywords.addAll(listOf("ice", "frost", "frozen", "cold", "winter", "snow"))
            }
            "undead" in normalized || "crypt" in normalized || "skeleton" in normalized -> {
                keywords.addAll(listOf("bone", "undead", "death", "grave", "spirit", "cursed"))
            }
            "desert" in normalized || "sand" in normalized -> {
                keywords.addAll(listOf("sand", "desert", "dry", "sun", "dune"))
            }
            "water" in normalized || "lake" in normalized || "swamp" in normalized -> {
                keywords.addAll(listOf("water", "wet", "aqua", "fish", "algae", "swamp"))
            }
        }

        return keywords.distinct()
    }

    /**
     * Scale quality with difficulty (1-10 range).
     * Higher difficulty = higher minimum quality.
     */
    private fun scaleQuality(difficulty: Int, baseQuality: Int): Int {
        val difficultyBonus = (difficulty / 5).coerceIn(0, 3)
        return (baseQuality + difficultyBonus).coerceIn(1, 10)
    }

    /**
     * Generate gold drop range for difficulty level.
     * Formula: (10 * difficulty)..(50 * difficulty)
     */
    fun generateGoldRange(difficulty: Int): IntRange {
        val min = 10 * difficulty
        val max = 50 * difficulty
        return min..max
    }
}
