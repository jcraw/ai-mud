package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*

/**
 * Adapter layer for V3 graph-based generation to V2 Room system.
 *
 * Converts GraphNodeComponent + SpacePropertiesComponent to Room format,
 * allowing both systems to coexist. V3 generates graph topology and lazy-fills
 * content, while V2 game loop operates on Room objects.
 *
 * Design Philosophy (KISS):
 * - No migration of existing V2 dungeons
 * - V3 as additive generation mode
 * - Runtime conversion at adapter boundary
 * - Gradual migration path available later
 */
object GraphToRoomAdapter {

    /**
     * Convert V3 components to V2 Room.
     *
     * @param nodeId The graph node ID (becomes room ID)
     * @param space The space properties component
     * @param graphNode Optional graph node for additional metadata
     * @return Room object compatible with existing game loop
     */
    fun toRoom(
        nodeId: String,
        space: SpacePropertiesComponent,
        graphNode: GraphNodeComponent? = null
    ): Room {
        // Extract name from description (first sentence)
        val name = extractNameFromDescription(space.description, graphNode?.type)

        // Build traits from space properties
        val traits = buildTraits(space, graphNode)

        // Convert exits to Direction enum map
        val exits = convertExits(space.exits)

        // Convert entities (space stores entity IDs, Room stores Entity objects)
        // For now, entities are handled separately in WorldState
        // This adapter focuses on room structure
        val entities = emptyList<Entity>()

        // Build properties map
        val properties = buildProperties(space)

        return Room(
            id = nodeId,
            name = name,
            traits = traits,
            exits = exits,
            entities = entities,
            properties = properties
        )
    }

    /**
     * Extract room name from description.
     * Falls back to node type-based name if description empty.
     */
    private fun extractNameFromDescription(
        description: String,
        nodeType: com.jcraw.mud.core.world.NodeType?
    ): String {
        if (description.isBlank()) {
            return when (nodeType) {
                is com.jcraw.mud.core.world.NodeType.Hub -> "Central Hub"
                is com.jcraw.mud.core.world.NodeType.Linear -> "Passage"
                is com.jcraw.mud.core.world.NodeType.Branching -> "Junction"
                is com.jcraw.mud.core.world.NodeType.DeadEnd -> "Dead End"
                is com.jcraw.mud.core.world.NodeType.TreasureRoom -> "Treasure Vault"
                is com.jcraw.mud.core.world.NodeType.Boss -> "Boss Chamber"
                is com.jcraw.mud.core.world.NodeType.Frontier -> "Frontier"
                is com.jcraw.mud.core.world.NodeType.Questable -> "Quest Location"
                null -> "Unexplored Space"
            }
        }

        // Extract first sentence or first 40 chars as name
        val firstSentence = description.split('.', '!', '?').firstOrNull()?.trim()
        return if (firstSentence.isNullOrBlank()) {
            "Unknown Room"
        } else {
            firstSentence.take(40).trim()
        }
    }

    /**
     * Build traits list from space properties and graph node.
     * Traits are descriptive tags used for context.
     */
    private fun buildTraits(
        space: SpacePropertiesComponent,
        graphNode: GraphNodeComponent?
    ): List<String> {
        val traits = mutableListOf<String>()

        // Add description as primary trait
        if (space.description.isNotBlank()) {
            traits.add(space.description)
        }

        // Add terrain descriptor
        when (space.terrainType) {
            com.jcraw.mud.core.world.TerrainType.NORMAL -> {} // No trait needed
            com.jcraw.mud.core.world.TerrainType.DIFFICULT -> traits.add("Difficult terrain")
            com.jcraw.mud.core.world.TerrainType.IMPASSABLE -> traits.add("Impassable terrain")
        }

        // Add brightness descriptor
        when {
            space.brightness >= 80 -> traits.add("Brightly lit")
            space.brightness >= 50 -> traits.add("Well lit")
            space.brightness >= 20 -> traits.add("Dimly lit")
            else -> traits.add("Dark")
        }

        // Add node type hint
        graphNode?.type?.let { nodeType ->
            when (nodeType) {
                is com.jcraw.mud.core.world.NodeType.Hub -> traits.add("Central hub area")
                is com.jcraw.mud.core.world.NodeType.Boss -> traits.add("Boss chamber")
                is com.jcraw.mud.core.world.NodeType.Frontier -> traits.add("Frontier boundary")
                is com.jcraw.mud.core.world.NodeType.Questable -> traits.add("Quest location")
                is com.jcraw.mud.core.world.NodeType.TreasureRoom -> traits.add("Treasure vault")
                else -> {} // Linear, branching, dead-end don't need explicit traits
            }
        }

        // Add feature descriptors
        if (space.traps.isNotEmpty()) {
            traits.add("Traps present")
        }
        if (space.resources.isNotEmpty()) {
            traits.add("Resources available")
        }
        if (space.isSafeZone) {
            traits.add("Safe zone - no combat")
        }

        return traits
    }

    /**
     * Convert ExitData list to Direction → RoomId map.
     * Only includes exits that map to valid Direction enum values.
     * Logs skipped exits for debugging.
     */
    private fun convertExits(exits: List<com.jcraw.mud.core.world.ExitData>): Map<Direction, RoomId> {
        val exitMap = mutableMapOf<Direction, RoomId>()

        for (exit in exits) {
            val direction = Direction.fromString(exit.direction)
            if (direction != null) {
                exitMap[direction] = exit.targetId
            } else {
                // Skip non-cardinal directions (e.g., "climb ladder", "through archway")
                // These require custom handling in movement logic
                // Future: Store in properties map for custom movement
            }
        }

        return exitMap
    }

    /**
     * Build properties map from space.
     * Stores V3-specific data for potential future use.
     */
    private fun buildProperties(space: SpacePropertiesComponent): Map<String, String> {
        val properties = mutableMapOf<String, String>()

        properties["brightness"] = space.brightness.toString()
        properties["terrainType"] = space.terrainType.name
        properties["trapCount"] = space.traps.size.toString()
        properties["resourceCount"] = space.resources.size.toString()
        properties["isSafeZone"] = space.isSafeZone.toString()
        properties["isTreasureRoom"] = space.isTreasureRoom.toString()

        // Store non-cardinal exit directions for future custom movement
        val nonCardinalExits = space.exits
            .filter { Direction.fromString(it.direction) == null }
            .map { "${it.direction}:${it.targetId}" }
        if (nonCardinalExits.isNotEmpty()) {
            properties["customExits"] = nonCardinalExits.joinToString(";")
        }

        return properties
    }

    /**
     * Batch convert multiple spaces to rooms.
     * Useful for chunk-level conversion.
     */
    fun toRooms(
        spaces: List<Pair<String, SpacePropertiesComponent>>,
        graphNodes: Map<String, GraphNodeComponent> = emptyMap()
    ): Map<RoomId, Room> {
        return spaces.associate { (nodeId, space) ->
            val graphNode = graphNodes[nodeId]
            nodeId to toRoom(nodeId, space, graphNode)
        }
    }
}
