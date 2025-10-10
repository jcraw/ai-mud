package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
enum class Direction(val displayName: String) {
    NORTH("north"),
    SOUTH("south"),
    EAST("east"),
    WEST("west"),
    UP("up"),
    DOWN("down"),
    NORTHEAST("northeast"),
    NORTHWEST("northwest"),
    SOUTHEAST("southeast"),
    SOUTHWEST("southwest");

    companion object {
        fun fromString(input: String): Direction? {
            return entries.find { it.displayName.equals(input, ignoreCase = true) }
        }

        val opposites = mapOf(
            NORTH to SOUTH,
            SOUTH to NORTH,
            EAST to WEST,
            WEST to EAST,
            UP to DOWN,
            DOWN to UP,
            NORTHEAST to SOUTHWEST,
            NORTHWEST to SOUTHEAST,
            SOUTHEAST to NORTHWEST,
            SOUTHWEST to NORTHEAST
        )
    }

    val opposite: Direction?
        get() = opposites[this]
}