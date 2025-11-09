package com.jcraw.mud.core

import com.jcraw.mud.core.world.ChunkLevel
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.core.world.TerrainType

/**
 * Configuration for building a V3 WorldState from legacy Room definitions.
 * Used by SampleDungeon and tests to avoid reintroducing V2 storage.
 */
data class LegacyWorldConfig(
    val chunkId: String = "legacy_chunk",
    val chunkLevel: ChunkLevel = ChunkLevel.SUBZONE,
    val lore: String = "Legacy dungeon import.",
    val biomeTheme: String = "legacy_dungeon",
    val mobDensity: Double = 0.2,
    val difficultyLevel: Int = 1
)

/**
 * Convert legacy Room maps into a V3-compatible WorldState.
 * All runtime data is stored using GraphNodeComponent + SpacePropertiesComponent.
 */
fun buildWorldStateFromRooms(
    rooms: Map<RoomId, Room>,
    player: PlayerState,
    config: LegacyWorldConfig = LegacyWorldConfig()
): WorldState {
    val chunk = WorldChunkComponent(
        level = config.chunkLevel,
        parentId = null,
        children = rooms.keys.toList(),
        lore = config.lore,
        biomeTheme = config.biomeTheme,
        sizeEstimate = rooms.size.coerceAtLeast(1),
        mobDensity = config.mobDensity,
        difficultyLevel = config.difficultyLevel
    )

    val graphNodes = rooms.values.associate { room ->
        room.id to GraphNodeComponent(
            id = room.id,
            type = inferNodeType(room.id, room),
            neighbors = room.exits.map { (direction, targetId) ->
                EdgeData(
                    targetId = targetId,
                    direction = direction.displayName
                )
            },
            chunkId = config.chunkId
        )
    }

    val entityMap = mutableMapOf<String, Entity>()
    val spaces = rooms.values.associate { room ->
        val exitData = room.exits.map { (direction, targetId) ->
            ExitData(
                targetId = targetId,
                direction = direction.displayName,
                description = "A ${direction.displayName} passage toward ${rooms[targetId]?.name ?: "another area"}."
            )
        }

        room.entities.forEach { entity ->
            entityMap[entity.id] = entity
        }

        val description = if (room.traits.isNotEmpty()) {
            room.traits.joinToString(". ") + "."
        } else {
            "A featureless stretch of the dungeon."
        }

        room.id to SpacePropertiesComponent(
            name = room.name,
            description = description,
            exits = exitData,
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            entities = room.entities.map { it.id }
        )
    }

    var worldState = WorldState(
        graphNodes = graphNodes,
        spaces = spaces,
        chunks = mapOf(config.chunkId to chunk),
        players = mapOf(player.id to player),
        entities = entityMap
    )

    worldState = worldState.copy(
        gameProperties = worldState.gameProperties + ("starting_space" to player.currentRoomId)
    )

    return worldState
}

fun buildWorldStateFromRooms(
    rooms: Map<RoomId, Room>,
    players: Map<PlayerId, PlayerState>,
    config: LegacyWorldConfig = LegacyWorldConfig()
): WorldState {
    require(players.isNotEmpty()) { "At least one player required" }
    val primary = players.values.first()
    var worldState = buildWorldStateFromRooms(rooms, primary, config)
    players.values.drop(1).forEach { player ->
        worldState = worldState.addPlayer(player)
    }
    return worldState
}

private fun inferNodeType(roomId: RoomId, room: Room): NodeType {
    return when {
        roomId.equals("entrance", ignoreCase = true) -> NodeType.Hub
        roomId.contains("secret", ignoreCase = true) -> NodeType.Boss
        room.exits.size <= 1 -> NodeType.DeadEnd
        room.exits.size == 2 -> NodeType.Linear
        else -> NodeType.Branching
    }
}
