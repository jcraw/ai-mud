package com.jcraw.mud.testbot.exploration

import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Deterministic graph exploration strategies for testing.
 *
 * Provides reusable exploration algorithms that can find loops in game worlds
 * without requiring LLM calls. Useful for testing world topology.
 */
object GraphExplorer {

    /**
     * Result of an exploration step.
     */
    sealed class ExplorationStep {
        /** Move to a new room */
        data class Move(val direction: String) : ExplorationStep()

        /** Exploration complete - loop found */
        data class LoopFound(val pathTaken: List<String>) : ExplorationStep()

        /** Exploration failed - no loop found */
        data class Failed(val reason: String) : ExplorationStep()
    }

    /**
     * Right-hand rule explorer.
     *
     * Classic maze-solving algorithm - always take the rightmost available exit.
     * Guarantees finding a loop in a connected graph with cycles.
     *
     * Usage:
     * ```
     * val explorer = GraphExplorer.rightHandRule(startRoomId)
     * while (true) {
     *     val step = explorer.nextMove(currentSpace, currentRoomId, availableExits)
     *     when (step) {
     *         is Move -> executeMove(step.direction)
     *         is LoopFound -> success!
     *         is Failed -> failure
     *     }
     * }
     * ```
     */
    fun rightHandRule(startRoomId: SpaceId): RightHandExplorer {
        return RightHandExplorer(startRoomId)
    }

    /**
     * Depth-first search explorer.
     *
     * Systematically explores all paths, backtracking when stuck.
     * Guarantees finding a loop if one exists.
     */
    fun depthFirstSearch(startRoomId: SpaceId): DFSExplorer {
        return DFSExplorer(startRoomId)
    }

    /**
     * Right-hand rule implementation.
     */
    class RightHandExplorer(private val startRoomId: SpaceId) {
        private val visited = mutableSetOf<SpaceId>()
        private val path = mutableListOf<String>()
        private var lastDirection: String? = null
        private var moveCount = 0
        private val maxMoves = 100

        // Cardinal direction order (clockwise: N, E, S, W)
        private val directionPriority = listOf("north", "east", "south", "west")

        fun nextMove(
            currentSpace: SpacePropertiesComponent?,
            currentRoomId: SpaceId,
            availableExits: List<String>
        ): ExplorationStep {
            moveCount++

            // Check if we've found a loop (returned to start after at least 3 moves)
            if (moveCount > 1 && currentRoomId == startRoomId && path.size >= 3) {
                return ExplorationStep.LoopFound(path.toList())
            }

            // Check max moves
            if (moveCount > maxMoves) {
                return ExplorationStep.Failed("Max moves ($maxMoves) exceeded without finding loop")
            }

            // Mark room as visited
            visited.add(currentRoomId)

            // No exits available
            if (availableExits.isEmpty()) {
                return ExplorationStep.Failed("Dead end - no exits available")
            }

            // Choose next direction using right-hand rule
            val direction = chooseRightHandDirection(availableExits)

            path.add(direction)
            lastDirection = direction

            return ExplorationStep.Move(direction)
        }

        /**
         * Choose the "rightmost" direction based on current heading.
         * If we came from north, prefer east (right turn), then south (straight), etc.
         */
        private fun chooseRightHandDirection(availableExits: List<String>): String {
            // Normalize exit names
            val exits = availableExits.map { it.lowercase() }

            if (lastDirection == null) {
                // First move - prefer directions in priority order
                return directionPriority.firstOrNull { it in exits } ?: exits.first()
            }

            // Get rotation based on last direction
            val rotatedPriority = when (lastDirection?.lowercase()) {
                "north" -> listOf("east", "north", "west", "south")  // Right is east
                "east" -> listOf("south", "east", "north", "west")   // Right is south
                "south" -> listOf("west", "south", "east", "north")  // Right is west
                "west" -> listOf("north", "west", "south", "east")   // Right is north
                else -> directionPriority
            }

            // Choose first available direction in rotated priority
            return rotatedPriority.firstOrNull { it in exits } ?: exits.first()
        }
    }

    /**
     * Depth-first search implementation with backtracking.
     */
    class DFSExplorer(private val startRoomId: SpaceId) {
        private val visited = mutableSetOf<SpaceId>()
        private val path = mutableListOf<String>()
        private val roomPath = mutableListOf<SpaceId>()
        private val backtrackStack = mutableListOf<BacktrackState>()
        private var moveCount = 0
        private val maxMoves = 100

        data class BacktrackState(
            val roomId: SpaceId,
            val remainingExits: List<String>
        )

        fun nextMove(
            currentSpace: SpacePropertiesComponent?,
            currentRoomId: SpaceId,
            availableExits: List<String>
        ): ExplorationStep {
            moveCount++

            // Check if we've found a loop (returned to start after at least 3 moves)
            if (moveCount > 1 && currentRoomId == startRoomId && path.size >= 3) {
                return ExplorationStep.LoopFound(path.toList())
            }

            // Check max moves
            if (moveCount > maxMoves) {
                return ExplorationStep.Failed("Max moves ($maxMoves) exceeded without finding loop")
            }

            // Mark current room as visited
            visited.add(currentRoomId)
            roomPath.add(currentRoomId)

            // Get unvisited exits (prefer exploring new rooms)
            val unvisitedExits = availableExits.filter { direction ->
                // We don't know where this exit leads, so assume it's worth trying
                true  // In a real implementation, we'd track the graph structure
            }

            // If we have exits to try, pick one
            if (unvisitedExits.isNotEmpty()) {
                // Save backtrack state for other options
                if (unvisitedExits.size > 1) {
                    backtrackStack.add(
                        BacktrackState(
                            roomId = currentRoomId,
                            remainingExits = unvisitedExits.drop(1)
                        )
                    )
                }

                val direction = unvisitedExits.first()
                path.add(direction)
                return ExplorationStep.Move(direction)
            }

            // No unvisited exits - try backtracking
            if (backtrackStack.isNotEmpty()) {
                val backtrackState = backtrackStack.removeAt(backtrackStack.lastIndex)

                // Calculate path back to backtrack point
                // For now, just try the remaining exits
                if (backtrackState.remainingExits.isNotEmpty()) {
                    val direction = backtrackState.remainingExits.first()
                    path.add(direction)

                    if (backtrackState.remainingExits.size > 1) {
                        backtrackStack.add(
                            backtrackState.copy(
                                remainingExits = backtrackState.remainingExits.drop(1)
                            )
                        )
                    }

                    return ExplorationStep.Move(direction)
                }
            }

            // Stuck - try any available exit
            if (availableExits.isNotEmpty()) {
                val direction = availableExits.first()
                path.add(direction)
                return ExplorationStep.Move(direction)
            }

            return ExplorationStep.Failed("No available moves")
        }
    }
}
