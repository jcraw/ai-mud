package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import java.util.UUID

/**
 * Handles entity death, corpse creation, and permadeath mechanics.
 *
 * When an entity dies:
 * - NPC: Creates corpse with loot, removes from room
 * - Player: Creates corpse with inventory, triggers permadeath sequence
 */
class DeathHandler {

    /**
     * Result of death handling
     */
    sealed class DeathResult {
        /**
         * NPC died - corpse created with loot
         */
        data class NPCDeath(
            val corpse: Entity.Corpse,
            val updatedWorld: WorldState
        ) : DeathResult()

        /**
         * Player died - requires permadeath sequence
         */
        data class PlayerDeath(
            val corpse: Entity.Corpse,
            val playerId: PlayerId,
            val deathRoomId: RoomId,
            val updatedWorld: WorldState
        ) : DeathResult()
    }

    /**
     * Handle entity death, creating corpse and updating world state.
     *
     * @param entityId ID of the entity that died
     * @param worldState Current world state
     * @return DeathResult containing corpse and updated world, or null if entity not found
     */
    fun handleDeath(entityId: String, worldState: WorldState): DeathResult? {
        // Find which room the entity is in
        val roomWithEntity = worldState.rooms.values.firstOrNull { room ->
            room.entities.any { it.id == entityId }
        } ?: return null

        val entity = roomWithEntity.getEntity(entityId) ?: return null

        return when (entity) {
            is Entity.NPC -> handleNPCDeath(entity, roomWithEntity, worldState)
            is Entity.Player -> handlePlayerDeath(entity, roomWithEntity, worldState)
            else -> null // Items, features, corpses don't die
        }
    }

    /**
     * Handle NPC death - create corpse with basic loot
     */
    private fun handleNPCDeath(
        npc: Entity.NPC,
        room: Room,
        worldState: WorldState
    ): DeathResult.NPCDeath {
        // Create corpse with basic description
        val corpse = Entity.Corpse(
            id = "corpse_${npc.id}_${UUID.randomUUID()}",
            name = "Corpse of ${npc.name}",
            description = "The lifeless body of ${npc.name}.",
            contents = emptyList(), // NPCs don't carry items yet (basic item system)
            decayTimer = 100
        )

        // Remove NPC, add corpse
        val updatedRoom = room
            .removeEntity(npc.id)
            .addEntity(corpse)

        val updatedWorld = worldState.updateRoom(updatedRoom)

        return DeathResult.NPCDeath(corpse, updatedWorld)
    }

    /**
     * Handle player death - create corpse with inventory
     */
    private fun handlePlayerDeath(
        player: Entity.Player,
        room: Room,
        worldState: WorldState
    ): DeathResult.PlayerDeath {
        val playerState = worldState.getPlayer(player.playerId) ?: return DeathResult.PlayerDeath(
            corpse = createEmptyCorpse(player),
            playerId = player.playerId,
            deathRoomId = room.id,
            updatedWorld = worldState
        )

        // Collect all player items (inventory + equipped)
        val allItems = buildList {
            addAll(playerState.inventory)
            playerState.equippedWeapon?.let { add(it) }
            playerState.equippedArmor?.let { add(it) }
        }

        // Create corpse with player's belongings
        val corpse = Entity.Corpse(
            id = "corpse_${player.id}_${UUID.randomUUID()}",
            name = "Corpse of ${player.name}",
            description = "Your lifeless body. Your belongings are scattered nearby.",
            contents = allItems,
            decayTimer = 200  // Player corpses last longer
        )

        // Remove player entity from room (will respawn elsewhere)
        val updatedRoom = room
            .removeEntity(player.id)
            .addEntity(corpse)

        val updatedWorld = worldState.updateRoom(updatedRoom)

        return DeathResult.PlayerDeath(
            corpse = corpse,
            playerId = player.playerId,
            deathRoomId = room.id,
            updatedWorld = updatedWorld
        )
    }

    /**
     * Create empty corpse for edge cases
     */
    private fun createEmptyCorpse(player: Entity.Player): Entity.Corpse {
        return Entity.Corpse(
            id = "corpse_${player.id}_${UUID.randomUUID()}",
            name = "Corpse of ${player.name}",
            description = "A lifeless body.",
            contents = emptyList(),
            decayTimer = 200
        )
    }

    /**
     * Check if entity should die based on health
     */
    fun shouldDie(entity: Entity): Boolean {
        return when (entity) {
            is Entity.NPC -> entity.health <= 0
            is Entity.Player -> entity.health <= 0
            else -> false
        }
    }
}
