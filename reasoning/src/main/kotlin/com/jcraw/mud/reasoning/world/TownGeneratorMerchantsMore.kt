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
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*

/**
 * Blacksmith + general store merchant factories (MUD-034g pure move).
 */
internal object TownGeneratorMerchantsMore {

    val GARETH_DESC =
        "A muscular human covered in soot and sweat. The clang of his hammer echoes through the town."
    val MIRA_DESC =
        "A cheerful halfling woman who always has a smile and a story to share."

    fun blacksmithStock(): List<ItemInstance> = listOf(
        TownGeneratorMerchants.createItemInstance("iron_sword", quality = 5, quantity = 4),
        TownGeneratorMerchants.createItemInstance("iron_axe", quality = 5, quantity = 3),
        TownGeneratorMerchants.createItemInstance("wooden_bow", quality = 5, quantity = 2),
        TownGeneratorMerchants.createItemInstance("steel_sword", quality = 6, quantity = 1)
    )

    fun createBlacksmith(): Entity.NPC {
        val trading = TradingComponent(800, blacksmithStock(), true, 1.1)
        val social = SocialComponent(0, "master craftsman", listOf("proud", "skilled", "direct"))
        return Entity.NPC(
            id = "npc_town_blacksmith",
            name = "Gareth the Smith",
            description = GARETH_DESC,
            isHostile = false,
            health = 100,
            maxHealth = 100,
            stats = Stats(strength = 18, constitution = 16, dexterity = 12),
            components = mapOf(ComponentType.TRADING to trading, ComponentType.SOCIAL to social)
        )
    }

    fun generalStock(): List<ItemInstance> = listOf(
        TownGeneratorMerchants.createItemInstance("torch", quality = 5, quantity = 20),
        TownGeneratorMerchants.createItemInstance("rope_50ft", quality = 5, quantity = 5),
        TownGeneratorMerchants.createItemInstance("lockpick_set", quality = 5, quantity = 3),
        TownGeneratorMerchants.createItemInstance("rations", quality = 5, quantity = 15)
    )

    fun createGeneralStore(): Entity.NPC {
        val trading = TradingComponent(300, generalStock(), true, 1.0)
        val social = SocialComponent(0, "chatty shopkeeper", listOf("friendly", "curious", "gossipy"))
        return Entity.NPC(
            id = "npc_town_general_store",
            name = "Mira Goodbarrel",
            description = MIRA_DESC,
            isHostile = false,
            health = 40,
            maxHealth = 40,
            stats = Stats(charisma = 16, wisdom = 13),
            components = mapOf(ComponentType.TRADING to trading, ComponentType.SOCIAL to social)
        )
    }
}
