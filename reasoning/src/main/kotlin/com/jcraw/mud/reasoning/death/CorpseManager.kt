package com.jcraw.mud.reasoning.death

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.CorpseRepository
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Manages corpse interactions: finding, looting, cleanup.
 *
 * Features:
 * - Find all corpses for a player (for UI/info display)
 * - Loot corpse to retrieve items/gold
 * - Clean up decayed corpses (periodic maintenance)
 * - Describe corpse contents
 *
 * Design:
 * - Weight-based looting: Can't retrieve if over capacity
 * - Corpse deleted after looting (no re-looting)
 * - Decay cleanup deletes corpses older than decay timer
 */

/**
 * Result of corpse looting operation
 *
 * @param itemsTransferred List of items successfully transferred to player
 * @param goldTransferred Amount of gold transferred
 * @param overweightItems Items that couldn't be transferred (over weight limit)
 * @param updatedPlayer Player state with retrieved items
 */
data class LootResult(
    val itemsTransferred: List<ItemInstance>,
    val goldTransferred: Int,
    val overweightItems: List<ItemInstance>,
    val updatedPlayer: PlayerState
)

/**
 * Find all corpses belonging to a player.
 *
 * Useful for:
 * - "corpses" command to list player's corpses
 * - UI display ("You have 2 corpses in the dungeon")
 *
 * @param playerId Player identifier
 * @param corpseRepository Repository for corpse lookups
 * @return List of corpses (ordered by decay timer, oldest first)
 */
fun findPlayerCorpses(
    playerId: PlayerId,
    corpseRepository: CorpseRepository
): Result<List<CorpseData>> {
    return corpseRepository.findByPlayerId(playerId)
}

/**
 * Loot corpse to retrieve items and gold.
 *
 * Process:
 * 1. Load corpse from database
 * 2. Check weight capacity for each item
 * 3. Transfer items to player inventory (skip overweight items)
 * 4. Transfer gold
 * 5. Mark corpse as looted
 * 6. Delete corpse from database
 * 7. Return loot result with updated player
 *
 * Weight handling:
 * - Uses V2 InventoryComponent with weight-based capacity
 * - Items that would exceed capacity are skipped (returned in overweightItems)
 *
 * @param corpseId Corpse identifier
 * @param player Player looting the corpse
 * @param corpseRepository Repository for corpse operations
 * @param itemRepository Repository for item template lookups (for weight)
 * @return Success with LootResult, or failure
 */
fun lootCorpse(
    corpseId: String,
    player: PlayerState,
    corpseRepository: CorpseRepository,
    itemRepository: ItemRepository
): Result<LootResult> {
    // Load corpse
    val corpse = corpseRepository.findById(corpseId).getOrElse { error ->
        return Result.failure(error)
    } ?: return Result.failure(Exception("Corpse not found: $corpseId"))

    // Check if already looted
    if (corpse.looted) {
        return Result.failure(Exception("This corpse has already been looted"))
    }

    // Get or initialize player's V2 inventory
    var updatedInventory = player.inventoryComponent ?: InventoryComponent(
        items = emptyList(),
        equipped = emptyMap(),
        gold = player.gold,
        capacityWeight = player.stats.strength * 5.0
    )

    // Combine all items from corpse (inventory + equipped)
    val allCorpseItems = corpse.inventory.items + corpse.equipment
    val transferredItems = mutableListOf<ItemInstance>()
    val overweightItems = mutableListOf<ItemInstance>()

    // Build template map for weight checking
    val templates = mutableMapOf<String, ItemTemplate>()
    for (item in allCorpseItems) {
        val template = itemRepository.findTemplateById(item.templateId).getOrNull()
        if (template != null) {
            templates[item.templateId] = template
        }
    }
    // Also include current inventory templates
    for (item in updatedInventory.items) {
        if (!templates.containsKey(item.templateId)) {
            val template = itemRepository.findTemplateById(item.templateId).getOrNull()
            if (template != null) {
                templates[item.templateId] = template
            }
        }
    }

    // Transfer each item, checking weight
    for (item in allCorpseItems) {
        val template = templates[item.templateId]
        if (template != null && updatedInventory.canAdd(template, item.quantity, templates)) {
            updatedInventory = updatedInventory.addItem(item)
            transferredItems.add(item)
        } else {
            overweightItems.add(item)
        }
    }

    // Add gold (gold is weightless)
    updatedInventory = updatedInventory.addGold(corpse.gold)

    // Update player with V2 inventory
    val updatedPlayer = player.copy(
        inventoryComponent = updatedInventory
    )

    // Mark corpse as looted and delete
    corpseRepository.markLooted(corpseId).getOrElse { error ->
        return Result.failure(error)
    }

    corpseRepository.delete(corpseId).getOrElse { error ->
        return Result.failure(error)
    }

    // Return loot result
    val lootResult = LootResult(
        itemsTransferred = transferredItems,
        goldTransferred = corpse.gold,
        overweightItems = overweightItems,
        updatedPlayer = updatedPlayer
    )

    return Result.success(lootResult)
}

