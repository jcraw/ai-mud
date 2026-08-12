@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

/**
 * Catalog of all predefined skills in the game
 * Organized by category for easy reference
 *
 * Data tables live in SkillCatalog* extracts; SkillDefinition type is separate (MUD-034j).
 */
object SkillDefinitions {

    private val coreStats = SkillCatalogCoreCombat.coreStats
    private val combatSkills = SkillCatalogCoreCombat.combatSkills
    private val rogueSkills = SkillCatalogRogueMagic.rogueSkills
    private val elementalMagic = SkillCatalogRogueMagic.elementalMagic
    private val advancedMagic = SkillCatalogRogueMagic.advancedMagic
    private val resourceSkills = SkillCatalogResourceOther.resourceSkills
    private val resistanceSkills = SkillCatalogResourceOther.resistanceSkills
    private val otherSkills = SkillCatalogResourceOther.otherSkills

    /**
     * All skill definitions indexed by name
     */
    val allSkills: Map<String, SkillDefinition> = (
        coreStats +
        combatSkills +
        rogueSkills +
        elementalMagic +
        advancedMagic +
        resourceSkills +
        resistanceSkills +
        otherSkills
    ).associateBy { it.name }

    /**
     * Get skills by tag filter
     */
    fun getSkillsByTag(tag: String): List<SkillDefinition> {
        return allSkills.values.filter { it.tags.contains(tag) }
    }

    /**
     * Get skills by category
     */
    fun getCoreStats(): List<SkillDefinition> = coreStats
    fun getCombatSkills(): List<SkillDefinition> = combatSkills
    fun getRogueSkills(): List<SkillDefinition> = rogueSkills
    fun getElementalMagic(): List<SkillDefinition> = elementalMagic
    fun getAdvancedMagic(): List<SkillDefinition> = advancedMagic
    fun getResourceSkills(): List<SkillDefinition> = resourceSkills
    fun getResistanceSkills(): List<SkillDefinition> = resistanceSkills

    /**
     * Get skill definition by name
     */
    fun getSkill(name: String): SkillDefinition? {
        return allSkills[name]
    }

    /**
     * Check if a skill exists
     */
    fun skillExists(name: String): Boolean {
        return allSkills.containsKey(name)
    }

    /**
     * Get total skill count
     */
    fun getSkillCount(): Int {
        return allSkills.size
    }
}
