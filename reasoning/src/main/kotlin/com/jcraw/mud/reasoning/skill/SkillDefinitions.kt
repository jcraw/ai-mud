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

/**
 * Catalog of all predefined skills in the game
 * Organized by category for easy reference
 */
object SkillDefinitions {

    /**
     * Core stats (6 skills)
     * Tags: ["stat"]
     */
    private val coreStats = listOf(
        SkillDefinition(
            name = "Strength",
            description = "Physical power and brute force. Affects melee damage and carrying capacity.",
            tags = listOf("stat", "physical"),
            baseUnlockChance = 10 // Stats easier to unlock
        ),
        SkillDefinition(
            name = "Agility",
            description = "Speed, reflexes, and dexterity. Affects dodge chance and attack speed.",
            tags = listOf("stat", "physical"),
            baseUnlockChance = 10
        ),
        SkillDefinition(
            name = "Vitality",
            description = "Endurance and constitution. Affects maximum health and recovery rate.",
            tags = listOf("stat", "physical"),
            baseUnlockChance = 10
        ),
        SkillDefinition(
            name = "Intelligence",
            description = "Mental acuity and knowledge. Affects spell damage and mana pool.",
            tags = listOf("stat", "mental"),
            baseUnlockChance = 10
        ),
        SkillDefinition(
            name = "Wisdom",
            description = "Perception and insight. Affects experience gain and detection.",
            tags = listOf("stat", "mental"),
            baseUnlockChance = 10
        ),
        SkillDefinition(
            name = "Charisma",
            description = "Force of personality and charm. Affects social interactions and leadership.",
            tags = listOf("stat", "social"),
            baseUnlockChance = 10
        )
    )

