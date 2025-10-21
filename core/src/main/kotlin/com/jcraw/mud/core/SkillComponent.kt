package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Skill component for entities (players, NPCs)
 * Tracks all skills, levels, XP, perks, and resource pools
 *
 * Skills are identified by name (e.g., "Sword Fighting", "Fire Magic")
 * This component holds the state for all skills an entity possesses
 */
@Serializable
data class SkillComponent(
    val skills: Map<String, SkillState> = emptyMap(),
    override val componentType: ComponentType = ComponentType.SKILL
) : Component {

    /**
     * Get skill state by name, or null if not present
     */
    fun getSkill(skillName: String): SkillState? {
        return skills[skillName]
    }

    /**
     * Check if entity has a specific skill unlocked
     */
    fun hasSkill(skillName: String): Boolean {
        return skills[skillName]?.unlocked == true
    }

    /**
     * Get effective level for a skill (including buffs)
     */
    fun getEffectiveLevel(skillName: String): Int {
        return skills[skillName]?.getEffectiveLevel() ?: 0
    }

    /**
     * Update a skill's state
     * Returns new SkillComponent with updated skill
     */
    fun updateSkill(skillName: String, skillState: SkillState): SkillComponent {
        return copy(skills = skills + (skillName to skillState))
    }

    /**
     * Add or update a skill
     * If skill doesn't exist, creates it with the provided state
     */
    fun addSkill(skillName: String, skillState: SkillState = SkillState()): SkillComponent {
        return copy(skills = skills + (skillName to skillState))
    }

    /**
     * Remove a skill (rarely used, but useful for testing)
     */
    fun removeSkill(skillName: String): SkillComponent {
        return copy(skills = skills - skillName)
    }

    /**
     * Get all unlocked skills
     */
    fun getUnlockedSkills(): Map<String, SkillState> {
        return skills.filterValues { it.unlocked }
    }

    /**
     * Get all skills with a specific tag
     */
    fun getSkillsByTag(tag: String): Map<String, SkillState> {
        return skills.filterValues { tag in it.tags }
    }

    /**
     * Get all skills with pending perk choices
     */
    fun getSkillsWithPendingPerks(): Map<String, SkillState> {
        return skills.filterValues { it.hasPendingPerkChoice() }
    }

    /**
     * Get resource pool current/max for a resource type
     * (e.g., "mana" → based on "Mana Reserve" skill level * 10)
     * Returns Pair(current, max)
     *
     * Note: Current resource is tracked separately in game state,
     * this just calculates the max pool based on skill level
     */
    fun getResourcePoolMax(resourceType: String): Int {
        val resourceSkill = skills.values.firstOrNull { it.resourceType == resourceType }
        return if (resourceSkill != null && resourceSkill.unlocked) {
            resourceSkill.getEffectiveLevel() * 10
        } else {
            0
        }
    }

    /**
     * Clear all temporary buffs from all skills
     */
    fun clearAllBuffs(): SkillComponent {
        val clearedSkills = skills.mapValues { (_, skill) -> skill.clearBuffs() }
        return copy(skills = clearedSkills)
    }
}
