package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val name: String,
    val currentRoomId: RoomId,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val stats: Stats = Stats(),
    val inventory: List<Entity.Item> = emptyList(),
    val equippedWeapon: Entity.Item? = null,
    val equippedArmor: Entity.Item? = null,
    val skills: Map<String, Int> = emptyMap(),
    val properties: Map<String, String> = emptyMap()
) {
    fun addToInventory(item: Entity.Item): PlayerState = copy(inventory = inventory + item)

    fun removeFromInventory(itemId: String): PlayerState = copy(inventory = inventory.filter { it.id != itemId })

    fun getInventoryItem(itemId: String): Entity.Item? = inventory.find { it.id == itemId }

    fun hasItem(itemId: String): Boolean = inventory.any { it.id == itemId }

    fun moveToRoom(roomId: RoomId): PlayerState = copy(currentRoomId = roomId)

    fun takeDamage(damage: Int): PlayerState = copy(health = (health - damage).coerceAtLeast(0))

    fun heal(amount: Int): PlayerState = copy(health = (health + amount).coerceAtMost(maxHealth))

    fun isDead(): Boolean = health <= 0

    fun getSkillLevel(skillName: String): Int = skills[skillName] ?: 0

    fun setSkillLevel(skillName: String, level: Int): PlayerState = copy(skills = skills + (skillName to level))

    fun equipWeapon(weapon: Entity.Item): PlayerState {
        // Add old weapon back to inventory if present
        val updatedInventory = if (equippedWeapon != null) {
            inventory + equippedWeapon
        } else {
            inventory
        }
        // Remove new weapon from inventory and equip it
        return copy(
            equippedWeapon = weapon,
            inventory = updatedInventory.filter { it.id != weapon.id }
        )
    }

    fun unequipWeapon(): PlayerState {
        return if (equippedWeapon != null) {
            copy(
                equippedWeapon = null,
                inventory = inventory + equippedWeapon
            )
        } else {
            this
        }
    }

    fun getWeaponDamageBonus(): Int = equippedWeapon?.damageBonus ?: 0

    fun equipArmor(armor: Entity.Item): PlayerState {
        // Add old armor back to inventory if present
        val updatedInventory = if (equippedArmor != null) {
            inventory + equippedArmor
        } else {
            inventory
        }
        // Remove new armor from inventory and equip it
        return copy(
            equippedArmor = armor,
            inventory = updatedInventory.filter { it.id != armor.id }
        )
    }

    fun unequipArmor(): PlayerState {
        return if (equippedArmor != null) {
            copy(
                equippedArmor = null,
                inventory = inventory + equippedArmor
            )
        } else {
            this
        }
    }

    fun getArmorDefenseBonus(): Int = equippedArmor?.defenseBonus ?: 0

    fun useConsumable(item: Entity.Item): PlayerState {
        return if (item.isConsumable) {
            val newHealth = (health + item.healAmount).coerceAtMost(maxHealth)
            copy(
                health = newHealth,
                inventory = inventory.filter { it.id != item.id }
            )
        } else {
            this
        }
    }
}