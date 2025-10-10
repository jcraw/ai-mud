package com.jcraw.mud.reasoning.procedural

import com.jcraw.mud.core.Direction
import kotlin.random.Random

/**
 * Node in the dungeon graph representing a room location
 */
data class DungeonNode(
    val id: String,
    val x: Int,
    val y: Int,
    val connections: MutableMap<Direction, String> = mutableMapOf(),
    val isEntrance: Boolean = false,
    val isBoss: Boolean = false
)

/**
 * Generates dungeon layout as a graph structure
 */
class DungeonLayoutGenerator(
    private val random: Random = Random.Default
) {

    /**
     * Generate a dungeon layout with specified number of rooms
     * Uses a growing algorithm to create interconnected rooms
     */
    fun generateLayout(
        roomCount: Int = 10,
        branchingFactor: Double = 0.3  // Probability of creating branches
    ): List<DungeonNode> {
        require(roomCount >= 3) { "Dungeon must have at least 3 rooms (entrance, boss, and 1 other)" }

        val nodes = mutableListOf<DungeonNode>()
        val occupiedPositions = mutableSetOf<Pair<Int, Int>>()

        // Create entrance at origin
        val entrance = DungeonNode(
            id = "room_0",
            x = 0,
            y = 0,
            isEntrance = true
        )
        nodes.add(entrance)
        occupiedPositions.add(0 to 0)

        // Track frontier rooms that can be expanded from
        val frontier = mutableListOf(entrance)

        // Generate remaining rooms
        var roomIndex = 1
        while (nodes.size < roomCount && frontier.isNotEmpty()) {
            // Pick random frontier room to expand from
            val currentNode = frontier.random(random)

            // Try to add a room in an available direction
            val availableDirections = getAvailableDirections(currentNode, occupiedPositions)

            if (availableDirections.isEmpty()) {
                // Dead end - remove from frontier
                frontier.remove(currentNode)
                continue
            }

            // Pick random direction
            val direction = availableDirections.random(random)
            val (newX, newY) = getPositionInDirection(currentNode.x, currentNode.y, direction)

            // Create new room
            val isBossRoom = nodes.size == roomCount - 1  // Last room is boss room
            val newNode = DungeonNode(
                id = "room_$roomIndex",
                x = newX,
                y = newY,
                isBoss = isBossRoom
            )

            // Connect rooms bidirectionally
            currentNode.connections[direction] = newNode.id
            newNode.connections[direction.opposite()] = currentNode.id

            nodes.add(newNode)
            occupiedPositions.add(newX to newY)
            roomIndex++

            // Add to frontier with probability based on branching factor
            if (random.nextDouble() < branchingFactor) {
                frontier.add(newNode)
            }

            // Remove current from frontier if it's full
            if (getAvailableDirections(currentNode, occupiedPositions).isEmpty()) {
                frontier.remove(currentNode)
            }
        }

        // Add some cross-connections for non-linear dungeons (optional loops)
        addCrossConnections(nodes, occupiedPositions)

        return nodes
    }

    /**
     * Get available directions from a node (not blocked by existing rooms)
     */
    private fun getAvailableDirections(
        node: DungeonNode,
        occupiedPositions: Set<Pair<Int, Int>>
    ): List<Direction> {
        return Direction.entries.filter { direction ->
            // Don't use if already connected in this direction
            if (node.connections.containsKey(direction)) return@filter false

            // Check if position in this direction is free
            val (newX, newY) = getPositionInDirection(node.x, node.y, direction)
            (newX to newY) !in occupiedPositions
        }
    }

    /**
     * Calculate position offset in a direction
     */
    private fun getPositionInDirection(x: Int, y: Int, direction: Direction): Pair<Int, Int> {
        return when (direction) {
            Direction.NORTH -> x to y + 1
            Direction.SOUTH -> x to y - 1
            Direction.EAST -> x + 1 to y
            Direction.WEST -> x - 1 to y
            Direction.UP -> x to y + 2      // Vertical movement uses larger offset
            Direction.DOWN -> x to y - 2
            Direction.NORTHEAST -> x + 1 to y + 1
            Direction.NORTHWEST -> x - 1 to y + 1
            Direction.SOUTHEAST -> x + 1 to y - 1
            Direction.SOUTHWEST -> x - 1 to y - 1
        }
    }

    /**
     * Get opposite direction for bidirectional connections
     */
    private fun Direction.opposite(): Direction = when (this) {
        Direction.NORTH -> Direction.SOUTH
        Direction.SOUTH -> Direction.NORTH
        Direction.EAST -> Direction.WEST
        Direction.WEST -> Direction.EAST
        Direction.UP -> Direction.DOWN
        Direction.DOWN -> Direction.UP
        Direction.NORTHEAST -> Direction.SOUTHWEST
        Direction.NORTHWEST -> Direction.SOUTHEAST
        Direction.SOUTHEAST -> Direction.NORTHWEST
        Direction.SOUTHWEST -> Direction.NORTHEAST
    }

    /**
     * Add some cross-connections to create loops (makes dungeon less linear)
     */
    private fun addCrossConnections(
        nodes: List<DungeonNode>,
        occupiedPositions: Set<Pair<Int, Int>>
    ) {
        // Try to add 1-3 cross connections
        val maxCrossConnections = random.nextInt(1, 4)
        var added = 0

        for (node in nodes.shuffled(random)) {
            if (added >= maxCrossConnections) break

            // Look for adjacent rooms not yet connected
            for (direction in Direction.entries) {
                val (targetX, targetY) = getPositionInDirection(node.x, node.y, direction)

                // Skip if no room at target position or already connected
                if ((targetX to targetY) !in occupiedPositions) continue
                if (node.connections.containsKey(direction)) continue

                // Find the target node
                val targetNode = nodes.find { it.x == targetX && it.y == targetY } ?: continue

                // Don't create cross-connections to entrance or boss rooms
                if (targetNode.isEntrance || targetNode.isBoss) continue

                // Add bidirectional connection
                node.connections[direction] = targetNode.id
                targetNode.connections[direction.opposite()] = node.id
                added++
                break
            }
        }
    }
}
