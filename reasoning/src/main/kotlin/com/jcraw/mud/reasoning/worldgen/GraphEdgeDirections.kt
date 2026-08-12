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
 * Edge building entry for [GraphGenerator].
 * Pure extract — no algorithm change. Direction geometry/assign in siblings.
 */
internal object GraphEdgeDirections {

    fun buildNodeEdges(
        nodes: List<GraphNodeComponent>,
        edges: List<Pair<String, String>>
    ): List<GraphNodeComponent> {
        val nodeMap = nodes.associateBy { it.id }
        val edgeMap = nodes.associate { it.id to mutableListOf<EdgeData>() }.toMutableMap()
        populateBidirectionalEdges(edges, nodeMap, edgeMap)
        ensureBidirectionalConsistency(edgeMap, nodeMap)
        assignAllDirectionPairs(nodes, edgeMap, nodeMap)
        val nodesWithUniqueDirections = nodes.map { node ->
            node.copy(neighbors = edgeMap[node.id] ?: emptyList())
        }
        GraphDirectionAssign.validateBidirectionalOpposites(nodesWithUniqueDirections)
        return nodesWithUniqueDirections
    }

    private fun populateBidirectionalEdges(
        edges: List<Pair<String, String>>,
        nodeMap: Map<String, GraphNodeComponent>,
        edgeMap: MutableMap<String, MutableList<EdgeData>>
    ) {
        for ((from, to) in edges) {
            val fromNode = nodeMap[from] ?: continue
            val toNode = nodeMap[to] ?: continue
            addEdgePair(fromNode, toNode, edgeMap)
        }
    }

    private fun addEdgePair(
        fromNode: GraphNodeComponent,
        toNode: GraphNodeComponent,
        edgeMap: MutableMap<String, MutableList<EdgeData>>
    ) {
        val forward = buildDirectedEdge(fromNode, toNode)
        val reverse = buildDirectedEdge(toNode, fromNode)
        edgeMap.getValue(fromNode.id).add(forward)
        edgeMap.getValue(toNode.id).add(reverse)
    }

    private fun buildDirectedEdge(
        fromNode: GraphNodeComponent,
        toNode: GraphNodeComponent
    ): EdgeData {
        val direction = GraphDirectionGeometry.calculateDirection(fromNode, toNode)
        val angleDegrees = GraphDirectionGeometry.calculateAngleAndDistance(fromNode, toNode).first
        val angleRadians = GraphDirectionGeometry.degreesToGameRadians(angleDegrees)
        return GraphDirectionGeometry.makeEdgeData(
            targetId = toNode.id,
            direction = direction,
            geometricAngle = angleRadians,
            fromPosition = fromNode.position,
            toPosition = toNode.position
        )
    }

    private fun assignAllDirectionPairs(
        nodes: List<GraphNodeComponent>,
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>
    ) {
        val processedPairs = mutableSetOf<Pair<String, String>>()
        for (node in nodes) {
            val nodeId = node.id
            val edges = edgeMap[nodeId] ?: continue
            for (edge in edges) {
                val pairKey = orderedPair(nodeId, edge.targetId)
                if (pairKey in processedPairs) continue
                processedPairs.add(pairKey)
                GraphDirectionAssign.assignDirectionPair(
                    nodeId, edge.targetId, edgeMap, nodeMap
                )
            }
        }
    }

    private fun orderedPair(a: String, b: String): Pair<String, String> {
        return if (a < b) a to b else b to a
    }

    private fun ensureBidirectionalConsistency(
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>
    ) {
        val snapshot = edgeMap.mapValues { it.value.toList() }
        snapshot.forEach { (fromId, edges) ->
            edges.forEach { edge ->
                ensureReverseExists(fromId, edge, edgeMap, nodeMap)
            }
        }
    }

    private fun ensureReverseExists(
        fromId: String,
        edge: EdgeData,
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>
    ) {
        val reverseList = edgeMap.getOrPut(edge.targetId) { mutableListOf() }
        if (reverseList.any { it.targetId == fromId }) return
        val reverseDirection = reverseDirectionLabel(fromId, edge.targetId, nodeMap)
        reverseList.add(
            edge.copy(
                targetId = fromId,
                direction = reverseDirection,
                geometricAngle = edge.geometricAngle,
                fromPosition = edge.fromPosition,
                toPosition = edge.toPosition
            )
        )
    }

    private fun reverseDirectionLabel(
        fromId: String,
        toId: String,
        nodeMap: Map<String, GraphNodeComponent>
    ): String {
        val fromNode = nodeMap[fromId]
        val toNode = nodeMap[toId]
        return if (fromNode != null && toNode != null) {
            GraphDirectionGeometry.calculateDirection(toNode, fromNode)
        } else {
            "back"
        }
    }
}
