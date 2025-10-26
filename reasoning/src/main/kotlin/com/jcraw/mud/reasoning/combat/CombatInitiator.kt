package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*

/**
 * Handles combat initiation based on disposition thresholds
 *
 * Replaces modal combat with emergent behavior: hostile entities (disposition < -75)
 * automatically engage in combat with nearby players.
 */
object CombatInitiator {

    /**
     * Threshold for hostile behavior
     * NPCs with disposition below this value will initiate combat
     */
    const val HOSTILE_THRESHOLD = -75

    /**
     * Find all hostile entities in the specified room
     *
     * @param roomId The room to check for hostile entities
     * @param worldState Current world state
     * @param playerId The player to check hostility toward (defaults to main player)
     * @return List of entity IDs that are hostile toward the player
     */
    fun checkForHostileEntities(
        roomId: RoomId,
        worldState: WorldState,
        playerId: PlayerId = worldState.player.id
    ): List<String> {
        val room = worldState.getRoom(roomId) ?: return emptyList()

        return room.entities
            .filterIsInstance<Entity.NPC>()
            .filter { npc -> isHostile(npc, playerId) }
            .map { it.id }
    }

    /**
     * Check if a specific NPC is hostile toward the player
     *
     * Uses disposition from SocialComponent if available, falls back to legacy isHostile flag
     *
     * @param npc The NPC to check
     * @param playerId The player to check hostility toward (unused in V1, for future per-player tracking)
     * @return true if the NPC should engage in combat
     */
    fun isHostile(npc: Entity.NPC, playerId: PlayerId = ""): Boolean {
        // Check component-based disposition first
        val socialComponent = npc.getSocialComponent()
        if (socialComponent != null) {
            return socialComponent.disposition < HOSTILE_THRESHOLD
        }

        // Fall back to legacy isHostile flag
        return npc.isHostile
    }

    /**
     * Check if the player is in a room with any hostile entities
     *
     * @param worldState Current world state
     * @param playerId The player to check (defaults to main player)
     * @return true if there are hostile entities in the player's current room
     */
    fun hasHostileEntitiesInCurrentRoom(
        worldState: WorldState,
        playerId: PlayerId = worldState.player.id
    ): Boolean {
        val playerState = worldState.getPlayer(playerId) ?: return false
        val hostileEntities = checkForHostileEntities(playerState.currentRoomId, worldState, playerId)
        return hostileEntities.isNotEmpty()
    }

    /**
     * Get all hostile entities in the player's current room
     *
     * @param worldState Current world state
     * @param playerId The player to check (defaults to main player)
     * @return List of hostile entity IDs
     */
    fun getHostileEntitiesInCurrentRoom(
        worldState: WorldState,
        playerId: PlayerId = worldState.player.id
    ): List<String> {
        val playerState = worldState.getPlayer(playerId) ?: return emptyList()
        return checkForHostileEntities(playerState.currentRoomId, worldState, playerId)
    }
}
