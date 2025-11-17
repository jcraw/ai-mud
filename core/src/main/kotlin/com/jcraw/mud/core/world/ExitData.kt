package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import kotlinx.serialization.Serializable

/**
 * Condition for exit access
 * Sealed class enables exhaustive when statements and type safety
 */
@Serializable
sealed class Condition {
    /**
     * Check if player meets this condition
     * @param player PlayerState for inventory/item checks
     * @param playerSkills V2 skill component for skill checks
     */
    abstract fun meetsCondition(player: PlayerState, playerSkills: SkillComponent): Boolean

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
        override fun meetsCondition(player: PlayerState, playerSkills: SkillComponent): Boolean {
            // Use V2 skill system
            val skillLevel = playerSkills.getEffectiveLevel(skill)
            return skillLevel >= difficulty
        }

        override fun describe(): String = "requires $skill $difficulty"
    }

    /**
     * Item requirement (e.g., climbing gear, key)
     */
    @Serializable
    data class ItemRequired(val itemTag: String) : Condition() {
        override fun meetsCondition(player: PlayerState, playerSkills: SkillComponent): Boolean {
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
     * @param player PlayerState for inventory/item checks
     * @param playerSkills V2 skill component for skill checks
     */
    fun meetsConditions(player: PlayerState, playerSkills: SkillComponent): Boolean =
        conditions.all { it.meetsCondition(player, playerSkills) }

    /**
     * Describe exit with condition hints
     * @param player PlayerState for inventory/item checks
     * @param playerSkills V2 skill component for skill checks
     */
    fun describeWithConditions(player: PlayerState, playerSkills: SkillComponent): String {
        val meetsAll = meetsConditions(player, playerSkills)
        val conditionText = if (conditions.isNotEmpty() && !meetsAll) {
            val failed = conditions.filter { !it.meetsCondition(player, playerSkills) }
            " (${failed.joinToString(", ") { it.describe() }})"
        } else ""
        return description + conditionText
    }
}
