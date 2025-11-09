package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.loot.LootGenerator
import com.jcraw.mud.reasoning.loot.LootSource
import com.jcraw.mud.reasoning.loot.LootTableRegistry
import java.util.UUID

/**
 * Handles entity death, corpse creation, and permadeath mechanics.
 *
 * When an entity dies:
 * - NPC: Creates corpse with loot from loot tables, removes from room
 * - Player: Creates corpse with inventory, triggers permadeath sequence
 *
 * @property lootGenerator Generator for creating item drops from loot tables
 */
class DeathHandler(
    private val lootGenerator: LootGenerator
) {

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
        // Get entity from global storage (V3)
        val entity = worldState.getEntity(entityId) ?: return null

        // Find which space the entity is in
        val spaceWithEntity = worldState.spaces.entries.firstOrNull { (_, space) ->
            space.entities.contains(entityId)
        } ?: return null

        return when (entity) {
            is Entity.NPC -> handleNPCDeath(entity, spaceWithEntity.key, spaceWithEntity.value, worldState)
            is Entity.Player -> handlePlayerDeath(entity, spaceWithEntity.key, spaceWithEntity.value, worldState)
            else -> null // Items, features, corpses don't die
        }
    }

    /**
     * Handle NPC death - create corpse with loot from loot tables
     */
    private fun handleNPCDeath(
        npc: Entity.NPC,
        spaceId: SpaceId,
        space: SpacePropertiesComponent,
        worldState: WorldState
    ): DeathResult.NPCDeath {
        // Generate loot from loot table if NPC has one
        val lootTableId = npc.lootTableId
        val loot = if (lootTableId != null) {
            val table = LootTableRegistry.getTable(lootTableId)
            if (table != null) {
                // Determine loot source based on NPC properties
                val source = determineLootSource(npc)
                lootGenerator.generateLoot(table, source).getOrElse { emptyList() }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        // Generate gold drop
        val gold = if (npc.goldDrop > 0) {
            val source = determineLootSource(npc)
            lootGenerator.generateGoldDrop(npc.goldDrop, source)
        } else {
            0
        }

        val goldInstance = lootGenerator.createGoldInstance(gold)
        val combinedLoot = buildList {
            addAll(loot)
            if (goldInstance != null) add(goldInstance)
        }

        // Create corpse with loot
        val corpse = Entity.Corpse(
            id = "corpse_${npc.id}_${UUID.randomUUID()}",
            name = "Corpse of ${npc.name}",
            description = "The lifeless body of ${npc.name}.",
            contents = combinedLoot,
            goldAmount = gold,
            decayTimer = 100
        )

        // Remove NPC from space, add corpse (V3)
        var updatedWorld = worldState
            .replaceEntityInSpace(spaceId, npc.id, corpse) ?: worldState

        if (combinedLoot.isNotEmpty()) {
            val dropEntities = lootGenerator.toEntityItems(combinedLoot)
            dropEntities.forEach { drop ->
                updatedWorld = updatedWorld.addEntityToSpace(spaceId, drop) ?: updatedWorld
            }

            val latestSpace = updatedWorld.getSpace(spaceId)
            if (latestSpace != null) {
                val updatedSpace = latestSpace.copy(itemsDropped = latestSpace.itemsDropped + combinedLoot)
                updatedWorld = updatedWorld.updateSpace(spaceId, updatedSpace)
            }
        }

        return DeathResult.NPCDeath(corpse, updatedWorld)
    }

    /**
     * Determine loot source type based on NPC properties.
     * Uses maxHealth as a rough indicator of difficulty.
     */
    private fun determineLootSource(npc: Entity.NPC): LootSource {
        return when {
            npc.maxHealth >= 200 -> LootSource.BOSS
            npc.maxHealth >= 150 -> LootSource.ELITE_MOB
            else -> LootSource.COMMON_MOB
        }
    }

    /**
     * Handle player death - create corpse with inventory.
     * Note: Inventory migration is pending. For now, creates empty corpse.
     * TODO: Update when InventoryComponent is fully integrated with PlayerState
     */
    private fun handlePlayerDeath(
        player: Entity.Player,
        spaceId: SpaceId,
        space: SpacePropertiesComponent,
        worldState: WorldState
    ): DeathResult.PlayerDeath {
        // Create corpse (inventory will be added when InventoryComponent migration completes)
        val corpse = Entity.Corpse(
            id = "corpse_${player.id}_${UUID.randomUUID()}",
            name = "Corpse of ${player.name}",
            description = "Your lifeless body. Your belongings are scattered nearby.",
            contents = emptyList(),  // TODO: Extract from InventoryComponent when integrated
            goldAmount = 0,  // TODO: Extract from InventoryComponent when integrated
            decayTimer = 200  // Player corpses last longer
        )

        // Remove player entity from space, add corpse (V3)
        val updatedWorld = worldState
            .replaceEntityInSpace(spaceId, player.id, corpse) ?: worldState

        return DeathResult.PlayerDeath(
            corpse = corpse,
            playerId = player.playerId,
            deathRoomId = spaceId,
            updatedWorld = updatedWorld
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
