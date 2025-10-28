package com.jcraw.mud.core.crafting

/**
 * Represents a crafting recipe
 *
 * @property id Unique recipe identifier
 * @property name Display name of the recipe
 * @property inputItems Map of template IDs to required quantities
 * @property outputItem Template ID of the crafted item
 * @property requiredSkill Skill required for crafting (e.g., "Blacksmithing", "Alchemy")
 * @property minSkillLevel Minimum skill level required
 * @property requiredTools List of tool tags required (e.g., ["blacksmith_tool"])
 * @property difficulty Skill check DC for crafting attempt
 */
data class Recipe(
    val id: String,
    val name: String,
    val inputItems: Map<String, Int>,
    val outputItem: String,
    val requiredSkill: String,
    val minSkillLevel: Int,
    val requiredTools: List<String>,
    val difficulty: Int
) {
    /**
     * Check if player has required skill level
     */
    fun meetsSkillRequirement(skillLevel: Int): Boolean {
        return skillLevel >= minSkillLevel
    }

    /**
     * Check if player has required tools (by checking for tags in inventory)
     */
    fun hasRequiredTools(availableToolTags: Set<String>): Boolean {
        if (requiredTools.isEmpty()) return true
        return requiredTools.any { it in availableToolTags }
    }
}
