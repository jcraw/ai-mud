package com.jcraw.app.handlers

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.repository.CorpseRepository
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.death.*

/**
 * Handlers for corpse-related intents.
 *
 * Handles:
 * - Intent.LootCorpse - Retrieve items from player corpse
 *
 * Integration:
 * - Uses CorpseManager for business logic
 * - Updates WorldState with modified player
 * - Generates narration for player feedback
 */

/**
 * Handle Intent.LootCorpse.
 *
 * Process:
 * 1. Find target corpse (by ID or "nearest" in current space)
 * 2. Call CorpseManager.lootCorpse()
 * 3. Update player state with retrieved items
 * 4. Generate narration
 * 5. Return updated world state
 *
 * Special handling:
 * - corpseTarget="nearest" or "my corpse": Find nearest corpse in current space
 * - corpseTarget=corpseId: Loot specific corpse by ID
 *
 * @param intent Loot corpse intent with target
 * @param world Current world state
 * @param player Current player state
 * @param corpseRepository Repository for corpse lookups
 * @param currentTime Current game time
 * @return Updated world state and narration
 */
fun handleLootCorpse(
    intent: Intent.LootCorpse,
    world: WorldState,
    player: PlayerState,
    corpseRepository: CorpseRepository,
    currentTime: Long
): Pair<WorldState, String> {
    // Determine target corpse ID
    val targetCorpseId = when {
        intent.corpseTarget.equals("nearest", ignoreCase = true) ||
        intent.corpseTarget.equals("my corpse", ignoreCase = true) -> {
            // Find nearest corpse in current space
            val corpsesInSpace = findCorpsesInSpace(player.currentRoomId, corpseRepository)
                .getOrElse { error ->
                    return world to "Error finding corpses: ${error.message}"
                }

            if (corpsesInSpace.isEmpty()) {
                return world to "There are no corpses here to loot."
            }

            // Find player's own corpse if it exists
            val playerCorpse = corpsesInSpace.firstOrNull { it.playerId == player.id }
            if (playerCorpse != null) {
                playerCorpse.id
            } else {
                // No player corpse, just use nearest
                corpsesInSpace.first().id
            }
        }
        else -> intent.corpseTarget // Use specified corpse ID
    }

    // Attempt to loot corpse
    val lootResult = lootCorpse(targetCorpseId, player, corpseRepository).getOrElse { error ->
        return world to """
            |Failed to loot corpse: ${error.message}
            |
            |Possible reasons:
            |- Corpse not found (may have decayed)
            |- Corpse already looted
            |- Database error
        """.trimMargin()
    }

    // Update world state with modified player
    val updatedWorld = world.updatePlayer(lootResult.updatedPlayer)

    // Generate narration
    val narration = generateLootNarration(lootResult, currentTime)

    return updatedWorld to narration
}

/**
 * Generate narration for corpse looting.
 *
 * Includes:
 * - Items transferred
 * - Gold transferred
 * - Overweight items (V2 only)
 * - Success message
 *
 * @param lootResult Result of looting operation
 * @param currentTime Current game time
 * @return Narration text
 */
private fun generateLootNarration(
    lootResult: LootResult,
    currentTime: Long
): String {
    val itemCount = lootResult.itemsTransferred.size
    val goldAmount = lootResult.goldTransferred
    val overweightCount = lootResult.overweightItems.size

    val itemsList = if (itemCount > 0) {
        lootResult.itemsTransferred.take(10).joinToString(", ") { it.templateId }
    } else {
        "nothing"
    }

    val overweightWarning = if (overweightCount > 0) {
        """
        |
        |⚠ WARNING: $overweightCount items were too heavy to carry:
        |${lootResult.overweightItems.joinToString(", ") { it.templateId }}
        |
        |Consider dropping some items or increasing your Strength.
        """.trimMargin()
    } else {
        ""
    }

    return """
        |═══════════════════════════════════════════════════════════════
        |                      CORPSE LOOTED
        |═══════════════════════════════════════════════════════════════
        |
        |You recover your lost belongings from your corpse.
        |
        |Items retrieved: $itemCount
        |${if (itemCount > 0) "Items: $itemsList${if (itemCount > 10) " (and ${itemCount - 10} more)" else ""}" else ""}
        |
        |Gold retrieved: $goldAmount
        |
        |$overweightWarning
        |
        |Your corpse has been consumed by the void.
        |
        |═══════════════════════════════════════════════════════════════
    """.trimMargin()
}

/**
 * Handle "corpses" info command (not an Intent, but useful helper).
 *
 * Shows all player's corpses and their locations.
 *
 * @param player Current player state
 * @param corpseRepository Repository for corpse lookups
 * @param currentTime Current game time
 * @return Narration describing all player corpses
 */
fun handleCorpsesInfo(
    player: PlayerState,
    corpseRepository: CorpseRepository,
    currentTime: Long
): String {
    val corpses = findPlayerCorpses(player.id, corpseRepository).getOrElse { error ->
        return "Error retrieving corpses: ${error.message}"
    }

    if (corpses.isEmpty()) {
        return """
            |═══════════════════════════════════════════════════════════════
            |                      YOUR CORPSES
            |═══════════════════════════════════════════════════════════════
            |
            |You have no corpses in the dungeon.
            |
            |(You haven't died yet, or all your corpses have been looted/decayed)
            |
            |═══════════════════════════════════════════════════════════════
        """.trimMargin()
    }

    val corpsesList = corpses.mapIndexed { index, corpse ->
        val turnsRemaining = corpse.turnsUntilDecay(currentTime)
        val decayStatus = when {
            turnsRemaining <= 0 -> "[DECAYED]"
            turnsRemaining < 100 -> "[DECAYING SOON: $turnsRemaining turns]"
            else -> "[$turnsRemaining turns remaining]"
        }

        val lootedStatus = if (corpse.looted) "[LOOTED]" else "[Available]"

        """
        |${index + 1}. Corpse ID: ${corpse.id}
        |   Location: ${corpse.spaceId}
        |   Contents: ${corpse.contentsSummary()}
        |   Status: $lootedStatus $decayStatus
        """.trimMargin()
    }.joinToString("\n\n")

    return """
        |═══════════════════════════════════════════════════════════════
        |                      YOUR CORPSES
        |═══════════════════════════════════════════════════════════════
        |
        |You have ${corpses.size} corpse${if (corpses.size == 1) "" else "s"} in the dungeon:
        |
        |$corpsesList
        |
        |═══════════════════════════════════════════════════════════════
        |
        |Use 'loot corpse' when standing in the same location to retrieve your items.
        |
        |═══════════════════════════════════════════════════════════════
    """.trimMargin()
}
