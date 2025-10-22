package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.PerkType

/**
 * Predefined perk choices for each skill at milestone levels (10, 20, 30, etc.)
 * Each milestone offers 2 choices (A or B)
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
    private val perkTrees: Map<String, List<List<Perk>>> = mapOf(
        // COMBAT SKILLS
        "Sword Fighting" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Quick Strike",
                    description = "Perform a lightning-fast attack (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("damageBonus" to "25", "cooldown" to "3")
                ),
                Perk(
                    name = "Feint",
                    description = "Deceive opponent to create opening (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("defenseReduction" to "20", "duration" to "2")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Blade Mastery",
                    description = "+15% damage with swords (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("damageBonus" to "15")
                ),
                Perk(
                    name = "Riposte",
                    description = "+10% chance to counter-attack (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("counterChance" to "10")
                )
            ),
            // Level 30
            listOf(
                Perk(
                    name = "Whirlwind Strike",
                    description = "Attack all enemies in range (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("aoeRadius" to "3", "cooldown" to "5")
                ),
                Perk(
                    name = "Precision",
                    description = "+20% critical hit chance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("critChance" to "20")
                )
            )
        ),

        "Axe Mastery" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Cleave",
                    description = "Strike splits damage to adjacent enemies (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("splitDamage" to "50", "cooldown" to "3")
                ),
                Perk(
                    name = "Rend Armor",
                    description = "Destroy enemy armor on hit (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("armorReduction" to "30", "duration" to "3")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Heavy Impact",
                    description = "+20% damage with axes (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("damageBonus" to "20")
                ),
                Perk(
                    name = "Armor Breaker",
                    description = "Ignore 15% of enemy armor (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("armorPenetration" to "15")
                )
            )
        ),

        "Bow Accuracy" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Power Shot",
                    description = "Charge a devastating arrow (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("damageMultiplier" to "200", "chargeTime" to "1")
                ),
                Perk(
                    name = "Multi-Shot",
                    description = "Fire 3 arrows at once (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("arrowCount" to "3", "cooldown" to "4")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Eagle Eye",
                    description = "+25% ranged accuracy (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("accuracyBonus" to "25")
                ),
                Perk(
                    name = "Critical Aim",
                    description = "+15% critical hit damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("critDamage" to "15")
                )
            )
        ),

        // ARMOR SKILLS
        "Light Armor" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Evasion",
                    description = "+15% dodge chance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("dodgeChance" to "15")
                ),
                Perk(
                    name = "Mobility",
                    description = "+20% movement speed (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("movementSpeed" to "20")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Reflex Guard",
                    description = "Automatically dodge first attack each turn (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("autoDodge" to "1")
                ),
                Perk(
                    name = "Acrobatics",
                    description = "Reduce fall damage by 50% (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("fallReduction" to "50")
                )
            )
        ),

        "Heavy Armor" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Iron Skin",
                    description = "+10 flat damage reduction (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("damageReduction" to "10")
                ),
                Perk(
                    name = "Unbreakable",
                    description = "+20% resistance to critical hits (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("critResistance" to "20")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Fortress",
                    description = "+25% armor effectiveness (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("armorBonus" to "25")
                ),
                Perk(
                    name = "Retaliation",
                    description = "Reflect 10% of damage taken (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("damageReflect" to "10")
                )
            )
        ),

        // ROGUE SKILLS
        "Stealth" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Shadow Step",
                    description = "Teleport short distance while hidden (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("range" to "5", "cooldown" to "4")
                ),
                Perk(
                    name = "Vanish",
                    description = "Enter stealth instantly in combat (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("duration" to "2", "cooldown" to "6")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Silent Movement",
                    description = "+30% stealth effectiveness (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("stealthBonus" to "30")
                ),
                Perk(
                    name = "Ambush",
                    description = "+50% damage on first attack from stealth (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("ambushDamage" to "50")
                )
            )
        ),

        "Backstab" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Kidney Shot",
                    description = "Stun target for 1 turn (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("stunDuration" to "1", "cooldown" to "5")
                ),
                Perk(
                    name = "Hemorrhage",
                    description = "Apply bleeding damage over time (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("dotDamage" to "15", "duration" to "3")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Lethal Strike",
                    description = "+25% backstab damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("backstabBonus" to "25")
                ),
                Perk(
                    name = "Find Weakness",
                    description = "Ignore 20% of target's armor on backstab (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("armorPenetration" to "20")
                )
            )
        ),

        "Lockpicking" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Speed Picking",
                    description = "Pick locks 50% faster (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("speedBonus" to "50")
                ),
                Perk(
                    name = "Delicate Touch",
                    description = "Never break lockpicks (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("unbreakable" to "true")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Master Picker",
                    description = "+20% success on difficult locks (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("difficultyReduction" to "20")
                ),
                Perk(
                    name = "Treasure Sense",
                    description = "Detect quality of chest contents before opening (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("detection" to "true")
                )
            )
        ),

        // ELEMENTAL MAGIC
        "Fire Magic" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Fireball Volley",
                    description = "Launch 3 fireballs in rapid succession (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("projectileCount" to "3", "cooldown" to "4")
                ),
                Perk(
                    name = "Flame Shield",
                    description = "Burn attackers who hit you (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("reflectDamage" to "30", "duration" to "3")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Pyromaniac",
                    description = "+20% fire spell damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spellDamageBonus" to "20")
                ),
                Perk(
                    name = "Burn",
                    description = "Fire spells apply burning DoT (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("dotDamage" to "10", "duration" to "3")
                )
            ),
            // Level 30
            listOf(
                Perk(
                    name = "Meteor Strike",
                    description = "Call down a massive meteor (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("aoeDamage" to "200", "radius" to "5", "cooldown" to "10")
                ),
                Perk(
                    name = "Inferno",
                    description = "Fire spells have 15% chance to spread to nearby enemies (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spreadChance" to "15", "spreadRadius" to "3")
                )
            )
        ),

        "Water Magic" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Healing Wave",
                    description = "Heal yourself and allies (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("healAmount" to "50", "aoeRadius" to "4", "cooldown" to "5")
                ),
                Perk(
                    name = "Ice Barrier",
                    description = "Create protective ice shield (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("shieldAmount" to "75", "duration" to "3")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Fluid Motion",
                    description = "+15% spell casting speed (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("castSpeedBonus" to "15")
                ),
                Perk(
                    name = "Chill",
                    description = "Water spells slow enemies by 20% (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("slowPercent" to "20", "duration" to "2")
                )
            )
        ),

        "Earth Magic" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Stone Skin",
                    description = "Harden your body to resist damage (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("damageReduction" to "40", "duration" to "3")
                ),
                Perk(
                    name = "Earthen Grasp",
                    description = "Root enemies in place (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("rootDuration" to "2", "cooldown" to "4")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Boulder Toss",
                    description = "+25% earth spell damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spellDamageBonus" to "25")
                ),
                Perk(
                    name = "Tremor",
                    description = "Earth spells have chance to knock down enemies (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("knockdownChance" to "20")
                )
            )
        ),

        "Air Magic" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Lightning Bolt",
                    description = "Instant-cast lightning strike (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("damage" to "80", "cooldown" to "3")
                ),
                Perk(
                    name = "Gust",
                    description = "Push enemies away with wind (active ability)",
                    type = PerkType.ABILITY,
                    effectData = mapOf("knockbackRange" to "5", "cooldown" to "4")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Storm Caller",
                    description = "+20% air spell damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spellDamageBonus" to "20")
                ),
                Perk(
                    name = "Chain Lightning",
                    description = "Lightning spells jump to additional targets (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("chainTargets" to "2")
                )
            )
        ),

        // RESOURCE SKILLS
        "Mana Reserve" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Deep Pool",
                    description = "+20% maximum mana (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("manaBonus" to "20")
                ),
                Perk(
                    name = "Efficient Casting",
                    description = "-10% spell mana cost (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("costReduction" to "10")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Boundless Reserve",
                    description = "+30% maximum mana (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("manaBonus" to "30")
                ),
                Perk(
                    name = "Arcane Battery",
                    description = "Store excess mana up to 150% of maximum (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("overfillPercent" to "150")
                )
            )
        ),

        "Mana Flow" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Rapid Regeneration",
                    description = "+50% mana regeneration rate (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("regenBonus" to "50")
                ),
                Perk(
                    name = "Combat Focus",
                    description = "Regenerate mana even in combat (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("combatRegen" to "true")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Arcane Surge",
                    description = "Burst of mana regeneration after killing enemy (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("burstRegen" to "50")
                ),
                Perk(
                    name = "Meditation",
                    description = "Double regeneration rate when not in combat (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("outOfCombatMultiplier" to "2")
                )
            )
        ),

        // RESISTANCE SKILLS
        "Fire Resistance" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Flame Ward",
                    description = "+15% fire damage reduction (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("fireResistance" to "15")
                ),
                Perk(
                    name = "Ember Absorption",
                    description = "Heal 25% of fire damage taken (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("damageToHeal" to "25")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Inferno Immunity",
                    description = "+25% fire damage reduction (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("fireResistance" to "25")
                ),
                Perk(
                    name = "Phoenix",
                    description = "Revive with 50% health when killed by fire (once per day, passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("reviveHealth" to "50", "cooldown" to "86400")
                )
            )
        ),

        "Poison Resistance" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Poison Ward",
                    description = "+20% poison resistance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("poisonResistance" to "20")
                ),
                Perk(
                    name = "Cleansing",
                    description = "Automatically remove poison after 2 turns (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("autoCleanse" to "2")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Venom Immunity",
                    description = "+30% poison resistance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("poisonResistance" to "30")
                ),
                Perk(
                    name = "Toxic Blood",
                    description = "Poison melee attackers for 10 damage/turn (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("reflectPoison" to "10", "duration" to "3")
                )
            )
        ),

        // CORE STATS
        "Strength" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Mighty Blow",
                    description = "+15% melee damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("meleeDamageBonus" to "15")
                ),
                Perk(
                    name = "Carry Capacity",
                    description = "+50% inventory capacity (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("inventoryBonus" to "50")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Colossus",
                    description = "+10% maximum health (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("healthBonus" to "10")
                ),
                Perk(
                    name = "Crushing Force",
                    description = "20% chance to stun on melee hit (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("stunChance" to "20")
                )
            )
        ),

        "Agility" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Quick Reflexes",
                    description = "+10% dodge chance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("dodgeChance" to "10")
                ),
                Perk(
                    name = "Fleet Footed",
                    description = "+25% movement speed (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("movementSpeed" to "25")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Acrobat",
                    description = "+15% dodge and critical hit chance (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("dodgeChance" to "15", "critChance" to "15")
                ),
                Perk(
                    name = "Double Strike",
                    description = "20% chance to attack twice (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("doubleAttackChance" to "20")
                )
            )
        ),

        "Intelligence" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Arcane Insight",
                    description = "+15% spell damage (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spellDamageBonus" to "15")
                ),
                Perk(
                    name = "Quick Learner",
                    description = "+25% skill XP gain (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("xpBonus" to "25")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Genius",
                    description = "+20% spell damage and -10% spell cost (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("spellDamageBonus" to "20", "costReduction" to "10")
                ),
                Perk(
                    name = "Arcane Mastery",
                    description = "Spells have 15% chance to not consume mana (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("freeCastChance" to "15")
                )
            )
        ),

        "Wisdom" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Insightful",
                    description = "+20% experience from all sources (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("xpBonus" to "20")
                ),
                Perk(
                    name = "Perceptive",
                    description = "Detect hidden enemies and traps (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("detection" to "true")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Sage",
                    description = "+30% XP gain and +10% gold find (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("xpBonus" to "30", "goldBonus" to "10")
                ),
                Perk(
                    name = "Inner Peace",
                    description = "Immune to fear and confusion effects (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("ccImmunity" to "true")
                )
            )
        ),

        "Charisma" to listOf(
            // Level 10
            listOf(
                Perk(
                    name = "Persuasive",
                    description = "+20% success on social checks (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("socialBonus" to "20")
                ),
                Perk(
                    name = "Intimidating",
                    description = "Enemies have -10% damage against you (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("enemyDamageReduction" to "10")
                )
            ),
            // Level 20
            listOf(
                Perk(
                    name = "Natural Leader",
                    description = "+15% damage for all allies (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("allyDamageBonus" to "15")
                ),
                Perk(
                    name = "Master Negotiator",
                    description = "20% better prices with merchants (passive)",
                    type = PerkType.PASSIVE,
                    effectData = mapOf("priceBonus" to "20")
                )
            )
        )
    )
}
