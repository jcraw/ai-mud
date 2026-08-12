@file:Suppress(
    "TooManyFunctions",
    "MagicNumber",
    "ReturnCount",
    "UnusedParameter",
    "LoopWithTooManyJumpStatements",
    "LongParameterList",
)

package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.EdgeData

/**
 * Legacy unique-direction helpers pure-move (MUD-034b).
 * Generate path uses [GraphDirectionAssign.assignDirectionPair].
 */
internal object GraphDirectionUnique {

    private val OPPOSITES get() = GraphDirectionGeometry.OPPOSITES

    fun assignUniqueDirectionsBidirectional(
        node: GraphNodeComponent,
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>
    ): List<EdgeData> {
        val neighbors = edgeMap[node.id] ?: return emptyList()
        if (neighbors.size <= 1) return neighbors
        val contexts = GraphDirectionGeometry.sortedNeighborContexts(node, neighbors, nodeMap)
        val used = mutableSetOf<String>()
        val result = mutableListOf<EdgeData>()
        for (context in contexts) {
            result.add(assignOneUniqueBidi(node, context, edgeMap, nodeMap, used))
        }
        return result
    }

    private fun assignOneUniqueBidi(
        node: GraphNodeComponent,
        context: GraphDirectionGeometry.NeighborContext,
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>,
        used: MutableSet<String>
    ): EdgeData {
        val reverseUsed = reverseUsedDirections(edgeMap, context.edge.targetId, node.id)
        val (newDirection, reverseDirection) = pickUniqueBidiPair(
            node, context, nodeMap, used, reverseUsed
        )
        used.add(newDirection.lowercase())
        updateReverseEdgeDirection(edgeMap, context.edge.targetId, node.id, reverseDirection)
        return context.edge.copy(
            direction = newDirection,
            geometricAngle = context.edge.geometricAngle,
            fromPosition = context.edge.fromPosition,
            toPosition = context.edge.toPosition
        )
    }

    private fun reverseUsedDirections(
        edgeMap: Map<String, MutableList<EdgeData>>,
        targetId: String,
        excludeNodeId: String
    ): Set<String> {
        val reverseEdges = edgeMap[targetId] ?: emptyList()
        return reverseEdges
            .filter { it.targetId != excludeNodeId }
            .map { it.direction.lowercase() }
            .toSet()
    }

    private fun pickUniqueBidiPair(
        node: GraphNodeComponent,
        context: GraphDirectionGeometry.NeighborContext,
        nodeMap: Map<String, GraphNodeComponent>,
        used: Set<String>,
        reverseUsed: Set<String>
    ): Pair<String, String> {
        for (candidateDir in generateSequence(0) { it + 1 }.take(20)) {
            val candidate = candidateAtAttempt(candidateDir, context, used)
            val candidateReverse = reverseForCandidate(candidate, node, context, nodeMap)
            if (candidate.lowercase() !in used &&
                candidateReverse.lowercase() !in reverseUsed
            ) {
                return candidate to candidateReverse
            }
        }
        val newDirection = GraphDirectionGeometry.pickFallbackDirection(used)
        return newDirection to (OPPOSITES[newDirection] ?: "back")
    }

    private fun candidateAtAttempt(
        attempt: Int,
        context: GraphDirectionGeometry.NeighborContext,
        used: Set<String>
    ): String {
        return if (attempt == 0) {
            context.angle?.let { GraphDirectionGeometry.pickDirectionForAngle(it, used) }
                ?: GraphDirectionGeometry.pickFallbackDirection(used)
        } else {
            GraphDirectionGeometry.pickFallbackDirection(used)
        }
    }

    private fun reverseForCandidate(
        candidate: String,
        node: GraphNodeComponent,
        context: GraphDirectionGeometry.NeighborContext,
        nodeMap: Map<String, GraphNodeComponent>
    ): String {
        OPPOSITES[candidate]?.let { return it }
        val toNode = nodeMap[context.edge.targetId] ?: return "back"
        val (reverseAngle, _) = GraphDirectionGeometry.calculateAngleAndDistance(toNode, node)
        return reverseAngle?.let { GraphDirectionGeometry.baseDirectionForAngle(it) } ?: "back"
    }

    private fun updateReverseEdgeDirection(
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        targetId: String,
        nodeId: String,
        reverseDirection: String
    ) {
        val reverseEdgeList = edgeMap[targetId] ?: return
        val reverseIndex = reverseEdgeList.indexOfFirst { it.targetId == nodeId }
        if (reverseIndex < 0) return
        val oldEdge = reverseEdgeList[reverseIndex]
        reverseEdgeList[reverseIndex] = EdgeData(
            targetId = nodeId,
            direction = reverseDirection,
            hidden = oldEdge.hidden,
            conditions = oldEdge.conditions,
            geometricAngle = oldEdge.geometricAngle,
            fromPosition = oldEdge.fromPosition,
            toPosition = oldEdge.toPosition
        )
    }

    fun assignUniqueDirections(
        node: GraphNodeComponent,
        neighbors: List<EdgeData>,
        nodeMap: Map<String, GraphNodeComponent>
    ): List<EdgeData> {
        if (neighbors.size <= 1) return neighbors
        val contexts = GraphDirectionGeometry.sortedNeighborContexts(node, neighbors, nodeMap)
        val used = mutableSetOf<String>()
        return contexts.map { context ->
            val label = uniqueDirectionLabel(context, used)
            used += label
            context.edge.copy(
                direction = label,
                geometricAngle = context.edge.geometricAngle,
                fromPosition = context.edge.fromPosition,
                toPosition = context.edge.toPosition
            )
        }
    }

    private fun uniqueDirectionLabel(
        context: GraphDirectionGeometry.NeighborContext,
        used: Set<String>
    ): String {
        return if (context.angle != null) {
            val geometricDirection =
                GraphDirectionGeometry.getBestDirectionForAngle(context.angle)
            if (geometricDirection !in used) {
                geometricDirection
            } else {
                "passage-${used.size + 1}"
            }
        } else {
            GraphDirectionGeometry.pickFallbackDirection(used)
        }
    }
}
