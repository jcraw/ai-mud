package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Room
import com.jcraw.mud.core.WorldState
import kotlin.random.Random

/**
 * Manages corpse decay over time.
 *
 * Corpses tick down their decay timer each turn. When timer reaches 0:
 * - Corpse is removed from the room
 * - Items have a chance to be dropped to the room (30% per item)
 * - Remaining items are destroyed
 */
class CorpseDecayManager(
    private val itemDropChance: Float = 0.3f,
    private val random: Random = Random.Default
) {

    /**
     * Result of decay tick
     */
    data class DecayResult(
        val worldState: WorldState,
        val decayedCorpses: List<Entity.Corpse>,
        val droppedItems: Map<String, List<Entity.Item>> // roomId -> items
    )

    /**
     * Tick all corpses in the world, decaying them and removing expired ones.
     *
     * @param worldState Current world state
     * @return DecayResult with updated world and decay information
     */
    fun tickDecay(worldState: WorldState): DecayResult {
        val decayedCorpses = mutableListOf<Entity.Corpse>()
        val droppedItems = mutableMapOf<String, MutableList<Entity.Item>>()
        var updatedWorld = worldState

        // Process each room
        worldState.rooms.values.forEach { room ->
            val (updatedRoom, roomDecayed, roomDropped) = processRoomCorpses(room)

            if (updatedRoom != room) {
                updatedWorld = updatedWorld.updateRoom(updatedRoom)
            }

            decayedCorpses.addAll(roomDecayed)
            if (roomDropped.isNotEmpty()) {
                droppedItems[room.id] = roomDropped.toMutableList()
            }
        }

        return DecayResult(
            worldState = updatedWorld,
            decayedCorpses = decayedCorpses,
            droppedItems = droppedItems
        )
    }

    /**
     * Process all corpses in a single room
     */
    private fun processRoomCorpses(room: Room): Triple<Room, List<Entity.Corpse>, List<Entity.Item>> {
        val corpses = room.entities.filterIsInstance<Entity.Corpse>()
        if (corpses.isEmpty()) {
            return Triple(room, emptyList(), emptyList())
        }

        val decayedCorpses = mutableListOf<Entity.Corpse>()
        val droppedItems = mutableListOf<Entity.Item>()
        var updatedRoom = room

        corpses.forEach { corpse ->
            // Tick the corpse
            val tickedCorpse = corpse.tick()

            if (tickedCorpse == null) {
                // Corpse has fully decayed
                decayedCorpses.add(corpse)

                // Drop some items to the room
                val itemsToDrop = corpse.contents.filter { random.nextFloat() < itemDropChance }
                droppedItems.addAll(itemsToDrop)

                // Remove corpse from room
                updatedRoom = updatedRoom.removeEntity(corpse.id)

                // Add dropped items to room
                itemsToDrop.forEach { item ->
                    updatedRoom = updatedRoom.addEntity(item)
                }
            } else {
                // Update corpse with decremented timer
                updatedRoom = updatedRoom.removeEntity(corpse.id).addEntity(tickedCorpse)
            }
        }

        return Triple(updatedRoom, decayedCorpses, droppedItems)
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
