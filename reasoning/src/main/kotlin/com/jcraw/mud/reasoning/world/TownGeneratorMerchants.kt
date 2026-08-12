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
import java.util.UUID

/**
 * Merchant NPC factories for [TownGenerator] (MUD-034g pure move).
 */
internal object TownGeneratorMerchants {

    val ALARA_DESC =
        "A middle-aged woman with stained robes and kind eyes. Her stall overflows with colorful vials."
    val THOREN_DESC =
        "A stocky dwarf with a thick beard. He examines each piece of armor with a critical eye."

    fun createItemInstance(
        templateId: String,
        quality: Int = 5,
        quantity: Int = 1
    ): ItemInstance {
        return ItemInstance(
            id = UUID.randomUUID().toString(),
            templateId = templateId,
            quality = quality,
            quantity = quantity
        )
    }

    fun createTownMerchants(): List<Entity.NPC> {
        return listOf(
            createPotionsMerchant(),
            createArmorMerchant(),
            TownGeneratorMerchantsMore.createBlacksmith(),
            TownGeneratorMerchantsMore.createGeneralStore()
        )
    }

    fun potionsStock(): List<ItemInstance> = listOf(
        createItemInstance("health_potion_minor", quality = 5, quantity = 10),
        createItemInstance("health_potion_moderate", quality = 5, quantity = 5),
        createItemInstance("mana_potion_minor", quality = 5, quantity = 8),
        createItemInstance("mana_potion_moderate", quality = 5, quantity = 3)
    )

    fun createPotionsMerchant(): Entity.NPC {
        val trading = TradingComponent(500, potionsStock(), false, 1.0)
        val social = SocialComponent(0, "friendly alchemist", listOf("helpful", "knowledgeable", "patient"))
        return Entity.NPC(
            id = "npc_town_potions_merchant",
            name = "Alara the Alchemist",
            description = ALARA_DESC,
            isHostile = false,
            health = 50,
            maxHealth = 50,
            stats = Stats(intelligence = 14, wisdom = 12),
            components = mapOf(ComponentType.TRADING to trading, ComponentType.SOCIAL to social)
        )
    }

    fun armorStock(): List<ItemInstance> = listOf(
        createItemInstance("leather_helmet", quality = 5, quantity = 3),
        createItemInstance("leather_chest", quality = 5, quantity = 3),
        createItemInstance("chainmail_chest", quality = 5, quantity = 2),
        createItemInstance("chainmail_legs", quality = 5, quantity = 2)
    )

    fun createArmorMerchant(): Entity.NPC {
        val trading = TradingComponent(1000, armorStock(), true, 1.2)
        val social = SocialComponent(0, "gruff merchant", listOf("practical", "honest", "businesslike"))
        return Entity.NPC(
            id = "npc_town_armor_merchant",
            name = "Thoren Ironfist",
            description = THOREN_DESC,
            isHostile = false,
            health = 80,
            maxHealth = 80,
            stats = Stats(strength = 16, constitution = 15),
            components = mapOf(ComponentType.TRADING to trading, ComponentType.SOCIAL to social)
        )
    }
}
