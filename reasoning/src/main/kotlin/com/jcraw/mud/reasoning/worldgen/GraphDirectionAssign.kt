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
 * Active direction-pair assignment + validation for [GraphEdgeDirections].
 * Pure extract from GraphGenerator (MUD-034b) — no algorithm change.
 */
internal object GraphDirectionAssign {

    private val OPPOSITES get() = GraphDirectionGeometry.OPPOSITES

    fun assignDirectionPair(
        fromId: String,
        toId: String,
        edgeMap: MutableMap<String, MutableList<EdgeData>>,
        nodeMap: Map<String, GraphNodeComponent>
    ) {
        val fromNode = nodeMap[fromId] ?: return
        val toNode = nodeMap[toId] ?: return
        val fromEdges = edgeMap[fromId] ?: return
        val toEdges = edgeMap[toId] ?: return
        val forwardEdgeIdx = fromEdges.indexOfFirst { it.targetId == toId }
        val reverseEdgeIdx = toEdges.indexOfFirst { it.targetId == fromId }
        if (forwardEdgeIdx < 0 || reverseEdgeIdx < 0) return
        writeResolvedPair(
            fromNode, toNode, fromEdges, toEdges, forwardEdgeIdx, reverseEdgeIdx
        )
    }

    private fun writeResolvedPair(
        fromNode: GraphNodeComponent,
        toNode: GraphNodeComponent,
        fromEdges: MutableList<EdgeData>,
        toEdges: MutableList<EdgeData>,
        forwardEdgeIdx: Int,
        reverseEdgeIdx: Int
    ) {
        val fromUsed = usedEdgeDirectionsExcept(fromEdges, forwardEdgeIdx)
        val toUsed = usedEdgeDirectionsExcept(toEdges, reverseEdgeIdx)
        val (newForward, newReverse) = resolvePairDirections(
            fromNode, toNode, fromUsed, toUsed
        )
        fromEdges[forwardEdgeIdx] = fromEdges[forwardEdgeIdx].copy(direction = newForward)
        toEdges[reverseEdgeIdx] = toEdges[reverseEdgeIdx].copy(direction = newReverse)
    }

    private fun usedEdgeDirectionsExcept(
        edges: List<EdgeData>,
        excludeIdx: Int
    ): Set<String> {
        return edges.filterIndexed { idx, _ -> idx != excludeIdx }
            .map { it.direction.lowercase() }
            .toSet()
    }

    private fun resolvePairDirections(
        fromNode: GraphNodeComponent,
        toNode: GraphNodeComponent,
        fromUsed: Set<String>,
        toUsed: Set<String>
    ): Pair<String, String> {
        val geometric = tryGeometricPairDirs(fromNode, toNode, fromUsed, toUsed)
        if (geometric != null) return geometric
        val anyPair = tryAnyOpposite(fromUsed, toUsed)
        if (anyPair != null) return anyPair
        return "passage-${fromUsed.size + 1}" to "passage-back-${toUsed.size + 1}"
    }

    private fun tryGeometricPairDirs(
        fromNode: GraphNodeComponent,
        toNode: GraphNodeComponent,
        fromUsed: Set<String>,
        toUsed: Set<String>
    ): Pair<String, String>? {
        val (forwardAngle, _) = GraphDirectionGeometry.calculateAngleAndDistance(fromNode, toNode)
        if (forwardAngle == null) return null
        val geometricDir = GraphDirectionGeometry.getBestDirectionForAngle(forwardAngle)
        val geometricReverse = OPPOSITES[geometricDir]
        return if (geometricDir !in fromUsed &&
            geometricReverse != null &&
            geometricReverse !in toUsed
        ) {
            geometricDir to geometricReverse
        } else {
            null
        }
    }

    fun tryAnyOpposite(
        nodeUsed: Set<String>,
        targetUsed: Set<String>
    ): Pair<String, String>? {
        for ((fwd, rev) in OPPOSITES) {
            if (fwd !in nodeUsed && rev !in targetUsed) {
                return fwd to rev
            }
        }
        return null
    }

    fun validateBidirectionalOpposites(nodes: List<GraphNodeComponent>) {
        val nodeMap = nodes.associateBy { it.id }
        val errors = mutableListOf<String>()
        for (node in nodes) {
            for (edge in node.neighbors) {
                collectOppositeError(node, edge, nodeMap, errors)
            }
        }
        reportValidationErrors(errors)
    }

    private fun collectOppositeError(
        node: GraphNodeComponent,
        edge: EdgeData,
        nodeMap: Map<String, GraphNodeComponent>,
        errors: MutableList<String>
    ) {
        val targetNode = nodeMap[edge.targetId] ?: return
        val reverseEdge = targetNode.neighbors.find { it.targetId == node.id }
        if (reverseEdge == null) {
            errors.add(missingReverseMsg(node, edge))
            return
        }
        val expectedReverse = OPPOSITES[edge.direction.lowercase()]
        if (expectedReverse != null && expectedReverse != reverseEdge.direction.lowercase()) {
            errors.add(mismatchMsg(node, edge, targetNode, reverseEdge, expectedReverse))
        }
    }

    private fun missingReverseMsg(node: GraphNodeComponent, edge: EdgeData): String {
        return "Missing reverse edge: ${node.id} -> ${edge.targetId} " +
            "(${edge.direction}) has no return path"
    }

    private fun mismatchMsg(
        node: GraphNodeComponent,
        edge: EdgeData,
        targetNode: GraphNodeComponent,
        reverseEdge: EdgeData,
        expectedReverse: String
    ): String {
        return "Direction mismatch: ${node.id} -> ${edge.targetId} (${edge.direction}) " +
            "but reverse is ${targetNode.id} -> ${node.id} (${reverseEdge.direction}), " +
            "expected ${expectedReverse}"
    }

    private fun reportValidationErrors(errors: List<String>) {
        if (errors.isEmpty()) return
        println("WARNING: Bidirectional validation found ${errors.size} issues:")
        errors.take(10).forEach { println("  - $it") }
        if (errors.size > 10) {
            println("  ... and ${errors.size - 10} more")
        }
    }
}
