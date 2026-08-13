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

import kotlin.random.Random

/**
 * Theme title map for [QuestGenerator] (MUD-034n).
 */
internal object QuestGeneratorTitles {

    val questTitles = mapOf(
        DungeonTheme.CRYPT to listOf(
            "Cleanse the Tomb",
            "Retrieve the Lost Relic",
            "Put the Dead to Rest",
            "Silence the Necromancer",
            "Recover Ancient Bones"
        ),
        DungeonTheme.CASTLE to listOf(
            "Reclaim the Throne Room",
            "Find the Royal Seal",
            "Defeat the Usurper",
            "Recover the Crown Jewels",
            "Restore Honor to the Keep"
        ),
        DungeonTheme.CAVE to listOf(
            "Clear the Goblin Nest",
            "Mine the Rare Crystals",
            "Defeat the Cave Troll",
            "Map the Deep Tunnels",
            "Retrieve the Earth Stone"
        ),
        DungeonTheme.TEMPLE to listOf(
            "Purify the Sanctuary",
            "Recover Sacred Texts",
            "Defeat the False Prophet",
            "Restore the Divine Wards",
            "Find the Holy Artifact"
        )
    )

    fun title(theme: DungeonTheme, random: Random, fallback: String): String {
        return questTitles[theme]?.randomOrNull(random) ?: fallback
    }
}
