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
 * Elemental magic perk trees
 * Extracted from PerkDefinitions (MUD-034j pure-move).
 */
internal object PerkTreesElemental {
    val trees: Map<String, List<List<Perk>>> = mapOf(
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
    )
}
