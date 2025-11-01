package com.jcraw.mud.reasoning.town

import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Validates safe zone rules and constraints.
 *
 * Safe zones are spaces where:
 * - Combat is forbidden
 * - Traps do not exist
 * - No hostile mobs spawn
 * - Players can rest to recover HP/Mana
 *
 * The safe zone flag (isSafeZone=true) is set on SpacePropertiesComponent.
 */
object SafeZoneValidator {

    /**
     * Check if a space is designated as a safe zone.
     *
     * @param space Space properties to check
     * @return True if space has isSafeZone flag set
     */
    fun isSafeZone(space: SpacePropertiesComponent?): Boolean {
        return space?.isSafeZone ?: false
    }

    /**
     * Validate that a space meets safe zone requirements.
     *
     * Checks:
     * 1. isSafeZone flag is true
     * 2. No traps present
     * 3. No hostile entities (optional check - merchants are allowed)
     *
     * @param space Space to validate
     * @return Result.success if valid, Result.failure with reason if invalid
     */
    fun validateSafeZone(space: SpacePropertiesComponent): Result<Unit> {
        // Check safe zone flag
        if (!space.isSafeZone) {
            return Result.failure(
                IllegalStateException("Space is not designated as a safe zone (isSafeZone=false)")
            )
        }

        // Check for traps
        if (space.traps.isNotEmpty()) {
            return Result.failure(
                IllegalStateException("Safe zone contains ${space.traps.size} trap(s). Safe zones cannot have traps.")
            )
        }

        // Note: We don't check entities here because merchants (NPCs) are allowed in safe zones.
        // Only hostile entities should be prevented from spawning, which is handled by SpacePopulator.

        return Result.success(Unit)
    }

    /**
     * Check if combat is allowed in a space.
     *
     * Combat is blocked in safe zones.
     *
     * @param space Space to check
     * @return True if combat is allowed (not a safe zone)
     */
    fun isCombatAllowed(space: SpacePropertiesComponent?): Boolean {
        return !isSafeZone(space)
    }

    /**
     * Get a description of safe zone rules.
     *
     * @return Human-readable description of safe zone constraints
     */
    fun getSafeZoneRules(): String {
        return """
            |Safe Zone Rules:
            |  - Combat is forbidden (attacks are blocked)
            |  - No traps or hazards
            |  - Resting is allowed (HP/Mana recovery)
            |  - Merchants may be present for trading
            |  - Game time advances when resting
        """.trimMargin()
    }

    /**
     * Get a description of why combat was blocked.
     *
     * @param targetName Name of attempted combat target
     * @return Refusal message
     */
    fun getCombatBlockedMessage(targetName: String): String {
        return """
            |This is a safe zone. Combat is forbidden.
            |
            |You cannot attack $targetName here.
            |Find them outside the safe zone if you wish to engage in combat.
        """.trimMargin()
    }

    /**
     * Get a description of why an action was blocked in a safe zone.
     *
     * @param actionType Type of action attempted (e.g., "trap placement", "hostile magic")
     * @return Refusal message
     */
    fun getActionBlockedMessage(actionType: String): String {
        return """
            |This is a safe zone. $actionType is not allowed.
            |
            |Safe zones are sanctuaries where violence and danger are prohibited.
        """.trimMargin()
    }

    /**
     * Check if entity spawning should be skipped in a space.
     *
     * Hostile entities should not spawn in safe zones.
     * This is enforced by SpacePopulator during world generation.
     *
     * @param space Space to check
     * @param entityIsHostile True if entity is hostile/aggressive
     * @return True if spawning should be skipped
     */
    fun shouldSkipEntitySpawn(space: SpacePropertiesComponent, entityIsHostile: Boolean): Boolean {
        return space.isSafeZone && entityIsHostile
    }

    /**
     * Check if trap placement should be skipped in a space.
     *
     * Traps should not spawn in safe zones.
     *
     * @param space Space to check
     * @return True if trap placement should be skipped
     */
    fun shouldSkipTrapPlacement(space: SpacePropertiesComponent): Boolean {
        return space.isSafeZone
    }
}
