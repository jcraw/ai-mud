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
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Kruskal MST + loop edge placement for [GraphGenerator].
 * Pure extract — no algorithm change. Preserves RNG call order.
 */
internal object GraphMst {

    fun kruskalMST(nodes: List<GraphNodeComponent>): List<Pair<String, String>> {
        if (nodes.size == 1) return emptyList()
        val allEdges = buildWeightedEdges(nodes)
        allEdges.sortBy { it.weight }
        return unionFindMst(nodes, allEdges)
    }

    private fun buildWeightedEdges(nodes: List<GraphNodeComponent>): MutableList<Edge> {
        val allEdges = mutableListOf<Edge>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val weight = calculateDistance(nodes[i], nodes[j])
                allEdges.add(Edge(nodes[i].id, nodes[j].id, weight))
            }
        }
        return allEdges
    }

    private fun unionFindMst(
        nodes: List<GraphNodeComponent>,
        allEdges: List<Edge>
    ): List<Pair<String, String>> {
        val parent = nodes.associate { it.id to it.id }.toMutableMap()
        fun find(id: String): String {
            if (parent[id] != id) {
                parent[id] = find(parent[id]!!)
            }
            return parent[id]!!
        }
        fun union(id1: String, id2: String) {
            parent[find(id2)] = find(id1)
        }
        val mst = mutableListOf<Pair<String, String>>()
        for (edge in allEdges) {
            if (find(edge.from) != find(edge.to)) {
                mst.add(edge.from to edge.to)
                union(edge.from, edge.to)
                if (mst.size == nodes.size - 1) break
            }
        }
        return mst
    }

    fun calculateDistance(n1: GraphNodeComponent, n2: GraphNodeComponent): Double {
        val pos1 = n1.position ?: return 1.0
        val pos2 = n2.position ?: return 1.0
        val dx = (pos1.first - pos2.first).toDouble()
        val dy = (pos1.second - pos2.second).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    fun addLoopEdges(
        nodes: List<GraphNodeComponent>,
        mstEdges: List<Pair<String, String>>,
        loopFrequency: Double = 0.5,
        rng: Random
    ): List<Pair<String, String>> {
        val n = nodes.size
        val minExtraEdges = ((n + 2) / 2.0).toInt().coerceAtLeast(1)
        val buffer = (minExtraEdges * (0.10 + rng.nextDouble() * 0.10)).toInt().coerceAtLeast(1)
        val extraEdges = (buffer * 2.0 * loopFrequency).toInt()
        val targetCount = (minExtraEdges + extraEdges).coerceAtLeast(1)
        val existingEdges = mstEdges.toSet()
        val adjacency = buildAdjacencyMap(mstEdges)
        val loopEdges = mutableListOf<Pair<String, String>>()
        val candidates = collectLongLoopCandidates(nodes, existingEdges, adjacency)
        loopEdges.addAll(candidates.shuffled(rng).take(targetCount.coerceAtMost(candidates.size)))
        if (loopEdges.size < minExtraEdges) {
            fillRemainingLoops(nodes, existingEdges, loopEdges, minExtraEdges, rng)
        }
        return loopEdges
    }

    private fun collectLongLoopCandidates(
        nodes: List<GraphNodeComponent>,
        existingEdges: Set<Pair<String, String>>,
        adjacency: Map<String, List<String>>
    ): MutableList<Pair<String, String>> {
        val candidates = mutableListOf<Pair<String, String>>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val edge = nodes[i].id to nodes[j].id
                val reverseEdge = nodes[j].id to nodes[i].id
                if (edge in existingEdges || reverseEdge in existingEdges) continue
                val distance = shortestPath(nodes[i].id, nodes[j].id, adjacency)
                if (distance > 2) {
                    candidates.add(edge)
                }
            }
        }
        return candidates
    }

    private fun fillRemainingLoops(
        nodes: List<GraphNodeComponent>,
        existingEdges: Set<Pair<String, String>>,
        loopEdges: MutableList<Pair<String, String>>,
        minExtraEdges: Int,
        rng: Random
    ) {
        val allPossibleEdges = mutableListOf<Pair<String, String>>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val edge = nodes[i].id to nodes[j].id
                val reverseEdge = nodes[j].id to nodes[i].id
                if (edge !in existingEdges && reverseEdge !in existingEdges && edge !in loopEdges) {
                    allPossibleEdges.add(edge)
                }
            }
        }
        val needed = minExtraEdges - loopEdges.size
        loopEdges.addAll(allPossibleEdges.shuffled(rng).take(needed.coerceAtMost(allPossibleEdges.size)))
    }

    fun buildAdjacencyMap(edges: List<Pair<String, String>>): Map<String, List<String>> {
        val adj = mutableMapOf<String, MutableList<String>>()
        for ((from, to) in edges) {
            adj.getOrPut(from) { mutableListOf() }.add(to)
            adj.getOrPut(to) { mutableListOf() }.add(from)
        }
        return adj
    }

    fun shortestPath(from: String, to: String, adjacency: Map<String, List<String>>): Int {
        if (from == to) return 0
        val queue = ArrayDeque<Pair<String, Int>>()
        val visited = mutableSetOf<String>()
        queue.add(from to 0)
        visited.add(from)
        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()
            for (neighbor in adjacency[current] ?: emptyList()) {
                if (neighbor == to) return distance + 1
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor to distance + 1)
                }
            }
        }
        return Int.MAX_VALUE // Not connected
    }

    /** Edge with weight for MST. */
    internal data class Edge(val from: String, val to: String, val weight: Double)
}
