@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

/**
 * Resource + resistance + other skill catalog (MUD-034j pure-move).
 */
internal object SkillCatalogResourceOther {
    /**
     * Resource pool skills (4 skills)
     * Tags: ["resource"]
     * ResourceType: "mana" or "chi"
     */
    val resourceSkills = listOf(
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
    val resistanceSkills = listOf(
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
    val otherSkills = listOf(
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
}
