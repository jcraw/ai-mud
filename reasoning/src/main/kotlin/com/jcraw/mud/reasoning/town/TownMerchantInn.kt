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
 * Innkeeper factory for [TownMerchantTemplates] (MUD-034n).
 */
internal object TownMerchantInn {

    fun create(): Entity.NPC {
        val tradingComponent = TradingComponent(
            merchantGold = 300,
            stock = stock(),
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
            stats = innkeeperStats(),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    private fun stock(): List<ItemInstance> = listOf(
        // Food
        TownMerchantItems.createItemInstance("bread", quantity = 20),
        TownMerchantItems.createItemInstance("dried_meat", quantity = 15),
        TownMerchantItems.createItemInstance("cheese", quantity = 15),
        TownMerchantItems.createItemInstance("water_flask", quantity = 10),
        // Supplies
        TownMerchantItems.createItemInstance("torch", quantity = 30),
        TownMerchantItems.createItemInstance("rope", quantity = 10),
        TownMerchantItems.createItemInstance("bedroll", quantity = 5),
        // Tools
        TownMerchantItems.createItemInstance("lockpick_set", quantity = 3),
        TownMerchantItems.createItemInstance("tinderbox", quantity = 8)
    )

    private fun innkeeperStats(): Stats = Stats(
        strength = 10,
        dexterity = 12,
        intelligence = 14,
        wisdom = 16,
        constitution = 12,
        charisma = 18
    )
}
