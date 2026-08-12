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
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.NodeType
import kotlin.random.Random

/**
 * Node type assignment + hidden-edge marking for [GraphGenerator].
 * Pure extract — no algorithm change. Preserves RNG call order.
 */
internal object GraphNodeTyping {

    fun assignNodeTypes(
        nodes: List<GraphNodeComponent>,
        chunkId: String,
        rng: Random
    ): List<GraphNodeComponent> {
        if (nodes.isEmpty()) return nodes
        val mutableNodes = nodes.toMutableList()
        // 1. First node is always a Hub (entry point)
        mutableNodes[0] = mutableNodes[0].copy(type = NodeType.Hub)
        assignBossFromEntry(mutableNodes)
        assignFrontiers(mutableNodes, rng)
        assignDeadEnds(mutableNodes, rng)
        assignLinearOrBranching(mutableNodes)
        return mutableNodes
    }

    private fun assignBossFromEntry(mutableNodes: MutableList<GraphNodeComponent>) {
        val entryId = mutableNodes[0].id
        val adjacency = buildAdjacencyFromNodes(mutableNodes)
        val distances = bfsDistances(entryId, adjacency)
        val farthestId = distances.maxByOrNull { it.value }?.key
        if (farthestId != null && farthestId != entryId) {
            val farthestIndex = mutableNodes.indexOfFirst { it.id == farthestId }
            if (farthestIndex >= 0) {
                mutableNodes[farthestIndex] = mutableNodes[farthestIndex].copy(type = NodeType.Boss)
            }
        }
    }

    private fun assignFrontiers(mutableNodes: MutableList<GraphNodeComponent>, rng: Random) {
        val minFrontiers = 2
        val targetFrontiers = minFrontiers.coerceAtLeast(mutableNodes.size / 10)
        var frontiersAssigned = 0
        frontiersAssigned = assignBoundaryFrontiers(
            mutableNodes, rng, targetFrontiers, frontiersAssigned
        )
        if (frontiersAssigned < minFrontiers) {
            assignRemainingFrontiers(mutableNodes, rng, minFrontiers, frontiersAssigned)
        }
    }

    private fun assignBoundaryFrontiers(
        mutableNodes: MutableList<GraphNodeComponent>,
        rng: Random,
        targetFrontiers: Int,
        startCount: Int
    ): Int {
        var frontiersAssigned = startCount
        val boundaryCandidates = findBoundaryNodes(mutableNodes).shuffled(rng)
        for (boundaryId in boundaryCandidates) {
            if (frontiersAssigned >= targetFrontiers) break
            val index = mutableNodes.indexOfFirst { it.id == boundaryId }
            if (index >= 0 &&
                mutableNodes[index].type != NodeType.Hub &&
                mutableNodes[index].type != NodeType.Boss
            ) {
                mutableNodes[index] = mutableNodes[index].copy(type = NodeType.Frontier)
                frontiersAssigned++
            }
        }
        return frontiersAssigned
    }

    private fun assignRemainingFrontiers(
        mutableNodes: MutableList<GraphNodeComponent>,
        rng: Random,
        minFrontiers: Int,
        startCount: Int
    ) {
        var frontiersAssigned = startCount
        val remainingCandidates = mutableNodes.filter {
            it.type == NodeType.Linear // Still unassigned
        }.shuffled(rng)
        for (node in remainingCandidates) {
            if (frontiersAssigned >= minFrontiers) break
            val index = mutableNodes.indexOfFirst { it.id == node.id }
            if (index >= 0) {
                mutableNodes[index] = mutableNodes[index].copy(type = NodeType.Frontier)
                frontiersAssigned++
            }
        }
    }

