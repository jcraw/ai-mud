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

import com.jcraw.mud.core.SocialComponent
import kotlin.random.Random

/**
 * Hostile / friendly / boss SocialComponent for [NPCGenerator] (MUD-034n).
 */
internal object NPCGeneratorSocial {

    /**
     * Generate SocialComponent for hostile NPC
     * Hostile NPCs start with negative disposition and aggressive traits
     */
    fun generateHostile(theme: DungeonTheme, random: Random): SocialComponent {
        val personality = NPCGeneratorTables.hostilePersonalities[theme]?.random(random)
            ?: "hostile warrior"

        // Select 1-3 traits
        val traitCount = random.nextInt(1, 4)
        val traits = (NPCGeneratorTables.hostileTraits + NPCGeneratorTables.neutralTraits)
            .shuffled(random)
            .take(traitCount)

        // Hostile NPCs start with unfriendly to hostile disposition
        val disposition = random.nextInt(-75, -25)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }

    /**
     * Generate SocialComponent for friendly NPC
     * Friendly NPCs start with positive disposition and helpful traits
     */
    fun generateFriendly(theme: DungeonTheme, random: Random): SocialComponent {
        val personality = NPCGeneratorTables.friendlyPersonalities[theme]?.random(random)
            ?: "friendly traveler"

        // Select 1-3 traits
        val traitCount = random.nextInt(1, 4)
        val traits = (NPCGeneratorTables.friendlyTraits + NPCGeneratorTables.neutralTraits)
            .shuffled(random)
            .take(traitCount)

        // Friendly NPCs start with friendly disposition
        val disposition = random.nextInt(25, 60)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }

    /**
     * Generate SocialComponent for boss NPC
     * Bosses start with very hostile disposition and intimidating traits
     */
    fun generateBoss(theme: DungeonTheme, random: Random): SocialComponent {
        val personality = NPCGeneratorTables.hostilePersonalities[theme]?.firstOrNull()
            ?: "powerful overlord"

        // Bosses get more traits (2-4)
        val traitCount = random.nextInt(2, 5)
        val traits = (NPCGeneratorTables.hostileTraits + listOf("powerful", "commanding", "intimidating"))
            .shuffled(random)
            .take(traitCount)

        // Bosses start very hostile
        val disposition = random.nextInt(-100, -75)

        return SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = traits
        )
    }
}
