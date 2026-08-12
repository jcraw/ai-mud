@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.getCurrentRoomView
import com.jcraw.mud.testbot.TestScenario
import com.jcraw.mud.testbot.TestStep

/**
 * LLM prompt construction for different validation scenarios.
 * Criteria packs live in ValidationCriteria* extracts (MUD-034f).
 */
object ValidationPrompts {
    fun buildSystemPrompt(scenario: TestScenario): String {
        return """
            You are a QA validator for a text-based MUD (Multi-User Dungeon) game engine.
            Your job is to verify that the game responds correctly and coherently to player inputs.

            Scenario: ${scenario.name}
            Description: ${scenario.description}

            CRITICAL: Be LENIENT with validation. Only fail if there's a clear error or crash.
            Normal MUD responses like room descriptions or "You can't go that way" are VALID.

            Validation criteria:
            1. Response is coherent and makes sense given the input
            2. Response follows MUD conventions (room descriptions, combat mechanics, etc.)
            3. Response maintains consistency with previous history
            4. No obvious errors, crashes, or nonsensical text
            5. Response advances the game state appropriately

            DEFAULT TO PASS unless you see a clear problem like:
            - Error messages when action should succeed
            - Crash or exception text
            - Completely nonsensical response
            - Violates game mechanics

            Respond with JSON in this format:
            {
                "pass": true/false,
                "reason": "brief explanation",
                "details": {
                    "coherence": "pass/fail",
                    "consistency": "pass/fail",
                    "mechanics": "pass/fail"
                }
            }
        """.trimIndent()
    }

    fun buildUserContext(
        scenario: TestScenario,
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        expectedOutcome: String?,
        worldState: WorldState?
    ): String {
        // Extract room name from CURRENT response for tracking
        val currentRoomName = gmResponse.lines().firstOrNull()?.trim()?.takeIf {
            it.isNotBlank() && !it.startsWith("You ")
        }

        // Extract room name from PREVIOUS step for movement validation
        val previousRoomName = if (recentHistory.isNotEmpty()) {
            val lastResponse = recentHistory.last().gmResponse
            // Try to extract room name from first line
            lastResponse.lines().firstOrNull()?.trim()?.takeIf {
                it.isNotBlank() && !it.startsWith("You ")
            }
        } else {
            null
        }

        val historyText = if (recentHistory.isEmpty()) {
            "No previous history."
        } else {
            recentHistory.takeLast(2).joinToString("\n") { step ->
                "Player: ${step.playerInput}\nGM: ${step.gmResponse.take(150)}"
            }
        }

        // Track inventory from history for item validation
        val trackedInventory = CodeValidationRules.trackInventoryFromHistory(recentHistory)

        // Add game state context for better validation
        val gameStateContext = buildGameStateContext(
            worldState, previousRoomName, currentRoomName, trackedInventory
        )

        val scenarioCriteria = ValidationScenarioCriteria.forScenario(scenario)
        val expectedText = expectedOutcome?.let { "\nExpected outcome: $it" } ?: ""

        return """
            Recent history:
            $historyText
            $gameStateContext

            Current turn:
            Player input: $playerInput
            GM response: $gmResponse
            $expectedText

            Scenario-specific criteria:
            $scenarioCriteria

            Validate this response.
        """.trimIndent()
    }

    private fun buildGameStateContext(
        worldState: WorldState?,
        previousRoomName: String?,
        currentRoomName: String?,
        trackedInventory: Set<String>
    ): String {
        if (worldState == null) return ""
        val currentRoom = worldState.getCurrentRoomView()
        val player = worldState.player
        val roomTransitionInfo = buildRoomTransitionInfo(previousRoomName, currentRoomName)
        val inventoryInfo = if (trackedInventory.isNotEmpty()) {
            "\n            - Items in inventory (tracked): ${trackedInventory.joinToString(", ")}"
        } else {
            "\n            - Inventory is empty (tracked)"
        }
        return """

            Current game state:
            - Player location: ${currentRoom?.name ?: "Unknown"}$roomTransitionInfo
            - Available exits: ${currentRoom?.exits?.keys?.joinToString(", ") { it.displayName } ?: "none"}
            - Player health: ${player.health}/${player.maxHealth}
            - Room entities: ${currentRoom?.entities?.joinToString(", ") { it.name } ?: "none"}$inventoryInfo
        """.trimIndent()
    }

    private fun buildRoomTransitionInfo(previousRoomName: String?, currentRoomName: String?): String {
        return buildString {
            if (previousRoomName != null && currentRoomName != null) {
                if (previousRoomName != currentRoomName) {
                    append("\n            - ROOM CHANGED: \"$previousRoomName\" → \"$currentRoomName\" (successful movement)")
                } else {
                    append("\n            - Same room name: \"$previousRoomName\" (could be: stayed in place, OR moved to different room with same name)")
                }
            } else if (previousRoomName != null) {
                append("\n            - Previous room: $previousRoomName")
            } else if (currentRoomName != null) {
                append("\n            - Current room from response: $currentRoomName")
            }
        }
    }
}
