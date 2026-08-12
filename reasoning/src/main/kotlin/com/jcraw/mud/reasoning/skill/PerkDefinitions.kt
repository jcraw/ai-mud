@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.Perk

/**
 * Predefined perk choices for each skill at milestone levels (10, 20, 30, etc.)
 * Each milestone offers 2 choices (A or B)
 *
 * Data tables live in PerkTrees* extracts (MUD-034j).
 */
object PerkDefinitions {

    /**
     * Get perk choices for a skill at a specific level
     * Returns 2 perk options if level is a milestone (10, 20, 30...), empty list otherwise
     */
    fun getPerkChoices(skillName: String, level: Int): List<Perk> {
        if (level % 10 != 0 || level <= 0) {
            return emptyList()
        }

        val milestone = level / 10
        return perkTrees[skillName]?.getOrNull(milestone - 1) ?: emptyList()
    }

    /**
     * Check if a skill has defined perks
     */
    fun hasPerks(skillName: String): Boolean {
        return perkTrees.containsKey(skillName)
    }

    /**
     * Get all milestones defined for a skill
     */
    fun getMilestoneCount(skillName: String): Int {
        return perkTrees[skillName]?.size ?: 0
    }

    /**
     * Perk trees: Map<SkillName, List<Milestone Choices>>
     * Each milestone has 2 perk options
     */
    private val perkTrees: Map<String, List<List<Perk>>> =
        PerkTreesCombat.trees +
            PerkTreesRogue.trees +
            PerkTreesElemental.trees +
            PerkTreesResource.trees +
            PerkTreesCoreStats.trees
}
