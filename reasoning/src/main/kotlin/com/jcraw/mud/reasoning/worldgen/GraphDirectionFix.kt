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
 * Legacy fixBidirectionalDirections pure-move (MUD-034b).
 * Generate path uses [GraphDirectionAssign.assignDirectionPair].
 */
internal object GraphDirectionFix {

    private val OPPOSITES get() = GraphDirectionGeometry.OPPOSITES

    fun fixBidirectionalDirections(nodes: List<GraphNodeComponent>): List<GraphNodeComponent> {
        val nodeMap = nodes.associateBy { it.id }.toMutableMap()
        val processedPairs = mutableSetOf<Pair<String, String>>()
        for (originalNode in nodes) {
            val node = nodeMap[originalNode.id] ?: continue
            for (edge in node.neighbors) {
                processFixPair(node, edge, nodeMap, processedPairs)
            }
        }
        return nodeMap.values.toList()
    }

    private fun processFixPair(
        node: GraphNodeComponent,
        edge: EdgeData,
        nodeMap: MutableMap<String, GraphNodeComponent>,
        processedPairs: MutableSet<Pair<String, String>>
    ) {
        val pairKey = node.id to edge.targetId
        if (pairKey in processedPairs) return
        processedPairs.add(pairKey)
        processedPairs.add(edge.targetId to node.id)
        val targetNode = nodeMap[edge.targetId] ?: return
        val reverseEdge = targetNode.neighbors.find { it.targetId == node.id } ?: return
        val expectedReverse = OPPOSITES[edge.direction.lowercase()]
        if (expectedReverse == reverseEdge.direction.lowercase()) {
            return // Already correct
        }
        applyFixedDirections(node, edge, targetNode, nodeMap)
    }

    private fun applyFixedDirections(
        node: GraphNodeComponent,
        edge: EdgeData,
        targetNode: GraphNodeComponent,
        nodeMap: MutableMap<String, GraphNodeComponent>
    ) {
        val nodeUsed = usedDirectionsExcept(nodeMap, node.id, edge.targetId)
        val targetUsed = usedDirectionsExcept(nodeMap, targetNode.id, node.id)
        val (newForward, newReverse) = chooseOppositePair(
            node, targetNode, nodeUsed, targetUsed
        )
        updateNodeDirection(nodeMap, node.id, edge.targetId, newForward)
        updateNodeDirection(nodeMap, targetNode.id, node.id, newReverse)
    }

    private fun usedDirectionsExcept(
        nodeMap: Map<String, GraphNodeComponent>,
        nodeId: String,
        excludeTarget: String
    ): Set<String> {
        return nodeMap[nodeId]!!.neighbors
            .filter { it.targetId != excludeTarget }
            .map { it.direction.lowercase() }
            .toSet()
    }

    private fun chooseOppositePair(
        node: GraphNodeComponent,
        targetNode: GraphNodeComponent,
        nodeUsed: Set<String>,
        targetUsed: Set<String>
    ): Pair<String, String> {
        val geometric = tryGeometricOpposite(node, targetNode, nodeUsed, targetUsed)
        if (geometric != null) return geometric
        val anyPair = GraphDirectionAssign.tryAnyOpposite(nodeUsed, targetUsed)
        if (anyPair != null) return anyPair
        return "passage-${nodeUsed.size + 1}" to "passage-back-${targetUsed.size + 1}"
    }

    private fun tryGeometricOpposite(
        node: GraphNodeComponent,
        targetNode: GraphNodeComponent,
        nodeUsed: Set<String>,
        targetUsed: Set<String>
    ): Pair<String, String>? {
        val (forwardAngle, _) = GraphDirectionGeometry.calculateAngleAndDistance(node, targetNode)
        if (forwardAngle == null) return null
        val geometricForward = GraphDirectionGeometry.getBestDirectionForAngle(forwardAngle)
        val expectedReverse = OPPOSITES[geometricForward]
        return if (geometricForward !in nodeUsed &&
            expectedReverse != null &&
            expectedReverse !in targetUsed
        ) {
            geometricForward to expectedReverse
        } else {
            null
        }
    }

    private fun updateNodeDirection(
        nodeMap: MutableMap<String, GraphNodeComponent>,
        nodeId: String,
        targetId: String,
        newDirection: String
    ) {
        val current = nodeMap[nodeId]!!
        nodeMap[nodeId] = current.copy(
            neighbors = current.neighbors.map { edge ->
                if (edge.targetId == targetId) {
                    edge.copy(
                        direction = newDirection,
                        geometricAngle = edge.geometricAngle,
                        fromPosition = edge.fromPosition,
                        toPosition = edge.toPosition
                    )
                } else {
                    edge
                }
            }
        )
    }
}
