@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

/**
 * Core stats + combat skill catalog (MUD-034j pure-move).
 */
internal object SkillCatalogCoreCombat {
    /**
     * Core stats (6 skills)
     * Tags: ["stat"]
     */
    val coreStats = listOf(
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
     * Combat skills (11 skills)
     * Tags: ["combat", "weapon"/"armor"]
     */
    val combatSkills = listOf(
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
        ),
        SkillDefinition(
            name = "Dodge",
            description = "Evading and sidestepping attacks. Increases chance to avoid incoming damage.",
            tags = listOf("combat", "defense"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Parry",
            description = "Deflecting attacks with weapon or shield. Reduces damage and creates counter-attack opportunities.",
            tags = listOf("combat", "defense"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Unarmed Combat",
            description = "Fighting without weapons using fists, kicks, and grapples. Damage scales with Strength.",
            tags = listOf("combat", "weapon", "melee", "unarmed"),
            baseUnlockChance = 8
        ),
        SkillDefinition(
            name = "Escape",
            description = "Fleeing from combat and evading pursuit. Increases success chance when retreating from battle.",
            tags = listOf("combat", "mobility", "evasion"),
            baseUnlockChance = 5
        ),
        SkillDefinition(
            name = "Pursuit",
            description = "Chasing and intercepting fleeing targets. Prevents enemies from escaping combat.",
            tags = listOf("combat", "mobility", "tracking"),
            baseUnlockChance = 5
        )
    )
}
