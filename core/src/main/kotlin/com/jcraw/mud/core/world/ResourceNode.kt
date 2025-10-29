package com.jcraw.mud.core.world

import kotlinx.serialization.Serializable

/**
 * Resource node for gathering system
 * Ties to ItemTemplate from existing items system
 * Respawn time enables renewable resources for sustained exploration
 */
@Serializable
data class ResourceNode(
    val id: String,
    val templateId: String,
    val quantity: Int,
    val respawnTime: Int? = null,
    val description: String = "",
    val timeSinceHarvest: Int = 0
) {
    /**
     * Harvest resource, reducing quantity
     * Returns updated node and quantity harvested
     */
    fun harvest(amount: Int = 1): Pair<ResourceNode, Int> {
        val harvestedAmount = minOf(amount, quantity)
        val newQuantity = quantity - harvestedAmount
        val updatedNode = copy(
            quantity = newQuantity,
            timeSinceHarvest = if (newQuantity == 0 && respawnTime != null) 0 else timeSinceHarvest
        )
        return updatedNode to harvestedAmount
    }

    /**
     * Tick for respawn timer
     * Returns updated node and whether it respawned
     */
    fun tick(ticks: Int = 1): Pair<ResourceNode, Boolean> {
        if (quantity > 0 || respawnTime == null) {
            return this to false
        }

        val newTime = timeSinceHarvest + ticks
        if (newTime >= respawnTime) {
            // Respawn to full quantity (implementation can vary)
            val respawnedNode = copy(
                quantity = 5, // Default respawn quantity
                timeSinceHarvest = 0
            )
            return respawnedNode to true
        }

        return copy(timeSinceHarvest = newTime) to false
    }

    /**
     * Check if node is depleted
     */
    fun isDepleted(): Boolean = quantity <= 0

    /**
     * Check if node can respawn
     */
    fun canRespawn(): Boolean = respawnTime != null
}
