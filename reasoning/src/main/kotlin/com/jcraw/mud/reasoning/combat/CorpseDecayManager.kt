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

        // Process each space (V3)
        worldState.spaces.forEach { (spaceId, space) ->
            val result = processSpaceCorpses(spaceId, space, worldState)

            if (result.updatedWorld != worldState) {
                updatedWorld = result.updatedWorld
            }

            decayedCorpses.addAll(result.decayedCorpses)
            if (result.destroyedItems.isNotEmpty()) {
                destroyedItems[spaceId] = result.destroyedItems.toMutableList()
            }
            if (result.destroyedGold > 0) {
                destroyedGold[spaceId] = result.destroyedGold
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
     * Result of processing corpses in a single space
     */
    private data class SpaceDecayResult(
        val updatedWorld: WorldState,
        val decayedCorpses: List<Entity.Corpse>,
        val destroyedItems: List<ItemInstance>,
        val destroyedGold: Int
    )

    /**
     * Process all corpses in a single space (V3)
     */
    private fun processSpaceCorpses(spaceId: SpaceId, space: SpacePropertiesComponent, worldState: WorldState): SpaceDecayResult {
        val corpses = worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Corpse>()
        if (corpses.isEmpty()) {
            return SpaceDecayResult(worldState, emptyList(), emptyList(), 0)
        }

        val decayedCorpses = mutableListOf<Entity.Corpse>()
        val destroyedItems = mutableListOf<ItemInstance>()
        var destroyedGold = 0
        var updatedWorld = worldState

        corpses.forEach { corpse ->
            // Tick the corpse
            val tickedCorpse = corpse.tick()

            if (tickedCorpse == null) {
                // Corpse has fully decayed
                decayedCorpses.add(corpse)
                destroyedItems.addAll(corpse.contents)
                destroyedGold += corpse.goldAmount

                // Remove corpse from space (items and gold destroyed) (V3)
                updatedWorld = updatedWorld.removeEntityFromSpace(spaceId, corpse.id) ?: updatedWorld
            } else {
                // Update corpse with decremented timer (V3)
                updatedWorld = updatedWorld.updateEntity(tickedCorpse)
            }
        }

        return SpaceDecayResult(updatedWorld, decayedCorpses, destroyedItems, destroyedGold)
    }

    /**
     * Get all corpses in a specific space (V3)
     */
    fun getCorpsesInSpace(spaceId: SpaceId, worldState: WorldState): List<Entity.Corpse> {
        return worldState.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Corpse>()
    }

    /**
     * Get total number of corpses in the world (V3)
     */
    fun getTotalCorpses(worldState: WorldState): Int {
        return worldState.entities.values.count { it is Entity.Corpse }
    }
}
