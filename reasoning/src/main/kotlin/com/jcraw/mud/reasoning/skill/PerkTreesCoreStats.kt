@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.Perk
import com.jcraw.mud.core.PerkType

/**
 * Core stat perk trees
 * Extracted from PerkDefinitions (MUD-034j pure-move).
 */
internal object PerkTreesCoreStats {
    val trees: Map<String, List<List<Perk>>> = mapOf(
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
