@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.town

import com.jcraw.mud.core.Entity

/**
 * Predefined merchant templates for town safe zones.
 *
 * Each merchant has:
 * - TradingComponent with finite gold and stock
 * - SocialComponent with NEUTRAL starting disposition
 * - Specific stock based on merchant type
 *
 * Merchants:
 * - Potions Merchant: Healing/mana potions (gold: 500)
 * - Armor Merchant: Leather/chainmail/plate armor (gold: 1000)
 * - Blacksmith: Weapons - swords/axes/bows (gold: 800)
 * - Innkeeper: Food/torches/rope/lore (gold: 300)
 *
 * Thin facade — bodies in TownMerchant* extracts (MUD-034n).
 */
object TownMerchantTemplates {

    /**
     * Create Potions Merchant NPC.
     *
     * Sells:
     * - Health potions (minor/standard/greater)
     * - Mana potions (when mana system exists)
     * - Stamina potions
     *
     * Starting gold: 500
     * Disposition: NEUTRAL (0)
     *
     * @return Entity.NPC merchant
     */
    fun createPotionsMerchant(): Entity.NPC = TownMerchantPotions.create()

    /**
     * Create Armor Merchant NPC.
     *
     * Sells:
     * - Leather armor (light)
     * - Chainmail armor (medium)
     * - Plate armor (heavy)
     * - Shields
     *
     * Starting gold: 1000
     * Disposition: NEUTRAL (0)
     *
     * @return Entity.NPC merchant
     */
    fun createArmorMerchant(): Entity.NPC = TownMerchantArmor.create()

    /**
     * Create Blacksmith NPC.
     *
     * Sells:
     * - Swords (short/long/great)
     * - Axes (hand/battle)
     * - Bows (short/longbow)
     * - Daggers
     *
     * Starting gold: 800
     * Disposition: NEUTRAL (0)
     *
     * @return Entity.NPC merchant
     */
    fun createBlacksmith(): Entity.NPC = TownMerchantBlacksmith.create()

    /**
     * Create Innkeeper NPC.
     *
     * Sells:
     * - Food (bread, meat, cheese)
     * - Torches
     * - Rope
     * - Basic supplies
     *
     * Also provides lore and hints about the dungeon.
     *
     * Starting gold: 300
     * Disposition: NEUTRAL (0)
     *
     * @return Entity.NPC merchant
     */
    fun createInnkeeper(): Entity.NPC = TownMerchantInn.create()

    /**
     * Get all town merchants.
     *
     * Returns list of 4 merchants for town population.
     *
     * @return List of all merchant NPCs
     */
    fun getAllMerchants(): List<Entity.NPC> {
        return listOf(
            createPotionsMerchant(),
            createArmorMerchant(),
            createBlacksmith(),
            createInnkeeper()
        )
    }
}
