@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
    "MaxLineLength",
)

package com.jcraw.mud.perception

import com.jcraw.mud.core.Direction

/**
 * Fallback parser arms: navigation / look / search / interact.
 * Pure extract (MUD-034c) — behavior unchanged.
 */
internal object IntentFallbackNav {

    fun parseMoveCommand(command: String, args: String?): Intent {
        val direction = when (command) {
            "n", "north" -> Direction.NORTH
            "s", "south" -> Direction.SOUTH
            "e", "east" -> Direction.EAST
            "w", "west" -> Direction.WEST
            "ne", "northeast" -> Direction.NORTHEAST
            "nw", "northwest" -> Direction.NORTHWEST
            "se", "southeast" -> Direction.SOUTHEAST
            "sw", "southwest" -> Direction.SOUTHWEST
            "u", "up" -> Direction.UP
            "d", "down" -> Direction.DOWN
            "go", "move" -> Direction.fromString(args ?: "") ?: return Intent.Invalid("Go where?")
            else -> return Intent.Invalid("Unknown direction")
        }
        return Intent.Move(direction)
    }

    fun parseLook(args: String?): Intent {
        // Intent.Check if examining treasure room pedestals/altars
        return if (args != null && (args.contains("pedestal", ignoreCase = true) || args.contains("altar", ignoreCase = true))) {
            Intent.ExaminePedestal(args)
        } else {
            Intent.Look(args)
        }
    }

    fun parseSearch(args: String?): Intent = Intent.Search(args)

    fun parseInteract(args: String?): Intent =
        if (args.isNullOrBlank()) Intent.Invalid("Intent.Interact with what?") else Intent.Interact(args)
}
