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
 * Potions merchant factory for [TownMerchantTemplates] (MUD-034n).
 */
internal object TownMerchantPotions {

    fun create(): Entity.NPC {
        val tradingComponent = TradingComponent(
            merchantGold = 500,
            stock = stock(),
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
            stats = potionStats(),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    private fun stock(): List<ItemInstance> = listOf(
        // Health potions
        TownMerchantItems.createItemInstance("health_potion_minor", quantity = 10),
        TownMerchantItems.createItemInstance("health_potion_standard", quantity = 8),
        TownMerchantItems.createItemInstance("health_potion_greater", quantity = 5),
        // Mana potions (for future mana system)
        TownMerchantItems.createItemInstance("mana_potion_minor", quantity = 10),
        TownMerchantItems.createItemInstance("mana_potion_standard", quantity = 8),
        // Stamina potions
        TownMerchantItems.createItemInstance("stamina_potion", quantity = 6)
    )

    private fun potionStats(): Stats = Stats(
        strength = 8,
        dexterity = 10,
        intelligence = 15,
        wisdom = 12,
        constitution = 10,
        charisma = 14
    )
}
