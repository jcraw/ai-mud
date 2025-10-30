package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import kotlinx.serialization.Serializable

/**
 * Condition for exit access
 * Sealed class enables exhaustive when statements and type safety
 */
@Serializable
sealed class Condition {
    /**
     * Check if player meets this condition
     */
    abstract fun meetsCondition(player: PlayerState): Boolean

    /**
     * Describe the condition in natural language
     */
    abstract fun describe(): String

    /**
     * Skill check requirement (e.g., Perception 15 for hidden exit)
     */
    @Serializable
    data class SkillCheck(
        val skill: String,
        val difficulty: Int
    ) : Condition() {
        override fun meetsCondition(player: PlayerState): Boolean {
            val skillLevel = player.getSkillLevel(skill)
            return skillLevel >= difficulty
        }

        override fun describe(): String = "requires $skill $difficulty"
    }

    /**
     * Item requirement (e.g., climbing gear, key)
     */
    @Serializable
    data class ItemRequired(val itemTag: String) : Condition() {
        override fun meetsCondition(player: PlayerState): Boolean {
            // Check if player has any item (basic implementation)
            // TODO: Check actual item tags when ItemRepository integration is complete
            return player.inventory.isNotEmpty() ||
                   player.equippedWeapon != null ||
                   player.equippedArmor != null
        }

        override fun describe(): String = "requires $itemTag"
    }
}

/**
 * Exit data with flexible conditions
 */
@Serializable
data class ExitData(
    val targetId: String,
    val direction: String,
    val description: String,
    val conditions: List<Condition> = emptyList(),
    val isHidden: Boolean = false,
    val hiddenDifficulty: Int? = null  // DC for Perception check if hidden
) {
    /**
     * Check if player meets all conditions to use this exit
     */
    fun meetsConditions(player: PlayerState): Boolean =
        conditions.all { it.meetsCondition(player) }

    /**
     * Describe exit with condition hints
     */
    fun describeWithConditions(player: PlayerState): String {
        val meetsAll = meetsConditions(player)
        val conditionText = if (conditions.isNotEmpty() && !meetsAll) {
            val failed = conditions.filter { !it.meetsCondition(player) }
            " (${failed.joinToString(", ") { it.describe() }})"
        } else ""
        return description + conditionText
    }
}
