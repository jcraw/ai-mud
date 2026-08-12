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
 * Rogue perk trees
 * Extracted from PerkDefinitions (MUD-034j pure-move).
 */
internal object PerkTreesRogue {
    val trees: Map<String, List<List<Perk>>> = mapOf(
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
    )
}
