package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
data class PlayerState(
    val name: String,
    val currentRoomId: RoomId,
    val health: Int = 100,
    val maxHealth: Int = 100,
    val inventory: List<Entity.Item> = emptyList(),
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
}