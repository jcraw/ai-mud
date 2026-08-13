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
 * Blacksmith factory for [TownMerchantTemplates] (MUD-034n).
 */
internal object TownMerchantBlacksmith {

    fun create(): Entity.NPC {
        val tradingComponent = TradingComponent(
            merchantGold = 800,
            stock = stock(),
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
            stats = blacksmithStats(),
            components = mapOf(
                ComponentType.TRADING to tradingComponent,
                ComponentType.SOCIAL to socialComponent
            )
        )
    }

    private fun stock(): List<ItemInstance> = listOf(
        // Swords
        TownMerchantItems.createItemInstance("shortsword", quantity = 6),
        TownMerchantItems.createItemInstance("longsword", quantity = 4),
        TownMerchantItems.createItemInstance("greatsword", quantity = 2),
        // Axes
        TownMerchantItems.createItemInstance("handaxe", quantity = 5),
        TownMerchantItems.createItemInstance("battleaxe", quantity = 3),
        // Bows
        TownMerchantItems.createItemInstance("shortbow", quantity = 4),
        TownMerchantItems.createItemInstance("longbow", quantity = 2),
        // Daggers
        TownMerchantItems.createItemInstance("dagger", quantity = 8),
        TownMerchantItems.createItemInstance("iron_dagger", quantity = 5)
    )

    private fun blacksmithStats(): Stats = Stats(
        strength = 18,
        dexterity = 10,
        intelligence = 12,
        wisdom = 10,
        constitution = 20,
        charisma = 6
    )
}
