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
 * Resource + Resistance perk trees
 * Extracted from PerkDefinitions (MUD-034j pure-move).
 */
internal object PerkTreesResource {
    val trees: Map<String, List<List<Perk>>> = mapOf(
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
    )
}
