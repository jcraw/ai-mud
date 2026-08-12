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

/**
 * Direction helpers for [ExitLinker] (MUD-034g pure move).
 */
internal object ExitLinkerDirections {

    fun isVerticalDirection(direction: String): Boolean {
        return direction in setOf("u", "up", "d", "down") ||
               direction.contains("climb") || direction.contains("descend") ||
               direction.contains("ascend") || direction.contains("ladder") ||
               direction.contains("stairs")
    }

    /**
     * Creates the opposite direction for a given direction.
     * Maps cardinal directions to their opposites and attempts to reverse
     * natural language descriptions.
     */
    fun createReciprocalExit(direction: String): String {
        val normalized = direction.trim().lowercase()

        return when (normalized) {
            "n", "north" -> "south"
            "s", "south" -> "north"
            "e", "east" -> "west"
            "w", "west" -> "east"
            "ne", "northeast" -> "southwest"
            "nw", "northwest" -> "southeast"
            "se", "southeast" -> "northwest"
            "sw", "southwest" -> "northeast"
            "u", "up" -> "down"
            "d", "down" -> "up"
            else -> reverseNaturalLanguageDirection(normalized)
        }
    }

    /**
     * Attempts to reverse a natural language direction description.
     * Uses simple keyword substitution for common patterns.
     */
    fun reverseNaturalLanguageDirection(description: String): String {
        return when {
            description.contains("climb") -> description.replace("climb", "descend")
            description.contains("ascend") -> description.replace("ascend", "descend")
            description.contains("descend") -> description.replace("descend", "ascend")
            description.contains("enter") -> description.replace("enter", "exit")
            description.contains("exit") -> description.replace("exit", "enter")
            description.contains("through") -> description // Symmetric
            description.contains("into") -> description.replace("into", "out of")
            description.contains("out of") -> description.replace("out of", "into")
            else -> description  // Can't reverse, keep original
        }
    }

    /**
     * Creates a description for the reciprocal exit based on the original.
     */
    fun createReciprocalDescription(
        originalDescription: String,
        originalDirection: String,
        reciprocalDirection: String
    ): String {
        // For cardinal directions, create a simple description
        if (reciprocalDirection in setOf("north", "south", "east", "west", "up", "down",
                "northeast", "northwest", "southeast", "southwest")) {
            return "A passage leading $reciprocalDirection"
        }

        // For natural language, try to preserve the original description with reversed direction
        return originalDescription.replace(
            originalDirection,
            reciprocalDirection,
            ignoreCase = true
        ).ifEmpty {
            "The way back"
        }
    }

    /**
     * Collapses duplicate exits in the same direction, keeping the first occurrence.
     */
    fun collapseDuplicateExits(exits: List<com.jcraw.mud.core.world.ExitData>): List<com.jcraw.mud.core.world.ExitData> {
        val seen = mutableSetOf<String>()
        return exits.filter { exit ->
            val normalized = exit.direction.trim().lowercase()
            if (normalized in seen) {
                false
            } else {
                seen.add(normalized)
                true
            }
        }
    }
}
