package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemType
import kotlin.random.Random

/**
 * Generates procedural items (weapons, armor, consumables)
 */
class ItemGenerator(
    private val theme: DungeonTheme,
    private val random: Random = Random.Default
) {

    private val weaponTypes = listOf(
        "Sword" to (3..8),
        "Dagger" to (2..5),
        "Axe" to (4..9),
        "Mace" to (3..7),
        "Spear" to (3..6),
        "Hammer" to (4..8)
    )

    private val armorTypes = listOf(
        "Leather Armor" to (1..3),
        "Chainmail" to (3..5),
        "Plate Armor" to (5..7),
        "Scale Mail" to (3..5),
        "Studded Leather" to (2..4)
    )

    private val consumableTypes = listOf(
        "Health Potion" to (20..40),
        "Elixir" to (30..50),
        "Salve" to (15..30),
        "Tonic" to (25..45)
    )

    /**
     * Generate random weapon
     */
    fun generateWeapon(id: String): Entity.Item {
        val (baseType, damageRange) = weaponTypes.random(random)
        val prefix = theme.itemNamePrefixes.random(random)
        val name = "$prefix $baseType"
        val damage = random.nextInt(damageRange.first, damageRange.last + 1)

        return Entity.Item(
            id = id,
            name = name,
            description = "A $name with a keen edge",
            isPickupable = true,
            isUsable = true,
            itemType = ItemType.WEAPON,
            damageBonus = damage
        )
    }

    /**
     * Generate random armor
     */
    fun generateArmor(id: String): Entity.Item {
        val (baseType, defenseRange) = armorTypes.random(random)
        val prefix = theme.itemNamePrefixes.random(random)
        val name = "$prefix $baseType"
        val defense = random.nextInt(defenseRange.first, defenseRange.last + 1)

        return Entity.Item(
            id = id,
            name = name,
            description = "Protective $name that has seen better days",
            isPickupable = true,
            isUsable = true,
            itemType = ItemType.ARMOR,
            defenseBonus = defense
        )
    }

    /**
     * Generate random consumable (healing item)
     */
    fun generateConsumable(id: String): Entity.Item {
        val (baseType, healRange) = consumableTypes.random(random)
        val prefix = theme.itemNamePrefixes.random(random)
        val name = "$prefix $baseType"
        val healAmount = random.nextInt(healRange.first, healRange.last + 1)

        return Entity.Item(
            id = id,
            name = name,
            description = "A $name that restores vitality",
            isPickupable = true,
            isUsable = true,
            itemType = ItemType.CONSUMABLE,
            healAmount = healAmount,
            isConsumable = true
        )
    }

    /**
     * Generate random treasure (misc item)
     */
    fun generateTreasure(id: String): Entity.Item {
        val treasureTypes = listOf(
            "Gold Pouch",
            "Silver Coins",
            "Jeweled Ring",
            "Ancient Amulet",
            "Gemstone",
            "Ornate Key"
        )

        val treasureName = treasureTypes.random(random)
        val prefix = theme.itemNamePrefixes.random(random)
        val name = "$prefix $treasureName"

        return Entity.Item(
            id = id,
            name = name,
            description = "A valuable $name",
            isPickupable = true,
            itemType = ItemType.MISC
        )
    }

    /**
     * Generate random loot for a room
     * Returns 0-3 items of various types
     */
    fun generateRoomLoot(roomId: String): List<Entity.Item> {
        val itemCount = random.nextInt(0, 4)  // 0-3 items
        val items = mutableListOf<Entity.Item>()

        repeat(itemCount) { index ->
            val itemId = "${roomId}_item_$index"
            val roll = random.nextInt(100)

            val item = when {
                roll < 30 -> generateConsumable(itemId)  // 30% consumable
                roll < 50 -> generateWeapon(itemId)      // 20% weapon
                roll < 65 -> generateArmor(itemId)       // 15% armor
                else -> generateTreasure(itemId)         // 35% treasure
            }

            items.add(item)
        }

        return items
    }
}
