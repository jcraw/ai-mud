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

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.Stats
import com.jcraw.mud.core.TradingComponent
import java.util.UUID

/**
 * Armor merchant factory for [TownMerchantTemplates] (MUD-034n).
 */
internal object TownMerchantArmor {

    fun create(): Entity.NPC {
        val tradingComponent = TradingComponent(
            merchantGold = 1000,
            stock = stock(),
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
            stats = armorStats(),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    private fun stock(): List<ItemInstance> = listOf(
        // Leather armor
        TownMerchantItems.createItemInstance("leather_armor", quantity = 5),
        TownMerchantItems.createItemInstance("leather_helm", quantity = 5),
        TownMerchantItems.createItemInstance("leather_boots", quantity = 5),
        // Chainmail armor
        TownMerchantItems.createItemInstance("chainmail_armor", quantity = 3),
        TownMerchantItems.createItemInstance("chainmail_helm", quantity = 3),
        // Plate armor
        TownMerchantItems.createItemInstance("plate_armor", quantity = 2),
        TownMerchantItems.createItemInstance("plate_helm", quantity = 2),
        // Shields
        TownMerchantItems.createItemInstance("wooden_shield", quantity = 4),
        TownMerchantItems.createItemInstance("steel_shield", quantity = 2)
    )

    private fun armorStats(): Stats = Stats(
        strength = 16,
        dexterity = 8,
        intelligence = 10,
        wisdom = 12,
        constitution = 18,
        charisma = 8
    )
}
