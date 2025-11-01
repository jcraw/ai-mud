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
 * @param newPlayer Fresh player state spawned at town
 * @param corpseId Unique identifier for created corpse
 * @param townSpaceId Space ID where player respawned
 * @param deathSpaceId Space ID where player died
 * @param narration Death and respawn narration for player
 */
data class DeathResult(
    val newPlayer: PlayerState,
    val corpseId: String,
    val townSpaceId: String,
    val deathSpaceId: String,
    val narration: String
)

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

    // Convert V1 player inventory to V2 InventoryComponent
    // TODO: Remove this conversion when Item System V2 is fully integrated
    val inventoryComponent = convertPlayerInventoryToV2(player)
    val equippedItems = extractEquippedItems(player)

    val corpse = CorpseData(
        id = corpseId,
        playerId = player.id,
        spaceId = currentSpaceId,
        inventory = inventoryComponent,
        equipment = equippedItems,
        gold = player.gold,
        decayTimer = gameTime + 5000L, // 5000 turns decay time
        looted = false
    )

    // Save corpse to database
    corpseRepository.save(corpse).getOrElse { error ->
        return Result.failure(error)
    }

    // Create fresh player state with starter gear
    val newPlayer = createFreshPlayer(player.id, player.name, townSpaceId)

    // Generate death narration
    val narration = createCorpseNarration(player, currentSpaceId, corpseId, gameTime)

    return Result.success(
        DeathResult(
            newPlayer = newPlayer,
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
 * @param corpseSpaceId Space where corpse lies
 * @param corpseId Corpse identifier
 * @param gameTime Current game time
 * @return Dramatic death narration
 */
fun createCorpseNarration(
    oldPlayer: PlayerState,
    corpseSpaceId: String,
    corpseId: String,
    gameTime: Long
): String {
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
        |Items lost: ${oldPlayer.inventory.size} inventory items
        |Gold lost: ${oldPlayer.gold} gold
        |Equipped gear lost: ${if (oldPlayer.equippedWeapon != null) 1 else 0 + if (oldPlayer.equippedArmor != null) 1 else 0} pieces
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
 * - Starter gear (basic dagger, cloth armor)
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
    playerName: String,
    townSpaceId: String
): PlayerState {
    // Starter gear
    val starterDagger = Entity.Item(
        id = UUID.randomUUID().toString(),
        name = "Rusty Dagger",
        description = "A worn dagger. Better than nothing.",
        damageBonus = 2,
        defenseBonus = 0,
        healAmount = 0,
        isConsumable = false
    )

    val starterClothes = Entity.Item(
        id = UUID.randomUUID().toString(),
        name = "Tattered Clothes",
        description = "Barely provides any protection.",
        damageBonus = 0,
        defenseBonus = 1,
        healAmount = 0,
        isConsumable = false
    )

    // Basic stats (10 in all stats, D&D baseline)
    val baseStats = Stats(
        strength = 10,
        dexterity = 10,
        constitution = 10,
        intelligence = 10,
        wisdom = 10,
        charisma = 10
    )

    return PlayerState(
        id = playerId,
        name = playerName,
        currentRoomId = townSpaceId, // Spawn at town
        health = 100,
        maxHealth = 100,
        stats = baseStats,
        inventory = emptyList(), // Empty inventory
        equippedWeapon = starterDagger,
        equippedArmor = starterClothes,
        skills = emptyMap(), // No trained skills
        properties = emptyMap(),
        activeQuests = emptyList(),
        completedQuests = emptyList(),
        experiencePoints = 0,
        gold = 0
    )
}

/**
 * Convert V1 player inventory (List<Entity.Item>) to V2 InventoryComponent.
 *
 * Temporary converter for V1/V2 bridge.
 * TODO: Remove when Item System V2 is fully integrated
 *
 * @param player V1 player state
 * @return V2 InventoryComponent
 */
private fun convertPlayerInventoryToV2(player: PlayerState): InventoryComponent {
    // Convert V1 items to V2 ItemInstances
    val itemInstances = player.inventory.map { v1Item ->
        ItemInstance(
            id = v1Item.id,
            templateId = v1Item.name, // Use name as template ID for now
            quality = 5, // Default medium quality
            quantity = 1,
            charges = null
        )
    }

    // Build equipped map
    val equippedMap = mutableMapOf<EquipSlot, ItemInstance>()

    val weapon = player.equippedWeapon
    if (weapon != null) {
        val weaponInstance = ItemInstance(
            id = weapon.id,
            templateId = weapon.name,
            quality = 5,
            quantity = 1,
            charges = null
        )
        equippedMap[EquipSlot.HANDS_MAIN] = weaponInstance
    }

    val armor = player.equippedArmor
    if (armor != null) {
        val armorInstance = ItemInstance(
            id = armor.id,
            templateId = armor.name,
            quality = 5,
            quantity = 1,
            charges = null
        )
        equippedMap[EquipSlot.CHEST] = armorInstance
    }

    // Create InventoryComponent
    return InventoryComponent(
        items = itemInstances,
        equipped = equippedMap,
        gold = player.gold,
        capacityWeight = player.stats.strength * 5.0 // Strength-based capacity
    )
}

/**
 * Extract equipped items as separate list for CorpseData.
 *
 * @param player V1 player state
 * @return List of equipped items as ItemInstances
 */
private fun extractEquippedItems(player: PlayerState): List<ItemInstance> {
    val equipped = mutableListOf<ItemInstance>()

    val weapon = player.equippedWeapon
    if (weapon != null) {
        equipped.add(
            ItemInstance(
                id = weapon.id,
                templateId = weapon.name,
                quality = 5,
                quantity = 1,
                charges = null
            )
        )
    }

    val armor = player.equippedArmor
    if (armor != null) {
        equipped.add(
            ItemInstance(
                id = armor.id,
                templateId = armor.name,
                quality = 5,
                quantity = 1,
                charges = null
            )
        )
    }

    return equipped
}
