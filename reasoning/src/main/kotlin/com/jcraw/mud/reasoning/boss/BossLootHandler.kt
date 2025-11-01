package com.jcraw.mud.reasoning.boss

import com.jcraw.mud.core.*

/**
 * Handles loot generation for boss entities
 * Bosses drop unique legendary items based on their designation
 */
class BossLootHandler {
    /**
     * Generate loot for a boss entity
     * Returns list of ItemInstances (uses template IDs that must exist in ItemRepository)
     *
     * @param boss The boss NPC entity
     * @return List of ItemInstance for boss loot
     */
    fun generateBossLoot(boss: Entity.NPC): Result<List<ItemInstance>> {
        return try {
            if (!boss.bossDesignation.isValid()) {
                return Result.success(emptyList()) // Not a valid boss
            }

            val loot = when (boss.bossDesignation.victoryFlag) {
                "abyssal_lord_defeated" -> {
                    // Special: Abyss Heart
                    listOf(createAbyssHeart())
                }
                else -> {
                    // Standard legendary loot for other bosses
                    // TODO: Implement generic legendary loot generation
                    emptyList()
                }
            }

            Result.success(loot)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create the Abyss Heart legendary item
     * This is the victory condition item for the Ancient Abyss dungeon
     *
     * @return ItemInstance for Abyss Heart
     */
    fun createAbyssHeart(): ItemInstance {
        // NOTE: This requires an "abyss_heart" template to exist in the database
        // The template should be created by DungeonInitializer or inserted at startup
        return ItemInstance(
            templateId = "abyss_heart",
            quality = 10, // Max quality for legendary item
            charges = null, // Not consumable
            quantity = 1
        )
    }

    companion object {
        /**
         * Abyss Heart template ID (for reference)
         * This template should be created with:
         * - name: "Abyss Heart"
         * - type: QUEST
         * - rarity: LEGENDARY
         * - equipSlot: ACCESSORY_1
         * - properties: {
         *     "strength_bonus": "50",
         *     "dexterity_bonus": "50",
         *     "constitution_bonus": "50",
         *     "intelligence_bonus": "50",
         *     "wisdom_bonus": "50",
         *     "charisma_bonus": "50",
         *     "xp_multiplier": "2.0",
         *     "weight": "0.1",
         *     "value": "100000"
         *   }
         * - description: "A pulsing crystalline heart torn from the Abyssal Lord. Power radiates from its depths."
         */
        const val ABYSS_HEART_TEMPLATE_ID = "abyss_heart"

        /**
         * Create the Abyss Heart template (for initialization)
         * This should be called during dungeon initialization
         */
        fun createAbyssHeartTemplate(): ItemTemplate {
            return ItemTemplate(
                id = ABYSS_HEART_TEMPLATE_ID,
                name = "Abyss Heart",
                type = ItemType.QUEST,
                tags = listOf("legendary", "quest", "unique", "artifact"),
                properties = mapOf(
                    "strength_bonus" to "50",
                    "dexterity_bonus" to "50",
                    "constitution_bonus" to "50",
                    "intelligence_bonus" to "50",
                    "wisdom_bonus" to "50",
                    "charisma_bonus" to "50",
                    "xp_multiplier" to "2.0",
                    "weight" to "0.1",
                    "value" to "100000"
                ),
                rarity = Rarity.LEGENDARY,
                description = "A pulsing crystalline heart torn from the Abyssal Lord. Power radiates from its depths, granting immense strength to its bearer.",
                equipSlot = EquipSlot.ACCESSORY_1
            )
        }
    }
}
