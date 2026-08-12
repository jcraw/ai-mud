@file:Suppress(
    "MagicNumber",
    "MaxLineLength",
    "LargeClass",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

/**
 * Rogue + elemental + advanced magic skill catalog (MUD-034j pure-move).
 */
internal object SkillCatalogRogueMagic {
    /**
     * Rogue skills (5 skills)
     * Tags: ["rogue", "stealth"/"utility"]
     */
    val rogueSkills = listOf(
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
    val elementalMagic = listOf(
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
    val advancedMagic = listOf(
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
}
