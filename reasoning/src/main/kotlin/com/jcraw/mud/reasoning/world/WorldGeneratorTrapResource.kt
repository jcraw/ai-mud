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

import com.jcraw.mud.core.world.ResourceNode
import com.jcraw.mud.core.world.TrapData
import kotlin.random.Random

/**
 * Theme-appropriate trap and resource generation (MUD-034g pure move).
 * Preserves RNG call order within each public entry.
 */
internal object WorldGeneratorTrapResource {

    fun selectTrapType(theme: String): String {
        return when {
            "forest" in theme.lowercase() -> listOf("bear trap", "pit trap", "snare").random()
            "cave" in theme.lowercase() || "magma" in theme.lowercase() -> listOf("lava pool", "collapsing floor", "gas vent").random()
            "crypt" in theme.lowercase() || "tomb" in theme.lowercase() -> listOf("poison dart", "cursed rune", "arrow trap").random()
            "castle" in theme.lowercase() || "fortress" in theme.lowercase() -> listOf("spike trap", "swinging blade", "falling portcullis").random()
            else -> listOf("pit trap", "spike trap", "poison dart").random()
        }
    }

    /**
     * Generates theme-appropriate trap.
     */
    fun generateTrap(theme: String, difficultyLevel: Int): TrapData {
        val trapType = selectTrapType(theme)
        val id = "trap_${java.util.UUID.randomUUID().toString().take(8)}"
        val difficulty = (10 + difficultyLevel + Random.nextInt(-2, 3)).coerceIn(5, 25)

        return TrapData(
            id = id,
            type = trapType,
            difficulty = difficulty,
            triggered = false,
            description = "A $trapType lurks here"
        )
    }

    fun selectResourceType(theme: String): String {
        return when {
            "forest" in theme.lowercase() -> listOf("wood", "herbs", "berries").random()
            "cave" in theme.lowercase() -> listOf("iron ore", "coal", "crystal").random()
            "magma" in theme.lowercase() -> listOf("obsidian", "sulfur", "fire crystal").random()
            "crypt" in theme.lowercase() -> listOf("bone", "arcane dust", "ancient cloth").random()
            else -> listOf("stone", "wood", "herbs").random()
        }
    }

    /**
     * Generates theme-appropriate resource node.
     */
    fun generateResource(theme: String): ResourceNode {
        val resourceType = selectResourceType(theme)
        val id = "resource_${java.util.UUID.randomUUID().toString().take(8)}"
        val templateId = resourceType.replace(" ", "_").lowercase()

        return ResourceNode(
            id = id,
            templateId = templateId,
            quantity = Random.nextInt(1, 6),
            respawnTime = if (Random.nextBoolean()) 100 + Random.nextInt(50) else null
        )
    }
}
