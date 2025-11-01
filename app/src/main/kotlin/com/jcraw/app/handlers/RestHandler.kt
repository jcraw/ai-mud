package com.jcraw.app.handlers

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.repository.SpacePropertiesRepository
import com.jcraw.mud.perception.Intent

/**
 * Handles safe zone resting for HP/Mana regeneration.
 *
 * Players can rest in safe zones to:
 * - Restore HP to maximum
 * - Restore Mana to maximum (when mana system exists)
 * - Advance game time by 100 ticks
 *
 * Resting is only allowed in safe zones (spaces with isSafeZone=true).
 * Attempting to rest in combat zones fails.
 */

/**
 * Handle rest intent in safe zones.
 *
 * Logic:
 * 1. Check if current space is a safe zone
 * 2. If not safe: Deny rest with danger warning
 * 3. If safe: Restore HP to max, advance time by 100 ticks
 *
 * @param intent Rest intent with optional location
 * @param world Current world state
 * @param player Current player state
 * @param spacePropertiesRepository Repository for space lookups
 * @return Updated world state and narration message
 */
fun handleRest(
    intent: Intent.Rest,
    world: WorldState,
    player: PlayerState,
    spacePropertiesRepository: SpacePropertiesRepository
): Pair<WorldState, String> {
    // Get current space properties
    val currentSpaceId = player.currentRoomId
    val currentSpace = spacePropertiesRepository.findByChunkId(currentSpaceId).getOrNull()

    // Check if in safe zone
    if (currentSpace == null || !currentSpace.isSafeZone) {
        return world to """
            |You cannot rest here. Danger lurks in every shadow.
            |
            |Find a safe zone (such as the Town) to rest and recover.
        """.trimMargin()
    }

    // Rest in safe zone
    val restoredPlayer = player.copy(
        health = player.maxHealth
        // TODO: Add mana restoration when mana system exists:
        // mana = player.maxMana
    )

    val updatedWorld = world
        .updatePlayer(restoredPlayer)
        .advanceTime(100L) // Rest takes 100 game ticks

    val narration = """
        |You rest at the ${intent.location.ifEmpty { "safe zone" }}.
        |
        |HP fully restored: ${restoredPlayer.health}/${restoredPlayer.maxHealth}
        |Time passes... (+100 ticks, current time: ${updatedWorld.gameTime})
        |
        |You feel refreshed and ready to continue.
    """.trimMargin()

    return updatedWorld to narration
}

/**
 * Check if a space is a safe zone.
 *
 * @param space Space properties to check
 * @return True if space is a safe zone
 */
fun isSafeZone(space: SpacePropertiesComponent?): Boolean {
    return space?.isSafeZone ?: false
}

/**
 * Get description of why resting failed.
 *
 * @param space Current space (null if not in procedural world)
 * @return Failure reason
 */
fun getRestFailureReason(space: SpacePropertiesComponent?): String {
    return when {
        space == null -> "You are not in a procedurally generated area. Resting is only available in the Ancient Abyss Dungeon."
        !space.isSafeZone -> "This area is too dangerous. Find a safe zone to rest."
        space.entities.isNotEmpty() -> "Enemies are nearby. Eliminate threats before resting."
        space.traps.isNotEmpty() -> "Traps surround you. This is not a safe place to rest."
        else -> "You cannot rest here."
    }
}