    /**
     * Combat skills (6 skills)
     * Tags: ["combat", "weapon"/"armor"]
     */
    private val combatSkills = listOf(
        SkillDefinition(
            name = "Sword Fighting",
            description = "Proficiency with bladed weapons. Increases accuracy and damage with swords.",
            tags = listOf("combat", "weapon", "melee"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Axe Mastery",
            description = "Skill with axes and heavy chopping weapons. High damage, armor penetration.",
            tags = listOf("combat", "weapon", "melee"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Bow Accuracy",
            description = "Precision with ranged weapons. Increases hit chance and critical damage with bows.",
            tags = listOf("combat", "weapon", "ranged"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Light Armor",
            description = "Expertise with leather and light armor. Balances protection with mobility.",
            tags = listOf("combat", "armor", "defense"),
            baseUnlockChance = 8
        ),
        SkillDefinition(
            name = "Heavy Armor",
            description = "Mastery of plate and heavy armor. Maximum protection at cost of speed.",
            tags = listOf("combat", "armor", "defense"),
            baseUnlockChance = 8
        ),
        SkillDefinition(
            name = "Shield Use",
            description = "Blocking and shield bash techniques. Reduces incoming damage and enables counters.",
            tags = listOf("combat", "defense"),
            baseUnlockChance = 6
        )
    )

    /**
     * Rogue skills (5 skills)
     * Tags: ["rogue", "stealth"/"utility"]
     */
    private val rogueSkills = listOf(
        SkillDefinition(
            name = "Stealth",
            description = "Art of moving unseen. Enables sneaking, hiding, and surprise attacks.",
            tags = listOf("rogue", "stealth"),
            baseUnlockChance = 4
        ),
        SkillDefinition(
            name = "Backstab",
            description = "Devastating sneak attacks from behind. Massive damage to unaware targets.",
            tags = listOf("rogue", "combat", "stealth"),
            baseUnlockChance = 3,
            prerequisites = mapOf("Stealth" to 5) // Requires Stealth 5
        ),
        SkillDefinition(
            name = "Lockpicking",
            description = "Opening locks without keys. Grants access to locked chests and doors.",
            tags = listOf("rogue", "utility"),
            baseUnlockChance = 4
        ),
        SkillDefinition(
            name = "Trap Disarm",
            description = "Detecting and disabling traps. Prevents damage and enables safe looting.",
            tags = listOf("rogue", "utility"),
            baseUnlockChance = 4
        ),
        SkillDefinition(
            name = "Trap Setting",
            description = "Creating and deploying traps. Damage enemies before combat begins.",
            tags = listOf("rogue", "utility"),
            baseUnlockChance = 3,
            prerequisites = mapOf("Trap Disarm" to 3) // Requires Trap Disarm 3
        )
    )

    /**
     * Elemental magic skills (7 skills)
     * Tags: ["magic", "elemental"]
     */
    private val elementalMagic = listOf(
        SkillDefinition(
            name = "Fire Magic",
            description = "Conjuring flames and heat. High damage over time and area effects.",
            tags = listOf("magic", "elemental", "fire"),
            baseUnlockChance = 3
        ),
        SkillDefinition(
            name = "Water Magic",
            description = "Manipulating water and ice. Healing, slowing, and defensive spells.",
            tags = listOf("magic", "elemental", "water"),
            baseUnlockChance = 3
        ),
        SkillDefinition(
            name = "Earth Magic",
            description = "Commanding stone and earth. Defensive buffs and crowd control.",
            tags = listOf("magic", "elemental", "earth"),
            baseUnlockChance = 3
        ),
        SkillDefinition(
            name = "Air Magic",
            description = "Harnessing wind and lightning. Fast-cast, high-damage spells.",
            tags = listOf("magic", "elemental", "air"),
            baseUnlockChance = 3
        ),
        SkillDefinition(
            name = "Gesture Casting",
            description = "Somatic spellcasting through hand movements. Faster casting, no verbal components.",
            tags = listOf("magic", "casting"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Chant Casting",
            description = "Verbal spellcasting through incantations. More powerful but slower spells.",
            tags = listOf("magic", "casting"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Magical Projectile Accuracy",
            description = "Aiming spells at distant targets. Improves hit chance for ranged magic.",
            tags = listOf("magic", "accuracy"),
            baseUnlockChance = 6
        )
    )

    /**
     * Advanced magic skills (3 skills)
     * Tags: ["magic", "advanced"]
     */
    private val advancedMagic = listOf(
        SkillDefinition(
            name = "Summoning",
            description = "Calling creatures from other planes. Summons allies to fight for you.",
            tags = listOf("magic", "advanced", "summoning"),
            baseUnlockChance = 2,
            prerequisites = mapOf("Intelligence" to 10) // Requires Intelligence 10
        ),
        SkillDefinition(
            name = "Necromancy",
            description = "Raising and controlling undead. Animate corpses to serve you.",
            tags = listOf("magic", "advanced", "necromancy"),
            baseUnlockChance = 2,
            prerequisites = mapOf("Intelligence" to 10) // Requires Intelligence 10
        ),
        SkillDefinition(
            name = "Elemental Affinity",
            description = "Deep attunement to elemental forces. Massive boost to all elemental magic.",
            tags = listOf("magic", "advanced", "elemental"),
            baseUnlockChance = 1,
            prerequisites = mapOf("Fire Magic" to 50) // Requires Fire Magic 50 (or any elemental)
        )
    )

    /**
     * Resource pool skills (4 skills)
     * Tags: ["resource"]
     * ResourceType: "mana" or "chi"
     */
    private val resourceSkills = listOf(
        SkillDefinition(
            name = "Mana Reserve",
            description = "Capacity for magical energy. Maximum mana = level * 10.",
            tags = listOf("resource", "magic", "pool"),
            baseUnlockChance = 5,
            resourceType = "mana"
        ),
        SkillDefinition(
            name = "Mana Flow",
            description = "Mana regeneration rate. Regen = level * 2 per turn.",
            tags = listOf("resource", "magic", "regen"),
            baseUnlockChance = 5,
            resourceType = "mana"
        ),
        SkillDefinition(
            name = "Chi Reserve",
            description = "Capacity for inner energy. Maximum chi = level * 10.",
            tags = listOf("resource", "martial", "pool"),
            baseUnlockChance = 5,
            resourceType = "chi"
        ),
        SkillDefinition(
            name = "Chi Flow",
            description = "Chi regeneration rate. Regen = level * 2 per turn.",
            tags = listOf("resource", "martial", "regen"),
            baseUnlockChance = 5,
            resourceType = "chi"
        )
    )

    /**
     * Resistance skills (3 skills)
     * Tags: ["resistance"]
     */
    private val resistanceSkills = listOf(
        SkillDefinition(
            name = "Fire Resistance",
            description = "Resistance to fire damage. Reduces fire damage by (level / 2)%.",
            tags = listOf("resistance", "fire"),
            baseUnlockChance = 6
        ),
        SkillDefinition(
            name = "Poison Resistance",
            description = "Resistance to poison and toxins. Reduces poison damage by (level / 2)%.",
            tags = listOf("resistance", "poison"),
            baseUnlockChance = 6
        ),
        SkillDefinition(
            name = "Slashing Resistance",
            description = "Resistance to slashing damage. Reduces slashing damage by (level / 2)%.",
            tags = listOf("resistance", "physical"),
            baseUnlockChance = 6
        )
    )

    /**
     * Other utility skills (2 skills)
     * Tags: ["utility"]
     */
    private val otherSkills = listOf(
        SkillDefinition(
            name = "Blacksmithing",
            description = "Crafting and repairing weapons and armor. Higher level = better quality.",
            tags = listOf("utility", "crafting"),
            baseUnlockChance = 4
        ),
        SkillDefinition(
            name = "Diplomacy",
            description = "Art of negotiation and persuasion. Improves outcomes in social interactions.",
            tags = listOf("social", "utility"),
            baseUnlockChance = 6
        )
    )

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
