package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.repository.SkillComponentRepository
import com.jcraw.mud.memory.MemoryManager
import kotlinx.coroutines.runBlocking

/**
 * Manages perk choices and selection at skill milestone levels (10, 20, 30, etc.)
 *
 * Perks are granted at milestone levels (every 10 levels).
 * At each milestone, the player chooses 1 of 2 predefined perks for that skill.
 *
 * Optionally integrates with MemoryManager for RAG-enhanced narratives
 */
class PerkSelector(
    private val componentRepository: SkillComponentRepository,
    private val memoryManager: MemoryManager? = null
) {

    /**
     * Get available perk choices for a skill at a specific level
     * Returns 2 perk options if level is a milestone (10, 20, 30...), empty list otherwise
     */
    fun getPerkChoices(skillName: String, level: Int): List<Perk> {
        return PerkDefinitions.getPerkChoices(skillName, level)
    }

    /**
     * Select a perk for a skill
     *
     * @param entityId Entity choosing the perk
     * @param skillName Name of the skill
     * @param perkChoice The chosen perk (must be one of the available choices)
     * @return SkillEvent.PerkUnlocked if successful, null if validation fails
     */
    fun selectPerk(entityId: String, skillName: String, perkChoice: Perk): SkillEvent? {
        val component = componentRepository.load(entityId).getOrNull() ?: SkillComponent()
        val skillState = component.getSkill(skillName) ?: return null

        // Validate perk is available at current level
        val availablePerks = getPerkChoices(skillName, skillState.level)
        if (perkChoice !in availablePerks) {
            return null // Invalid perk choice
        }

        // Check if player hasn't already chosen this milestone's perk
        if (!skillState.hasPendingPerkChoice()) {
            return null // No pending perk choice
        }

        // Add perk to skill state
        val updatedSkillState = skillState.addPerk(perkChoice)
        val updatedComponent = component.updateSkill(skillName, updatedSkillState)

        // Persist to database
        componentRepository.save(entityId, updatedComponent)

        val event = SkillEvent.PerkUnlocked(
            entityId = entityId,
            skillName = skillName,
            perk = perkChoice,
            skillLevel = skillState.level,
            timestamp = System.currentTimeMillis()
        )

        // Log to memory for RAG
        memoryManager?.let { mm ->
            runBlocking {
                mm.remember(
                    "Unlocked perk '${perkChoice.name}' for $skillName at level ${skillState.level}",
                    metadata = mapOf("skill" to skillName, "event_type" to "perk_unlocked", "perk" to perkChoice.name)
                )
            }
        }

        return event
    }

    /**
     * Check if an entity has a pending perk choice for a skill
     *
     * A pending perk choice exists when:
     * - Skill has reached a milestone level (10, 20, 30...)
     * - Player hasn't chosen the perk for that milestone yet
     */
    fun hasPendingPerkChoice(entityId: String, skillName: String): Boolean {
        val component = componentRepository.load(entityId).getOrNull() ?: return false
        val skillState = component.getSkill(skillName) ?: return false

        return skillState.hasPendingPerkChoice()
    }

    /**
     * Get all skills with pending perk choices for an entity
     */
    fun getAllPendingPerkChoices(entityId: String): Map<String, List<Perk>> {
        val component = componentRepository.load(entityId).getOrNull() ?: return emptyMap()
        val pendingSkills = component.getSkillsWithPendingPerks()

        return pendingSkills.mapNotNull { entry ->
            val skillName = entry.key
            val skillState = entry.value
            val choices = getPerkChoices(skillName, skillState.level)
            if (choices.isNotEmpty()) {
                skillName to choices
            } else {
                null
            }
        }.toMap()
    }

    /**
     * Get skill state for an entity
     * Helper method to check skill details
     */
    fun getSkillComponent(entityId: String): SkillComponent? {
        return componentRepository.load(entityId).getOrNull()
    }

    /**
     * Check if a skill has any perks defined
     */
    fun hasPerks(skillName: String): Boolean {
        return PerkDefinitions.hasPerks(skillName)
    }

    /**
     * Get the milestone number for a level
     * Returns the milestone number (1 for level 10, 2 for level 20, etc.)
     * Returns 0 if not a milestone level
     */
    fun getMilestoneNumber(level: Int): Int {
        return if (level % 10 == 0 && level > 0) {
            level / 10
        } else {
            0
        }
    }
}
