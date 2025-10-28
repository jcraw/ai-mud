package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.Room
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Manages corpse decay over time.
 *
 * Corpses tick down their decay timer each turn. When timer reaches 0:
 * - Corpse is removed from the room
 * - All items and gold are lost (destroyed with corpse)
 *
 * Note: Items are not dropped to room as entities in V2 system.
 * TODO: Future enhancement could store items in room loot containers.
 */
class CorpseDecayManager(
    private val random: Random = Random.Default
) {

    /**
     * Result of decay tick
     */
    data class DecayResult(
        val worldState: WorldState,
        val decayedCorpses: List<Entity.Corpse>,
        val destroyedItems: Map<String, List<ItemInstance>>, // roomId -> items (for logging)
        val destroyedGold: Map<String, Int> // roomId -> gold amount (for logging)
    )

    /**
     * Tick all corpses in the world, decaying them and removing expired ones.
     *
     * @param worldState Current world state
     * @return DecayResult with updated world and decay information
     */
    fun tickDecay(worldState: WorldState): DecayResult {
        val decayedCorpses = mutableListOf<Entity.Corpse>()
        val destroyedItems = mutableMapOf<String, MutableList<ItemInstance>>()
        val destroyedGold = mutableMapOf<String, Int>()
        var updatedWorld = worldState

        // Process each room
        worldState.rooms.values.forEach { room ->
            val result = processRoomCorpses(room)

            if (result.updatedRoom != room) {
                updatedWorld = updatedWorld.updateRoom(result.updatedRoom)
            }

            decayedCorpses.addAll(result.decayedCorpses)
            if (result.destroyedItems.isNotEmpty()) {
                destroyedItems[room.id] = result.destroyedItems.toMutableList()
            }
            if (result.destroyedGold > 0) {
                destroyedGold[room.id] = result.destroyedGold
            }
        }

        return DecayResult(
            worldState = updatedWorld,
            decayedCorpses = decayedCorpses,
            destroyedItems = destroyedItems,
            destroyedGold = destroyedGold
        )
    }

    /**
     * Result of processing corpses in a single room
     */
    private data class RoomDecayResult(
        val updatedRoom: Room,
        val decayedCorpses: List<Entity.Corpse>,
        val destroyedItems: List<ItemInstance>,
        val destroyedGold: Int
    )

    /**
     * Process all corpses in a single room
     */
    private fun processRoomCorpses(room: Room): RoomDecayResult {
        val corpses = room.entities.filterIsInstance<Entity.Corpse>()
        if (corpses.isEmpty()) {
            return RoomDecayResult(room, emptyList(), emptyList(), 0)
        }

        val decayedCorpses = mutableListOf<Entity.Corpse>()
        val destroyedItems = mutableListOf<ItemInstance>()
        var destroyedGold = 0
        var updatedRoom = room

        corpses.forEach { corpse ->
            // Tick the corpse
            val tickedCorpse = corpse.tick()

            if (tickedCorpse == null) {
                // Corpse has fully decayed
                decayedCorpses.add(corpse)
                destroyedItems.addAll(corpse.contents)
                destroyedGold += corpse.goldAmount

                // Remove corpse from room (items and gold destroyed)
                updatedRoom = updatedRoom.removeEntity(corpse.id)
            } else {
                // Update corpse with decremented timer
                updatedRoom = updatedRoom.removeEntity(corpse.id).addEntity(tickedCorpse)
            }
        }

        return RoomDecayResult(updatedRoom, decayedCorpses, destroyedItems, destroyedGold)
    }

    /**
     * Get all corpses in a specific room
     */
    fun getCorpsesInRoom(room: Room): List<Entity.Corpse> {
        return room.entities.filterIsInstance<Entity.Corpse>()
    }

    /**
     * Get total number of corpses in the world
     */
    fun getTotalCorpses(worldState: WorldState): Int {
        return worldState.rooms.values.sumOf { room ->
            room.entities.count { it is Entity.Corpse }
        }
    }
}
