@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillState

/**
 * Predefined starter skill sets for character archetypes
 * Each archetype starts with 3-5 unlocked skills at level 1
 */
object StarterSkillSets {

    /**
     * Warrior archetype: Strength-based melee fighter
     * Starting skills: Strength, Sword Fighting, Heavy Armor
     */
    val warrior = listOf(
        "Strength",
        "Vitality",
        "Sword Fighting",
        "Heavy Armor"
    )

    /**
     * Rogue archetype: Agility-based stealth fighter
     * Starting skills: Agility, Stealth, Lockpicking
     */
    val rogue = listOf(
        "Agility",
        "Stealth",
        "Lockpicking",
        "Light Armor"
    )

    /**
     * Mage archetype: Intelligence-based spellcaster
     * Starting skills: Intelligence, Fire Magic, Mana Reserve
     */
    val mage = listOf(
        "Intelligence",
        "Wisdom",
        "Fire Magic",
        "Mana Reserve",
        "Gesture Casting"
    )

    /**
     * Cleric archetype: Wisdom-based support caster
     * Starting skills: Wisdom, Water Magic, Mana Reserve
     */
    val cleric = listOf(
        "Wisdom",
        "Vitality",
        "Water Magic",
        "Mana Reserve",
        "Chant Casting"
    )

    /**
     * Bard archetype: Charisma-based social character
     * Starting skills: Charisma, Diplomacy, Light Armor
     */
    val bard = listOf(
        "Charisma",
        "Agility",
        "Diplomacy",
        "Light Armor"
    )

    /**
     * Get starter skills for an archetype
     * Returns list of skill names
     */
    fun getStarterSkills(archetype: String): List<String> {
        return when (archetype.lowercase()) {
            "warrior" -> warrior
            "rogue" -> rogue
            "mage" -> mage
            "cleric" -> cleric
            "bard" -> bard
            else -> emptyList()
        }
    }

    /**
     * Get all archetype names
     */
    fun getArchetypes(): List<String> {
        return listOf("Warrior", "Rogue", "Mage", "Cleric", "Bard")
    }

    /**
     * Create unlocked skill states at level 1 for starter skills
     */
    fun createStarterSkillStates(archetype: String): Map<String, SkillState> {
        val starterSkillNames = getStarterSkills(archetype)
        return starterSkillNames.mapNotNull { skillName ->
            SkillDefinitions.getSkill(skillName)?.let { definition ->
                skillName to definition.toSkillState().copy(
                    unlocked = true,
                    level = 1,
                    xp = 0L
                )
            }
        }.toMap()
    }
}
