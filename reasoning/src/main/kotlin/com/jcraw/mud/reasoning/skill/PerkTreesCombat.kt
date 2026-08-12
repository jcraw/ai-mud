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
 * Combat + Armor perk trees
 * Extracted from PerkDefinitions (MUD-034j pure-move).
 */
internal object PerkTreesCombat {
    val trees: Map<String, List<List<Perk>>> = mapOf(
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
    )
}
