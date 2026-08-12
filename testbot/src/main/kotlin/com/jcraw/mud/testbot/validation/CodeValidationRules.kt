@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.getCurrentRoomView
import com.jcraw.mud.testbot.TestStep
import com.jcraw.mud.testbot.ValidationResult

/**
 * Code-based deterministic validation logic.
 * Returns ValidationResult if we can definitively validate, null otherwise.
 *
 * Domain validators live in CodeValidation* extracts (MUD-034f).
 */
object CodeValidationRules {
    fun validate(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        worldState: WorldState?
    ): ValidationResult? {
        if (worldState == null) return null

        // Check for player death - this is a critical failure state
        if (worldState.player.health <= 0) {
            return ValidationResult(
                pass = false,
                reason = "Player died (HP: ${worldState.player.health}/${worldState.player.maxHealth}) - test should end",
                details = mapOf(
                    "validation_type" to "code",
                    "health" to worldState.player.health.toString(),
                    "max_health" to worldState.player.maxHealth.toString(),
                    "death_detected" to "true"
                )
            )
        }

        // Extract previous room ID from history
        val previousRoomId = if (recentHistory.isNotEmpty()) {
            worldState.player.currentRoomId // This is the room AFTER the last action
        } else {
            null
        }

        // Get current room ID after this action
        val currentRoomId = worldState.player.currentRoomId
        val currentRoom = worldState.getCurrentRoomView()

        // Track inventory state from history
        val inventoryTracker = trackInventoryFromHistory(recentHistory)

        // Try each validation type
        CodeValidationItem.validate(playerInput, gmResponse, inventoryTracker, currentRoom, worldState)
            ?.let { return it }
        CodeValidationMovement.validate(playerInput, gmResponse, recentHistory, currentRoom, worldState)
            ?.let { return it }
        CodeValidationCombat.validate(playerInput, gmResponse, currentRoom, worldState)
            ?.let { return it }
        CodeValidationSocial.validate(playerInput, gmResponse, currentRoom, worldState)
            ?.let { return it }

        // No definitive validation
        return null
    }

    /**
     * Track inventory state by analyzing history for take/drop commands.
     * Returns set of lowercase item names currently in inventory.
     */
    internal fun trackInventoryFromHistory(history: List<TestStep>): Set<String> =
        CodeValidationInventory.trackInventoryFromHistory(history)
}