/**
 * Clean up decayed corpses.
 *
 * Periodic maintenance task:
 * - Find all corpses past their decay timer
 * - Delete them from database
 * - Return count of deleted corpses
 *
 * Should be called every ~100 turns to prevent database bloat.
 *
 * @param currentTime Current game time (in turns)
 * @param corpseRepository Repository for corpse operations
 * @return Success with count of deleted corpses, or failure
 */
fun cleanupDecayedCorpses(
    currentTime: Long,
    corpseRepository: CorpseRepository
): Result<Int> {
    // Find all decayed corpses
    val decayedCorpses = corpseRepository.findDecayed(currentTime).getOrElse { error ->
        return Result.failure(error)
    }

    // Delete each decayed corpse
    var deletedCount = 0
    for (corpse in decayedCorpses) {
        corpseRepository.delete(corpse.id).onSuccess {
            deletedCount++
        }
    }

    return Result.success(deletedCount)
}

/**
 * Generate description of corpse contents.
 *
 * Used for:
 * - "look" command when corpses are in room
 * - "corpses" command to list player's corpses
 *
 * Format:
 * "The corpse of [player name] lies here, clutching [inventory summary].
 *  It will decay in [turns remaining] turns."
 *
 * @param corpse Corpse to describe
 * @param currentTime Current game time (for decay calculation)
 * @return Human-readable corpse description
 */
fun describeCorpse(
    corpse: CorpseData,
    currentTime: Long
): String {
    val turnsRemaining = corpse.turnsUntilDecay(currentTime)
    val contentsSummary = corpse.contentsSummary()

    val decayWarning = when {
        turnsRemaining <= 0 -> "This corpse has decayed and will be removed soon."
        turnsRemaining < 100 -> "WARNING: This corpse will decay very soon ($turnsRemaining turns remaining)!"
        turnsRemaining < 500 -> "This corpse will decay in $turnsRemaining turns."
        else -> "This corpse will decay in $turnsRemaining turns."
    }

    return """
        |═══════════════════════════════════════════════════════════════
        |  Corpse of player '${corpse.playerId}'
        |═══════════════════════════════════════════════════════════════
        |
        |Location: ${corpse.spaceId}
        |Contents: $contentsSummary
        |
        |$decayWarning
        |
        |${if (corpse.looted) "[LOOTED - Empty]" else "[Available for looting]"}
        |
        |═══════════════════════════════════════════════════════════════
    """.trimMargin()
}

/**
 * Find corpses in a specific space.
 *
 * Used for "look" command to show corpses in current room.
 *
 * @param spaceId Space identifier
 * @param corpseRepository Repository for corpse lookups
 * @return List of corpses in space (ordered by decay timer)
 */
fun findCorpsesInSpace(
    spaceId: String,
    corpseRepository: CorpseRepository
): Result<List<CorpseData>> {
    return corpseRepository.findBySpaceId(spaceId)
}

