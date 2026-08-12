@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction

/**
 * Direction / compound-command pure helpers for [IntentRecognizer].
 * Pure extract (MUD-034c) — no parsing semantics change.
 */
internal object IntentDirectionParse {

    /**
     * Split compound commands by taking only the first action.
     * Handles "and", "then", commas, etc.
     */
    fun splitCompoundCommand(input: String): String {
        // Split on common conjunctions and punctuation
        val separators = listOf(" and ", " then ", ", and ", ", then ", ",")

        for (separator in separators) {
            val parts = input.split(separator, ignoreCase = true, limit = 2)
            if (parts.size > 1) {
                // Return only the first part
                return parts[0].trim()
            }
        }

        // No compound detected, return as-is
        return input
    }

    /**
     * Intent.Check if input is ONLY a cardinal direction (no extra words).
     * Also handles "go <direction>" and "move <direction>" patterns.
     * Returns the Direction if it's a pure cardinal direction, null otherwise.
     */
    fun parseCardinalDirection(input: String): Direction? {
        val trimmed = input.trim().lowercase()

        // Handle "go <direction>" and "move <direction>" patterns
        val goPattern = Regex("^(?:go|move)\\s+(\\w+)$")
        val goMatch = goPattern.find(trimmed)
        if (goMatch != null) {
            val directionPart = goMatch.groupValues[1]
            return parseDirectionWord(directionPart)
        }

        // Intent.Check if input is ONLY a cardinal direction (no extra words)
        return parseDirectionWord(trimmed)
    }

    /**
     * Parse a single word as a direction.
     */
    fun parseDirectionWord(word: String): Direction? {
        return when (word) {
            // Full names
            "north", "south", "east", "west" -> Direction.fromString(word)
            "northeast", "northwest", "southeast", "southwest" -> Direction.fromString(word)
            "up", "down" -> Direction.fromString(word)

            // Abbreviations
            "n" -> Direction.NORTH
            "s" -> Direction.SOUTH
            "e" -> Direction.EAST
            "w" -> Direction.WEST
            "ne" -> Direction.NORTHEAST
            "nw" -> Direction.NORTHWEST
            "se" -> Direction.SOUTHEAST
            "sw" -> Direction.SOUTHWEST
            "u" -> Direction.UP
            "d" -> Direction.DOWN

            else -> null
        }
    }
}
