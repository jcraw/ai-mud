package com.jcraw.mud.reasoning.loot

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.LootTable
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random

/**
 * Source type for loot generation affects quality and drop rates
 */
enum class LootSource {
    COMMON_MOB,    // Standard enemy drops
    ELITE_MOB,     // Tougher enemy drops (quality +1)
    BOSS,          // Boss drops (quality +2)
    CHEST,         // Container/chest loot
    QUEST,         // Quest objective rewards
    FEATURE        // Room feature interactions (mining, harvesting)
}

/**
 * Generates item instances from loot tables with quality variance based on source.
 *
 * @property itemRepository Repository for fetching item templates
 */
class LootGenerator(
    private val itemRepository: ItemRepository
) {
    /**
     * Generates loot from a loot table with source-based quality modifiers.
     *
     * @param lootTable The loot table to roll
     * @param source The source type (affects quality)
     * @param random Random instance for reproducible tests
     * @return List of generated item instances
     */
    fun generateLoot(
        lootTable: LootTable,
        source: LootSource = LootSource.COMMON_MOB,
        random: Random = Random.Default
    ): Result<List<ItemInstance>> {
        return try {
            val sourceModifier = getSourceQualityModifier(source)
            val modifiedTable = lootTable.copy(qualityModifier = lootTable.qualityModifier + sourceModifier)
            val drops = modifiedTable.generateDrops(random)

            val instances = drops.mapNotNull { (templateId, quality, quantity) ->
                createInstance(templateId, quality, quantity).getOrNull()
            }

            Result.success(instances)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a specific quest item with guaranteed quality.
     *
     * @param templateId The item template ID
     * @param quality Override quality (defaults to 7 for quest items)
     * @param quantity Number to generate
     * @return Result containing the item instance
     */
    fun generateQuestItem(
        templateId: String,
        quality: Int = 7,
        quantity: Int = 1
    ): Result<ItemInstance> {
        return createInstance(templateId, quality, quantity)
    }

    /**
     * Generates currency drop (gold) based on source type.
     *
     * @param baseAmount Base gold amount before variance
     * @param source Source type (affects multiplier)
     * @param random Random instance for reproducible tests
     * @return Amount of gold to drop
     */
    fun generateGoldDrop(
        baseAmount: Int,
        source: LootSource = LootSource.COMMON_MOB,
        random: Random = Random.Default
    ): Int {
        val multiplier = when (source) {
            LootSource.COMMON_MOB -> 1.0
            LootSource.ELITE_MOB -> 1.5
            LootSource.BOSS -> 3.0
            LootSource.CHEST -> 2.0
            LootSource.QUEST -> 5.0
            LootSource.FEATURE -> 0.5
        }

        // Variance: 80% to 120% of base amount
        val variance = random.nextDouble(0.8, 1.2)
        return (baseAmount * multiplier * variance).toInt().coerceAtLeast(1)
    }

    /**
     * Creates an item instance from template with proper charges for consumables.
     */
    private fun createInstance(
        templateId: String,
        quality: Int,
        quantity: Int
    ): Result<ItemInstance> {
        return try {
            val template = itemRepository.findTemplateById(templateId)
                .getOrElse { return Result.failure(it) }
                ?: return Result.failure(IllegalArgumentException("Template not found: $templateId"))

            val charges = calculateCharges(template)
            val instance = ItemInstance(
                templateId = templateId,
                quality = quality.coerceIn(1, 10),
                charges = charges,
                quantity = quantity
            )

            Result.success(instance)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Calculates initial charges for an item based on template.
     * Returns null for non-chargeable items.
     */
    private fun calculateCharges(template: ItemTemplate): Int? {
        return when (template.type) {
            ItemType.CONSUMABLE -> {
                // Consumables have 1 charge by default (single use)
                template.getPropertyInt("charges", 1)
            }
            ItemType.TOOL -> {
                // Tools have durability charges
                template.getPropertyInt("durability", 10)
            }
            ItemType.SPELL_BOOK, ItemType.SKILL_BOOK -> {
                // Books can be read multiple times
                template.getPropertyInt("charges", 3)
            }
            else -> null // Other items don't use charges
        }
    }

    /**
     * Gets quality modifier based on loot source.
     */
    private fun getSourceQualityModifier(source: LootSource): Int {
        return when (source) {
            LootSource.COMMON_MOB -> 0
            LootSource.ELITE_MOB -> 1
            LootSource.BOSS -> 2
            LootSource.CHEST -> 1
            LootSource.QUEST -> 2
            LootSource.FEATURE -> 0
        }
    }

    companion object {
        /**
         * Creates a standard loot table for a common mob with given template IDs.
         */
        fun createCommonMobTable(
            commonDrops: List<String>,
            uncommonDrops: List<String> = emptyList()
        ): LootTable {
            return LootTable.commonBias(
                commonIds = commonDrops,
                uncommonIds = uncommonDrops
            ).copy(guaranteedDrops = 0, maxDrops = 2)
        }

        /**
         * Creates a loot table for elite mobs with better drops.
         */
        fun createEliteMobTable(
            commonDrops: List<String>,
            uncommonDrops: List<String>,
            rareDrops: List<String> = emptyList()
        ): LootTable {
            val entries = buildList {
                commonDrops.forEach {
                    add(com.jcraw.mud.core.LootEntry(it, weight = 50, minQuality = 3, maxQuality = 8))
                }
                uncommonDrops.forEach {
                    add(com.jcraw.mud.core.LootEntry(it, weight = 35, minQuality = 4, maxQuality = 9))
                }
                rareDrops.forEach {
                    add(com.jcraw.mud.core.LootEntry(it, weight = 15, minQuality = 5, maxQuality = 10))
                }
            }
            return LootTable(entries, guaranteedDrops = 1, maxDrops = 3)
        }

        /**
         * Creates a treasure chest loot table with guaranteed drops.
         */
        fun createChestTable(
            guaranteedItems: List<String>,
            bonusItems: List<String> = emptyList()
        ): LootTable {
            val entries = buildList {
                guaranteedItems.forEach {
                    add(com.jcraw.mud.core.LootEntry(it, weight = 1, minQuality = 4, maxQuality = 8, dropChance = 1.0))
                }
                bonusItems.forEach {
                    add(com.jcraw.mud.core.LootEntry(it, weight = 1, minQuality = 3, maxQuality = 7, dropChance = 0.5))
                }
            }
            return LootTable(entries, guaranteedDrops = guaranteedItems.size)
        }
    }
}
