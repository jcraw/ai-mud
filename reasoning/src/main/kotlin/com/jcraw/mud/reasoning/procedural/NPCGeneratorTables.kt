@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.procedural

/**
 * Suffix / personality / trait tables for [NPCGenerator] (MUD-034n).
 */
internal object NPCGeneratorTables {

    val npcSuffixes = listOf(
        "Warrior", "Mage", "Rogue", "Brute", "Sentinel",
        "Berserker", "Assassin", "Champion", "Warden", "Destroyer"
    )

    // Personality templates based on dungeon theme
    val hostilePersonalities = mapOf(
        DungeonTheme.CRYPT to listOf(
            "undead revenant consumed by hatred",
            "cursed guardian bound to eternal service",
            "mindless skeleton driven by dark magic",
            "vengeful spirit seeking living souls"
        ),
        DungeonTheme.CASTLE to listOf(
            "battle-hardened knight sworn to defend",
            "ruthless mercenary seeking glory",
            "proud champion of the realm",
            "fanatical zealot defending their lord"
        ),
        DungeonTheme.CAVE to listOf(
            "savage beast defending its territory",
            "primitive warrior protecting the tribe",
            "territorial predator hunting intruders",
            "feral creature driven by instinct"
        ),
        DungeonTheme.TEMPLE to listOf(
            "zealous cultist devoted to dark gods",
            "corrupted priest spreading heresy",
            "fanatic guardian protecting sacred grounds",
            "possessed warrior controlled by divine will"
        )
    )

    val friendlyPersonalities = mapOf(
        DungeonTheme.CRYPT to listOf(
            "ancient spirit offering cryptic wisdom",
            "melancholic ghost seeking redemption",
            "scholarly lich pursuing knowledge",
            "wandering soul longing for peace"
        ),
        DungeonTheme.CASTLE to listOf(
            "noble courtier with courtly manners",
            "wise advisor offering counsel",
            "friendly merchant seeking profit",
            "helpful servant maintaining the halls"
        ),
        DungeonTheme.CAVE to listOf(
            "curious explorer seeking adventure",
            "hermit sage living in solitude",
            "friendly dwarf mining for ore",
            "peaceful druid tending to nature"
        ),
        DungeonTheme.TEMPLE to listOf(
            "devout priest offering blessings",
            "humble monk seeking enlightenment",
            "compassionate healer aiding travelers",
            "wise oracle sharing prophecies"
        )
    )

    // Trait pools for variety
    val hostileTraits = listOf(
        "aggressive", "ruthless", "merciless", "cunning",
        "fearless", "brutal", "savage", "vengeful"
    )

    val friendlyTraits = listOf(
        "helpful", "wise", "patient", "curious",
        "generous", "humble", "honorable", "compassionate"
    )

    val neutralTraits = listOf(
        "cautious", "observant", "pragmatic", "stoic",
        "mysterious", "reserved", "calculating", "diplomatic"
    )
}
