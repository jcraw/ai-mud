@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillState
import kotlinx.serialization.Serializable

/**
 * Metadata for a skill definition
 * Used to define the catalog of available skills
 */
@Serializable
data class SkillDefinition(
    val name: String,
    val description: String,
    val tags: List<String>, // e.g., ["combat", "weapon"], ["magic", "fire"], ["stat"]
    val baseUnlockChance: Int = 5, // % chance to unlock on attempt (d100 < baseUnlockChance)
    val prerequisites: Map<String, Int> = emptyMap(), // Map of skill name -> required level
    val resourceType: String? = null // For resource pool skills: "mana", "chi"
) {
    /**
     * Create initial SkillState from this definition (locked, level 0)
     */
    fun toSkillState(): SkillState {
        return SkillState(
            level = 0,
            xp = 0L,
            unlocked = false,
            tags = tags,
            perks = emptyList(),
            resourceType = resourceType,
            tempBuffs = 0
        )
    }

    /**
     * Check if prerequisites are met for this skill
     */
    fun prerequisitesMet(entitySkills: Map<String, SkillState>): Boolean {
        return prerequisites.all { (skillName, requiredLevel) ->
            val skill = entitySkills[skillName]
            skill != null && skill.unlocked && skill.level >= requiredLevel
        }
    }
}
