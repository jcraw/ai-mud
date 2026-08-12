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
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import java.util.UUID

/**
 * Deterministic fallback mob generation for [MobSpawner] (MUD-034g pure move).
 * Preserves RNG call order: archetype.random then health/maxHealth/stats/gold nextInts.
 */
internal object MobSpawnerFallback {

    fun rollFallbackStats(statBase: Int): Stats = Stats(
        strength = statBase + kotlin.random.Random.nextInt(-2, 3),
        dexterity = statBase + kotlin.random.Random.nextInt(-2, 3),
        constitution = statBase + kotlin.random.Random.nextInt(-2, 3),
        intelligence = statBase + kotlin.random.Random.nextInt(-2, 3),
        wisdom = statBase + kotlin.random.Random.nextInt(-2, 3),
        charisma = statBase + kotlin.random.Random.nextInt(-2, 3)
    )

    fun rollHealth(difficulty: Int): Int =
        (difficulty * 10 + kotlin.random.Random.nextInt(-10, 20)).coerceAtLeast(1)

    fun makeFallbackNpc(
        index: Int,
        archetype: String,
        theme: String,
        difficulty: Int,
        healthBase: Int,
        maxHealthBase: Int,
        components: Map<ComponentType, Component>,
        stats: Stats,
        goldDrop: Int
    ): Entity.NPC {
        val npc = Entity.NPC(
            id = "npc_${UUID.randomUUID()}",
            name = "$archetype #$index",
            description = "A $archetype from the $theme.",
            isHostile = true,
            health = healthBase,
            maxHealth = maxHealthBase,
            stats = stats,
            lootTableId = "${theme.lowercase().replace(" ", "_")}_$difficulty",
            goldDrop = goldDrop,
            components = components
        )
        println("[MOB SPAWN DEBUG] Created NPC (fallback): ${npc.name} (health=${npc.health}/${npc.maxHealth}, components=${npc.components.keys})")
        return npc
    }

    fun buildFallbackNpc(
        index: Int,
        archetype: String,
        theme: String,
        difficulty: Int
    ): Entity.NPC {
        val statBase = 8 + (difficulty / 2).coerceAtMost(6)
        val healthBase = rollHealth(difficulty)
        val maxHealthBase = rollHealth(difficulty)
        val components = MobSpawnerCombat.createCombatComponents(healthBase, maxHealthBase, difficulty)
        val stats = rollFallbackStats(statBase)
        val goldDrop = difficulty * 5 + kotlin.random.Random.nextInt(-5, 10)
        return makeFallbackNpc(
            index, archetype, theme, difficulty,
            healthBase, maxHealthBase, components, stats, goldDrop
        )
    }

    fun spawnEntitiesFallback(
        theme: String,
        count: Int,
        difficulty: Int
    ): List<Entity.NPC> {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()
        return (1..count).map { index ->
            buildFallbackNpc(index, profile.mobArchetypes.random(), theme, difficulty)
        }
    }
}
