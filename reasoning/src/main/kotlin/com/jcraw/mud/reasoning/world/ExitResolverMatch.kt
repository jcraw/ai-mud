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

import com.jcraw.mud.core.world.ExitData

/**
 * Phase 1–2 exit matching helpers for [ExitResolver] (MUD-034g pure move).
 * Levenshtein stays on host (cognitive residual).
 */
internal object ExitResolverMatch {

    val CARDINAL_DIRECTIONS = setOf(
        "n", "north",
        "s", "south",
        "e", "east",
        "w", "west",
        "ne", "northeast",
        "nw", "northwest",
        "se", "southeast",
        "sw", "southwest",
        "up", "u",
        "down", "d"
    )

    /**
     * Phase 1: Exact case-insensitive match for cardinal directions
     */
    fun phaseOneExactMatch(intent: String, exits: List<ExitData>): ExitData? {
        val normalizedIntent = intent.trim().lowercase()
        return exits.firstOrNull { exit ->
            exit.direction.lowercase() == normalizedIntent &&
                    normalizedIntent in CARDINAL_DIRECTIONS
        }
    }

    /**
     * Phase 2: Fuzzy match using Levenshtein distance for typos
     */
    fun phaseTwoFuzzyMatch(
        intent: String,
        exits: List<ExitData>,
        distance: (String, String) -> Int
    ): ExitData? {
        val normalizedIntent = intent.trim().lowercase()
        val matches = exits.filter { exit ->
            distance(normalizedIntent, exit.direction.lowercase()) <= 2
        }

        // Only return match if unambiguous
        return if (matches.size == 1) matches.first() else null
    }
}
