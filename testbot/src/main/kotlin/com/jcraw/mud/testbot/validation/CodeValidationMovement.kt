@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.RoomView
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.testbot.TestStep
import com.jcraw.mud.testbot.ValidationResult

/**
 * Movement code validation (MUD-034f).
 */
internal object CodeValidationMovement {

    fun validate(
        playerInput: String,
        gmResponse: String,
        recentHistory: List<TestStep>,
        currentRoom: RoomView?,
        worldState: WorldState
    ): ValidationResult? {
        val movementMatch = movementRegex.find(playerInput) ?: return null
        val directionStr = movementMatch.groupValues[1].lowercase()
        val direction = parseDirection(directionStr) ?: return null
        if (currentRoom == null) return null
        val hadValidExit = hadValidExit(recentHistory, direction, directionStr)
        if (gmResponse.contains("can't go that way", ignoreCase = true)) {
            return validateRejection(direction, hadValidExit)
        }
        return validateRoomHeader(gmResponse, worldState.player.currentRoomId)
    }

    private val movementRegex = Regex(
        "(?:go|move|^)\\s*(north|south|east|west|northeast|northwest|southeast|southwest|up|down|n|s|e|w|ne|nw|se|sw|u|d)(?:\\s|$)",
        RegexOption.IGNORE_CASE
    )

    private fun parseDirection(directionStr: String): Direction? = when (directionStr) {
        "north", "n" -> Direction.NORTH
        "south", "s" -> Direction.SOUTH
        "east", "e" -> Direction.EAST
        "west", "w" -> Direction.WEST
        "northeast", "ne" -> Direction.NORTHEAST
        "northwest", "nw" -> Direction.NORTHWEST
        "southeast", "se" -> Direction.SOUTHEAST
        "southwest", "sw" -> Direction.SOUTHWEST
        "up", "u" -> Direction.UP
        "down", "d" -> Direction.DOWN
        else -> null
    }

    private fun hadValidExit(
        recentHistory: List<TestStep>,
        direction: Direction,
        directionStr: String
    ): Boolean {
        val lastStep = recentHistory.lastOrNull() ?: return true
        val exitsMatch = Regex("Exits: ([^\\n]+)", RegexOption.IGNORE_CASE).find(lastStep.gmResponse)
        return exitsMatch?.groupValues?.get(1)?.split(", ")?.any { exitStr ->
            val exitDirection = exitStr.trim().split(" ")[0].lowercase()
            exitDirection == direction.displayName.lowercase() ||
                exitDirection == directionStr.lowercase()
        } ?: false
    }

    private fun validateRejection(direction: Direction, hadValidExit: Boolean): ValidationResult {
        return if (hadValidExit) {
            ValidationResult(
                pass = false,
                reason = "[CODE] Invalid rejection: exit $direction existed but game rejected movement",
                details = passFailDetails("fail")
            )
        } else {
            ValidationResult(
                pass = true,
                reason = "[CODE] Correctly rejected invalid direction: $direction",
                details = passFailDetails("pass")
            )
        }
    }

    private fun passFailDetails(value: String) = mapOf(
        "validation_type" to "code",
        "coherence" to value,
        "consistency" to value,
        "mechanics" to value
    )

    private fun validateRoomHeader(gmResponse: String, currentRoomId: String): ValidationResult? {
        val roomHeaderMatch = Regex("^([A-Z][a-zA-Z\\s]+)\\n").find(gmResponse) ?: return null
        val roomName = roomHeaderMatch.groupValues[1].trim()
        return ValidationResult(
            pass = true,
            reason = "[CODE] Movement succeeded: entered room '$roomName'",
            details = mapOf(
                "validation_type" to "code",
                "coherence" to "pass",
                "consistency" to "pass",
                "mechanics" to "pass",
                "room_name" to roomName,
                "room_id" to currentRoomId
            )
        )
    }
}
