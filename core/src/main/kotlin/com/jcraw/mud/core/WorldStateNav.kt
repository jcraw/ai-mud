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
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter",
    "UseCheckOrError"
)

package com.jcraw.mud.core

import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.EdgeData

/**
 * Graph navigation for [WorldState] members (MUD-034m pure-move).
 */
internal object WorldStateNav {

    fun move(
        world: WorldState,
        playerId: PlayerId,
        direction: Direction,
        playerSkills: SkillComponent
    ): WorldState? {
        val playerState = world.players[playerId] ?: return null
        val currentNode = world.graphNodes[playerState.currentRoomId] ?: return null

        // Find edge matching direction (position-aware for spatial coherence)
        val edge = currentNode.getEdgeGeometric(direction.displayName) ?: return null
        return traverse(world, playerState, currentNode, edge, playerSkills)
    }

    fun moveByExit(
        world: WorldState,
        playerId: PlayerId,
        exitLabel: String,
        playerSkills: SkillComponent
    ): WorldState? {
        val playerState = world.players[playerId] ?: return null
        val currentNode = world.graphNodes[playerState.currentRoomId] ?: return null
        val edge = currentNode.getEdge(exitLabel) ?: return null
        return traverse(world, playerState, currentNode, edge, playerSkills)
    }

    fun availableExits(
        world: WorldState,
        playerId: PlayerId,
        playerSkills: SkillComponent
    ): List<Direction> {
        val playerState = world.players[playerId] ?: return emptyList()
        val node = world.graphNodes[playerState.currentRoomId] ?: return emptyList()
        val space = world.spaces[playerState.currentRoomId] ?: return emptyList()

        // Get visible exits from space (handles Perception checks for hidden exits)
        val visibleExits = space.getVisibleExits(playerState, playerSkills)

        // Convert exit directions to Direction enum (filter out non-cardinal)
        return visibleExits.mapNotNull { exit ->
            Direction.fromString(exit.direction)
        }
    }

    private fun traverse(
        world: WorldState,
        playerState: PlayerState,
        currentNode: GraphNodeComponent,
        edge: EdgeData,
        playerSkills: SkillComponent
    ): WorldState? {
        val access = evaluateEdgeAccess(playerState, playerSkills, currentNode, edge)
        if (!access.canTraverse) return null
        if (!world.spaces.containsKey(edge.targetId)) {
            return null // Space not yet generated
        }
        return applyTraverse(world, playerState, edge, access)
    }

    private fun applyTraverse(
        world: WorldState,
        playerState: PlayerState,
        edge: EdgeData,
        access: EdgeAccess
    ): WorldState {
        var updatedPlayer = playerState
        if (access.shouldRevealEdge) {
            updatedPlayer = updatedPlayer.revealExit(access.edgeId)
        }

        // Auto-reveal the reverse edge (the path back)
        // If you just came from that direction, you know how to get back
        val reverseEdgeId = "${edge.targetId}->${playerState.currentRoomId}"
        updatedPlayer = updatedPlayer.revealExit(reverseEdgeId)

        // Move player to target
        return world.updatePlayer(updatedPlayer.moveToRoom(edge.targetId))
    }

    private data class EdgeAccess(
        val canTraverse: Boolean,
        val shouldRevealEdge: Boolean,
        val edgeId: String
    )

    private fun evaluateEdgeAccess(
        player: PlayerState,
        playerSkills: SkillComponent,
        node: GraphNodeComponent,
        edge: EdgeData
    ): EdgeAccess {
        val edgeId = edge.edgeId(node.id)
        if (!gatingMet(player, playerSkills, edge)) {
            return EdgeAccess(canTraverse = false, shouldRevealEdge = false, edgeId = edgeId)
        }
        if (!edge.hidden || player.hasRevealedExit(edgeId)) {
            return EdgeAccess(canTraverse = true, shouldRevealEdge = false, edgeId = edgeId)
        }
        return perceptionAccess(player, playerSkills, edge, edgeId)
    }

    private fun gatingMet(
        player: PlayerState,
        playerSkills: SkillComponent,
        edge: EdgeData
    ): Boolean {
        val gatingConditions = edge.conditions.filterNot { condition ->
            condition is Condition.SkillCheck && condition.skill.equals("Perception", ignoreCase = true)
        }
        return gatingConditions.all { it.meetsCondition(player, playerSkills) }
    }

    private fun perceptionAccess(
        player: PlayerState,
        playerSkills: SkillComponent,
        edge: EdgeData,
        edgeId: String
    ): EdgeAccess {
        val perceptionMet = edge.conditions.any { condition ->
            condition is Condition.SkillCheck &&
                condition.skill.equals("Perception", ignoreCase = true) &&
                condition.meetsCondition(player, playerSkills)
        }
        return if (perceptionMet) {
            EdgeAccess(canTraverse = true, shouldRevealEdge = true, edgeId = edgeId)
        } else {
            EdgeAccess(canTraverse = false, shouldRevealEdge = false, edgeId = edgeId)
        }
    }
}
