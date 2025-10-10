package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Room
import kotlin.random.Random

/**
 * Generates procedural rooms with theme-appropriate traits
 */
class RoomGenerator(
    private val theme: DungeonTheme,
    private val random: Random = Random.Default
) {

    private val roomTypeNames = listOf(
        "Chamber", "Hall", "Corridor", "Passage", "Room", "Gallery",
        "Sanctum", "Vault", "Alcove", "Antechamber", "Vestibule"
    )

    private val roomAdjectives = listOf(
        "Dark", "Narrow", "Vast", "Gloomy", "Forgotten", "Hidden",
        "Ancient", "Crumbling", "Silent", "Abandoned", "Dusty"
    )

    /**
     * Generate a procedural room with theme traits
     */
    fun generateRoom(
        id: String,
        exits: Map<Direction, String> = emptyMap(),
        includeEntities: Boolean = true
    ): Room {
        val name = generateRoomName()
        val traits = selectRandomTraits(minTraits = 4, maxTraits = 7)

        return Room(
            id = id,
            name = name,
            traits = traits,
            exits = exits,
            entities = emptyList()  // Entities added separately by other generators
        )
    }

    /**
     * Generate a themed room name
     */
    fun generateRoomName(): String {
        val adjective = roomAdjectives.random(random)
        val type = roomTypeNames.random(random)
        return "$adjective $type"
    }

    /**
     * Select random traits from theme pool
     */
    private fun selectRandomTraits(minTraits: Int, maxTraits: Int): List<String> {
        val traitCount = random.nextInt(minTraits, maxTraits + 1)
        return theme.roomTraitPool.shuffled(random).take(traitCount)
    }

    /**
     * Generate entrance room (starting point)
     */
    fun generateEntranceRoom(id: String, exits: Map<Direction, String>): Room {
        return Room(
            id = id,
            name = "${theme.displayName} Entrance",
            traits = listOf(
                "massive stone doorway",
                "torch sconces on walls",
                "threshold worn by countless footsteps",
                "sense of ancient history",
                "cool air flowing from within"
            ),
            exits = exits,
            entities = emptyList()
        )
    }

    /**
     * Generate boss room (final challenge)
     */
    fun generateBossRoom(id: String, exits: Map<Direction, String>): Room {
        return Room(
            id = id,
            name = "${theme.displayName} Sanctum",
            traits = listOf(
                "ominous atmosphere",
                "sense of foreboding",
                "grand architecture",
                "mystical energy in the air",
                "elaborate decorations",
                "feeling of being watched"
            ),
            exits = exits,
            entities = emptyList()
        )
    }
}