    private fun assignDeadEnds(mutableNodes: MutableList<GraphNodeComponent>, rng: Random) {
        val deadEndCandidates = mutableNodes.filter {
            it.degree() == 1 && it.type !in listOf(NodeType.Hub, NodeType.Boss, NodeType.Frontier)
        }.shuffled(rng)
        val deadEndCount = (mutableNodes.size * 0.2).toInt()
            .coerceAtLeast(1)
            .coerceAtMost(deadEndCandidates.size)
        for (node in deadEndCandidates.take(deadEndCount)) {
            val index = mutableNodes.indexOfFirst { it.id == node.id }
            if (index >= 0) {
                mutableNodes[index] = mutableNodes[index].copy(type = NodeType.DeadEnd)
            }
        }
    }

    private fun assignLinearOrBranching(mutableNodes: MutableList<GraphNodeComponent>) {
        for (i in mutableNodes.indices) {
            if (mutableNodes[i].type != NodeType.Linear) continue // Already assigned
            val degree = mutableNodes[i].degree()
            mutableNodes[i] = when {
                degree == 2 -> mutableNodes[i].copy(type = NodeType.Linear)
                degree >= 3 -> mutableNodes[i].copy(type = NodeType.Branching)
                else -> mutableNodes[i] // Keep Linear for degree 1 (edge case)
            }
        }
    }

    fun buildAdjacencyFromNodes(nodes: List<GraphNodeComponent>): Map<String, List<String>> {
        return nodes.associate { node ->
            node.id to node.neighbors.map { it.targetId }
        }
    }

    fun bfsDistances(startId: String, adjacency: Map<String, List<String>>): Map<String, Int> {
        val distances = mutableMapOf<String, Int>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(startId to 0)
        distances[startId] = 0
        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()
            for (neighbor in adjacency[current] ?: emptyList()) {
                if (neighbor !in distances) {
                    distances[neighbor] = distance + 1
                    queue.add(neighbor to distance + 1)
                }
            }
        }
        return distances
    }

    fun findBoundaryNodes(nodes: List<GraphNodeComponent>): List<String> {
        return nodes.filter { it.degree() <= 2 }.map { it.id }
    }

    fun markHiddenEdges(
        nodes: List<GraphNodeComponent>,
        rng: Random,
        difficultyLevel: Int
    ): List<GraphNodeComponent> {
        val hiddenPercentage = 0.15 + rng.nextDouble() * 0.10 // 15-25%
        val totalEdgeRefs = nodes.sumOf { it.neighbors.size }
        val targetHiddenCount = (totalEdgeRefs * hiddenPercentage).toInt().coerceAtLeast(1)
        val edgeRefs = collectEdgeRefs(nodes)
        val hiddenRefs = edgeRefs.shuffled(rng).take(targetHiddenCount).toSet()
        return applyHiddenFlags(nodes, hiddenRefs, rng, difficultyLevel)
    }

    private fun collectEdgeRefs(nodes: List<GraphNodeComponent>): List<Pair<Int, Int>> {
        return nodes.flatMapIndexed { nodeIndex, node ->
            node.neighbors.indices.map { edgeIndex -> nodeIndex to edgeIndex }
        }
    }

    private fun applyHiddenFlags(
        nodes: List<GraphNodeComponent>,
        hiddenRefs: Set<Pair<Int, Int>>,
        rng: Random,
        difficultyLevel: Int
    ): List<GraphNodeComponent> {
        return nodes.mapIndexed { nodeIndex, node ->
            val updatedNeighbors = node.neighbors.mapIndexed { edgeIndex, edge ->
                if ((nodeIndex to edgeIndex) in hiddenRefs) {
                    hideEdge(edge, rng, difficultyLevel)
                } else {
                    edge
                }
            }
            node.copy(neighbors = updatedNeighbors)
        }
    }

    private fun hideEdge(
        edge: com.jcraw.mud.core.world.EdgeData,
        rng: Random,
        difficultyLevel: Int
    ): com.jcraw.mud.core.world.EdgeData {
        val difficulty = 10 + difficultyLevel * 5 + rng.nextInt(10) // 10-30 range
        return edge.copy(
            hidden = true,
            conditions = edge.conditions + Condition.SkillCheck("Perception", difficulty)
        )
    }
}
