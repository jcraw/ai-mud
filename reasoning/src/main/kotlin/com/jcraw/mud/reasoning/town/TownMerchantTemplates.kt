package com.jcraw.mud.reasoning.town

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.TradingComponent
import java.util.UUID

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
    fun createPotionsMerchant(): Entity.NPC {
        val stock = listOf(
            // Health potions
            createItemInstance("health_potion_minor", quantity = 10),
            createItemInstance("health_potion_standard", quantity = 8),
            createItemInstance("health_potion_greater", quantity = 5),
            // Mana potions (for future mana system)
            createItemInstance("mana_potion_minor", quantity = 10),
            createItemInstance("mana_potion_standard", quantity = 8),
            // Stamina potions
            createItemInstance("stamina_potion", quantity = 6)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 500,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0, // NEUTRAL
            personality = "Cheerful alchemist who loves brewing potions",
            traits = listOf("Helpful", "Knowledgeable", "Chatty")
        )

        return Entity.NPC(
            id = "merchant_potions_${UUID.randomUUID()}",
            name = "Potions Merchant",
            description = "A friendly alchemist with colorful vials lining the shelves behind them.",
            health = 50,
            maxHealth = 50,
            strength = 8,
            agility = 10,
            intelligence = 15,
            wisdom = 12,
            vitality = 10,
            charisma = 14,
            skills = mapOf("Alchemy" to 50, "Herbalism" to 40),
            components = listOf(tradingComponent, socialComponent),
            aiPrompt = "You are a cheerful potion merchant. You love discussing the properties of potions and herbs."
        )
    }

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
    fun createArmorMerchant(): Entity.NPC {
        val stock = listOf(
            // Leather armor
            createItemInstance("leather_armor", quantity = 5),
            createItemInstance("leather_helm", quantity = 5),
            createItemInstance("leather_boots", quantity = 5),
            // Chainmail armor
            createItemInstance("chainmail_armor", quantity = 3),
            createItemInstance("chainmail_helm", quantity = 3),
            // Plate armor
            createItemInstance("plate_armor", quantity = 2),
            createItemInstance("plate_helm", quantity = 2),
            // Shields
            createItemInstance("wooden_shield", quantity = 4),
            createItemInstance("steel_shield", quantity = 2)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 1000,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0, // NEUTRAL
            personality = "Gruff but fair armor smith with decades of experience",
            traits = listOf("Professional", "Honest", "Prideful")
        )

        return Entity.NPC(
            id = "merchant_armor_${UUID.randomUUID()}",
            name = "Armor Merchant",
            description = "A sturdy smith with calloused hands, surrounded by gleaming armor pieces.",
            health = 80,
            maxHealth = 80,
            strength = 16,
            agility = 8,
            intelligence = 10,
            wisdom = 12,
            vitality = 18,
            charisma = 8,
            skills = mapOf("Blacksmithing" to 60, "Armorcraft" to 70),
            components = listOf(tradingComponent, socialComponent),
            aiPrompt = "You are a gruff armor merchant. You take pride in your work and know armor inside out."
        )
    }

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
    fun createBlacksmith(): Entity.NPC {
        val stock = listOf(
            // Swords
            createItemInstance("shortsword", quantity = 6),
            createItemInstance("longsword", quantity = 4),
            createItemInstance("greatsword", quantity = 2),
            // Axes
            createItemInstance("handaxe", quantity = 5),
            createItemInstance("battleaxe", quantity = 3),
            // Bows
            createItemInstance("shortbow", quantity = 4),
            createItemInstance("longbow", quantity = 2),
            // Daggers
            createItemInstance("dagger", quantity = 8),
            createItemInstance("iron_dagger", quantity = 5)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 800,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0, // NEUTRAL
            personality = "Master weaponsmith who speaks through their craft",
            traits = listOf("Focused", "Perfectionist", "Reserved")
        )

        return Entity.NPC(
            id = "merchant_blacksmith_${UUID.randomUUID()}",
            name = "Blacksmith",
            description = "A muscular smith hammering at the forge, surrounded by gleaming blades.",
            health = 100,
            maxHealth = 100,
            strength = 18,
            agility = 10,
            intelligence = 12,
            wisdom = 10,
            vitality = 20,
            charisma = 6,
            skills = mapOf("Blacksmithing" to 80, "Weaponcraft" to 75),
            components = listOf(tradingComponent, socialComponent),
            aiPrompt = "You are a master blacksmith. You speak little but your work speaks volumes."
        )
    }

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
    fun createInnkeeper(): Entity.NPC {
        val stock = listOf(
            // Food
            createItemInstance("bread", quantity = 20),
            createItemInstance("dried_meat", quantity = 15),
            createItemInstance("cheese", quantity = 15),
            createItemInstance("water_flask", quantity = 10),
            // Supplies
            createItemInstance("torch", quantity = 30),
            createItemInstance("rope", quantity = 10),
            createItemInstance("bedroll", quantity = 5),
            // Tools
            createItemInstance("lockpick_set", quantity = 3),
            createItemInstance("tinderbox", quantity = 8)
        )

        val tradingComponent = TradingComponent(
            merchantGold = 300,
            stock = stock,
            buyAnything = true,
            priceModBase = 1.0
        )

        val socialComponent = SocialComponent(
            disposition = 0, // NEUTRAL
            personality = "Warm innkeeper who knows all the local gossip and dungeon lore",
            traits = listOf("Friendly", "Chatty", "Observant", "Wise")
        )

        return Entity.NPC(
            id = "merchant_innkeeper_${UUID.randomUUID()}",
            name = "Innkeeper",
            description = "A welcoming host with a knowing smile, always ready with a tale or two.",
            health = 60,
            maxHealth = 60,
            strength = 10,
            agility = 12,
            intelligence = 14,
            wisdom = 16,
            vitality = 12,
            charisma = 18,
            skills = mapOf("Lore" to 60, "Persuasion" to 40, "Cooking" to 50),
            components = listOf(tradingComponent, socialComponent),
            aiPrompt = "You are a friendly innkeeper. You love sharing stories about the Ancient Abyss Dungeon and giving hints to adventurers. You know the dungeon has four regions (Upper/Mid/Lower/Abyssal Core) and mention the hidden exit in the Mid Depths for clever explorers."
        )
    }

    /**
     * Helper: Create ItemInstance from template ID.
     *
     * Creates instance with standard quality (5) and unique ID.
     *
     * @param templateId Template ID (must exist in item repository)
     * @param quantity Number of items in stack
     * @param quality Item quality (1-10, default 5)
     * @return ItemInstance
     */
    private fun createItemInstance(
        templateId: String,
        quantity: Int = 1,
        quality: Int = 5
    ): ItemInstance {
        return ItemInstance(
            id = "${templateId}_${UUID.randomUUID()}",
            templateId = templateId,
            quality = quality,
            quantity = quantity,
            charges = null,
            bonuses = emptyMap()
        )
    }

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
