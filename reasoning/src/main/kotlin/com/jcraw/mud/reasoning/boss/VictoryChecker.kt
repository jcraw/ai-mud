package com.jcraw.mud.reasoning.boss

import com.jcraw.mud.core.*

/**
 * Checks victory conditions for the Ancient Abyss dungeon
 * Victory requires: Return to town (safe zone) with Abyss Heart in inventory
 */
class VictoryChecker {
    /**
     * Check if victory condition is met
     *
     * @param player Current player state
     * @param currentSpace Current space properties
     * @return VictoryResult indicating victory status
     */
    fun checkVictoryCondition(player: PlayerState, currentSpace: SpacePropertiesComponent): VictoryResult {
        // Check condition 1: In safe zone (town)
        if (!currentSpace.isSafeZone) {
            return VictoryResult.NotYet("Not in a safe zone")
        }

        // Check condition 2: Has Abyss Heart in inventory (V2 InventoryComponent)
        val hasAbyssHeart = player.inventoryComponent.items.any { item ->
            item.templateId.equals(BossLootHandler.ABYSS_HEART_TEMPLATE_ID, ignoreCase = true)
        }

        if (!hasAbyssHeart) {
            return VictoryResult.NotYet("Abyss Heart not in inventory")
        }

        // Victory achieved!
        val narration = generateVictoryNarration(player)
        return VictoryResult.Won(narration)
    }

    /**
     * Generate victory narration for player
     * Uses fallback since LLM integration would require dependency injection
     *
     * @param player The victorious player
     * @return Epic victory narration string
     */
    fun generateVictoryNarration(player: PlayerState): String {
        // Fallback narration (LLM integration would happen in handlers layer)
        return buildString {
            appendLine("═══════════════════════════════════════════")
            appendLine("            VICTORY!")
            appendLine("═══════════════════════════════════════════")
            appendLine()
            appendLine("You have triumphed, ${player.name}!")
            appendLine()
            appendLine("The Abyss Heart pulses with eldritch power in your grasp.")
            appendLine("The ancient evil that plagued these depths is vanquished.")
            appendLine("The dungeon trembles, its darkness receding before your might.")
            appendLine()
            appendLine("You stand victorious, a legend forged in the Abyss.")
            appendLine()
            appendLine("Your deeds will be remembered for generations to come.")
            appendLine("═══════════════════════════════════════════")
        }
    }

    /**
     * Generate LLM prompt for custom victory narration
     * This can be used by handlers layer with LLM service
     *
     * @param player The victorious player
     * @return Prompt string for LLM
     */
    fun generateVictoryPrompt(player: PlayerState): String {
        return """
            Generate epic victory narration for a fantasy dungeon game.

            Player name: ${player.name}
            Achievement: Retrieved the Abyss Heart and returned to Town, conquering the Ancient Abyss Dungeon

            Requirements:
            - 3-4 dramatic sentences
            - Emphasize the magnitude of the achievement
            - Mention the Abyss Heart artifact
            - Convey a sense of legendary accomplishment
            - Keep tone epic fantasy (Dark Souls / D&D style)

            Output only the narration text, no meta-commentary.
        """.trimIndent()
    }
}

/**
 * Sealed class representing victory check results
 */
sealed class VictoryResult {
    /**
     * Victory condition met
     * @param narration Epic victory narration to display
     */
    data class Won(val narration: String) : VictoryResult()

    /**
     * Victory condition not yet met
     * @param reason Why victory hasn't been achieved (for debugging/hints)
     */
    data class NotYet(val reason: String) : VictoryResult()
}
