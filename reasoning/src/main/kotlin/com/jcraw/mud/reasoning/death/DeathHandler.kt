package com.jcraw.mud.reasoning.death

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.CorpseRepository
import java.util.UUID

/**
 * Handles player death, corpse creation, and respawn logic.
 *
 * Death Flow:
 * 1. Player HP reaches 0
 * 2. Create CorpseData with full inventory/equipment/gold
 * 3. Save corpse to database
 * 4. Create fresh PlayerState with starter gear
 * 5. Spawn new player at town
 * 6. Return death result with narration
 *
 * Design:
 * - Dark Souls-style corpse retrieval: old gear remains at death location
 * - Corpses decay after 5000 turns (~83 hours of gameplay)
 * - World state persists (no regeneration)
 * - Multiple corpses allowed (die again before retrieval)
 */

/**
 * Result of player death handling
 *
 * @param playerId Identifier of the fallen hero (used for continuity)
 * @param corpseId Unique identifier for created corpse
 * @param townSpaceId Space ID where the replacement character will spawn
 * @param deathSpaceId Space ID where player died
 * @param narration Death + respawn narration for the UI
 */
data class DeathResult(
    val playerId: PlayerId,
    val corpseId: String,
    val townSpaceId: String,
    val deathSpaceId: String,
    val narration: String
) {
    /**
     * Create a fresh player instance for the next run.
     */
    fun createNewPlayer(newCharacterName: String): PlayerState =
        createFreshPlayer(playerId, newCharacterName, townSpaceId)
}

/**
 * Handle player death.
 *
 * Creates corpse with full inventory, spawns fresh player at town.
 *
 * @param player Player who died (HP <= 0)
 * @param currentSpaceId Space where player died
 * @param townSpaceId Town space ID for respawn
 * @param gameTime Current game time for decay timer
 * @param corpseRepository Repository for corpse persistence
 * @return Success with DeathResult, or failure
 */
fun handlePlayerDeath(
    player: PlayerState,
    currentSpaceId: String,
    townSpaceId: String,
    gameTime: Long,
    corpseRepository: CorpseRepository
): Result<DeathResult> {
    // Create corpse from player state
    val corpseId = UUID.randomUUID().toString()

    // Use V2 InventoryComponent directly if available, otherwise create empty
    val inventoryComponent = player.inventoryComponent ?: InventoryComponent(
        items = emptyList(),
        equipped = emptyMap(),
        gold = player.gold,
        capacityWeight = player.stats.strength * 5.0
    )

    // Extract equipped items as separate list for CorpseData
    val equippedItems = inventoryComponent.equipped.values.toList()

    val corpse = CorpseData(
        id = corpseId,
        playerId = player.id,
        spaceId = currentSpaceId,
        inventory = inventoryComponent,
        equipment = equippedItems,
        gold = inventoryComponent.gold,
        decayTimer = gameTime + 5000L, // 5000 turns decay time
        looted = false
    )

    // Save corpse to database
    corpseRepository.save(corpse).getOrElse { error ->
        return Result.failure(error)
    }

    // Generate death narration
    val narration = createCorpseNarration(player, inventoryComponent, currentSpaceId, corpseId, gameTime)

    return Result.success(
        DeathResult(
            playerId = player.id,
            corpseId = corpseId,
            townSpaceId = townSpaceId,
            deathSpaceId = currentSpaceId,
            narration = narration
        )
    )
}

/**
 * Create death and respawn narration.
 *
 * @param oldPlayer Player who died
 * @param inventory V2 inventory that was lost
 * @param corpseSpaceId Space where corpse lies
 * @param corpseId Corpse identifier
 * @param gameTime Current game time
 * @return Dramatic death narration
 */
fun createCorpseNarration(
    oldPlayer: PlayerState,
    inventory: InventoryComponent,
    corpseSpaceId: String,
    corpseId: String,
    gameTime: Long
): String {
    val itemCount = inventory.items.size
    val equippedCount = inventory.equipped.size
    val goldLost = inventory.gold

    return """
        |═══════════════════════════════════════════════════════════════
        |
        |                        YOU HAVE PERISHED
        |
        |═══════════════════════════════════════════════════════════════
        |
        |Your vision fades to black. The last thing you see is your own
        |blood pooling on the cold stone floor.
        |
        |Your corpse lies in space '$corpseSpaceId', awaiting retrieval.
        |Corpse ID: $corpseId
        |
        |Items lost: $itemCount inventory items
        |Gold lost: $goldLost gold
        |Equipped gear lost: $equippedCount pieces
        |
        |Corpse will decay in 5000 turns (current time: $gameTime).
        |
        |─────────────────────────────────────────────────────────────────
        |
        |A new soul awakens in the Town, drawn by fate to reclaim what
        |was lost. Will you succeed where your predecessor failed?
        |
        |You spawn at the Town with basic gear. Return to your corpse
        |to retrieve your lost equipment... if you dare.
        |
        |═══════════════════════════════════════════════════════════════
    """.trimMargin()
}

/**
 * Create fresh player state for respawn.
 *
 * Player spawns with:
 * - Level 1 skills
 * - Starter gear (basic dagger, cloth armor) via V2 inventory
 * - Empty inventory
 * - 0 gold
 * - Full HP
 *
 * @param playerId Original player ID (preserved for corpse tracking)
 * @param playerName Original player name
 * @param townSpaceId Town spawn point
 * @return Fresh player state
 */
private fun createFreshPlayer(
    playerId: PlayerId,
    newCharacterName: String,
    townSpaceId: String
): PlayerState {
    // Basic stats (10 in all stats, D&D baseline)
    val baseStats = Stats(
        strength = 10,
        dexterity = 10,
        constitution = 10,
        intelligence = 10,
        wisdom = 10,
        charisma = 10
    )

    // Starter gear as V2 ItemInstances (uses starter item template IDs)
    val starterDagger = ItemInstance(
        id = UUID.randomUUID().toString(),
        templateId = "rusty_dagger",
        quality = 5,
        quantity = 1,
        charges = null
    )

    val starterClothes = ItemInstance(
        id = UUID.randomUUID().toString(),
        templateId = "tattered_clothes",
        quality = 5,
        quantity = 1,
        charges = null
    )

    // Create V2 inventory with starter gear equipped
    val starterInventory = InventoryComponent(
        items = listOf(starterDagger, starterClothes),
        equipped = mapOf(
            EquipSlot.HANDS_MAIN to starterDagger,
            EquipSlot.CHEST to starterClothes
        ),
        gold = 0,
        capacityWeight = baseStats.strength * 5.0 // 50kg capacity
    )

    return PlayerState(
        id = playerId,
        name = newCharacterName,
        currentRoomId = townSpaceId, // Spawn at town
        health = 100,
        maxHealth = 100,
        stats = baseStats,
        properties = emptyMap(),
        activeQuests = emptyList(),
        completedQuests = emptyList(),
        experiencePoints = 0,
        inventoryComponent = starterInventory
    )
}
