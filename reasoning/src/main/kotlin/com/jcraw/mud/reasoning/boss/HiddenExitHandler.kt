package com.jcraw.mud.reasoning.boss

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*

/**
 * Handles hidden exit discovery and validation
 * Used for the secret exit to Surface Wilderness in Mid Depths
 */
class HiddenExitHandler {
    /**
     * Check if player can discover/use hidden exit
     *
     * @param player Player attempting to use exit
     * @param exit The exit data with conditions
     * @return HiddenExitResult indicating success or failure with narration
     */
    fun checkHiddenExit(player: PlayerState, exit: ExitData): HiddenExitResult {
        // Check if exit is hidden
        if (!exit.isHidden) {
            return HiddenExitResult.Success("The exit is clearly visible.")
        }

        // Check exit conditions (typically skill checks)
        val passedConditions = exit.conditions.filter { condition ->
            condition.meetsCondition(player)
        }

        // If any condition is met, reveal the exit
        if (passedConditions.isNotEmpty()) {
            val narration = generateDiscoveryNarration(exit, passedConditions.first())
            return HiddenExitResult.Success(narration)
        }

        // All conditions failed
        val narration = generateFailureNarration(exit)
        return HiddenExitResult.Failed(narration, exit.conditions)
    }

    /**
     * Generate narration for successful hidden exit discovery
     *
     * @param exit The discovered exit
     * @param passedCondition The condition that was met
     * @return Discovery narration
     */
    private fun generateDiscoveryNarration(exit: ExitData, passedCondition: Condition): String {
        return buildString {
            when (passedCondition) {
                is Condition.SkillCheck -> {
                    when (passedCondition.skill.lowercase()) {
                        "perception" -> {
                            appendLine("Your keen eyes notice something unusual...")
                            appendLine("A cracked wall, nearly invisible in the shadows.")
                            appendLine("Starlight streams through the fissure.")
                        }
                        "lockpicking" -> {
                            appendLine("Your trained hands trace the wall's surface.")
                            appendLine("You discover a hidden latch, cunningly concealed.")
                            appendLine("With a soft click, the wall shifts aside.")
                        }
                        "strength" -> {
                            appendLine("You push against the wall experimentally.")
                            appendLine("Stone grinds against stone as your strength reveals a secret passage!")
                            appendLine("The wall crumbles inward, exposing a hidden path.")
                        }
                        else -> {
                            appendLine("You discover a hidden passage!")
                        }
                    }
                }
                is Condition.ItemRequired -> {
                    appendLine("Using the ${passedCondition.itemTag}, you unlock the hidden passage.")
                }
            }
            appendLine()
            appendLine(exit.description)
        }
    }

    /**
     * Generate narration for failed hidden exit discovery
     *
     * @param exit The exit that remains hidden
     * @return Failure narration with hints
     */
    private fun generateFailureNarration(exit: ExitData): String {
        val skillChecks = exit.conditions.filterIsInstance<Condition.SkillCheck>()
        val itemChecks = exit.conditions.filterIsInstance<Condition.ItemRequired>()

        return buildString {
            appendLine("The wall looks solid here.")
            if (skillChecks.isNotEmpty()) {
                appendLine()
                append("Perhaps with higher ")
                val skills = skillChecks.map { "${it.skill} (${it.difficulty}+)" }
                appendLine(skills.joinToString(" or ") + " you could discover something...")
            }
            if (itemChecks.isNotEmpty()) {
                appendLine()
                appendLine("Or maybe a specific tool could reveal secrets hidden here.")
            }
        }
    }

    companion object {
        /**
         * Create hidden exit data for Surface Wilderness
         * This is the secret exit in Mid Depths Zone 2
         *
         * @param sourceSpaceId ID of space containing the exit
         * @param targetSpaceId ID of destination space (Surface Wilderness entry)
         * @return ExitData for hidden exit
         */
        fun createSurfaceWildernessExit(sourceSpaceId: String, targetSpaceId: String): ExitData {
            return ExitData(
                targetId = targetSpaceId,
                direction = "crack", // Non-cardinal direction
                description = "Beyond lies the Surface Wilderness, bathed in moonlight. Fresh air beckons.",
                conditions = listOf(
                    Condition.SkillCheck("Perception", 40),
                    Condition.SkillCheck("Lockpicking", 30),
                    Condition.SkillCheck("Strength", 50)
                ),
                isHidden = true
            )
        }
    }
}

/**
 * Result of hidden exit check
 */
sealed class HiddenExitResult {
    /**
     * Exit successfully discovered and accessible
     * @param narration Description of discovery
     */
    data class Success(val narration: String) : HiddenExitResult()

    /**
     * Exit remains hidden or inaccessible
     * @param narration Failure message with hints
     * @param requiredConditions Conditions that must be met
     */
    data class Failed(
        val narration: String,
        val requiredConditions: List<Condition>
    ) : HiddenExitResult()
}
