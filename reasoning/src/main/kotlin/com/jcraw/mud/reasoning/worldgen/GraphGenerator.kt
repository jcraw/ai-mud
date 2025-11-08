package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import com.jcraw.mud.core.world.Condition
import kotlin.random.Random
import kotlin.math.sqrt

/**
 * Graph topology generator for V3 world system
 * Generates pre-computed graph structures before content generation
 * Supports multiple layout algorithms: Grid, BSP, FloodFill
 * Uses Kruskal MST for connectivity, adds loops, assigns node types, marks hidden edges
 */
class GraphGenerator(
    private val rng: Random,
    private val difficultyLevel: Int = 1
) {
    /**
     * Generate graph topology for a chunk
     *
     * @param chunkId Chunk this graph belongs to
     * @param layout Algorithm configuration
     * @return List of connected graph nodes with typed structure
     */
    fun generate(
        chunkId: String,
        layout: GraphLayout
    ): List<GraphNodeComponent> {
        // Step 1: Generate initial nodes with positions
        val nodes = when (layout) {
            is GraphLayout.Grid -> generateGridNodes(chunkId, layout)
            is GraphLayout.BSP -> generateBSPNodes(chunkId, layout)
            is GraphLayout.FloodFill -> generateFloodFillNodes(chunkId, layout)
        }

        if (nodes.isEmpty()) {
            throw IllegalStateException("Layout $layout generated no nodes")
        }

        // Step 2: Connect nodes via Kruskal MST
        val mstEdges = kruskalMST(nodes)

        // Step 3: Add 50-75% extra edges for loops
        val loopEdges = addLoopEdges(nodes, mstEdges)
        val allEdges = mstEdges + loopEdges

        // Step 4: Build adjacency for each node
        val nodesWithEdges = buildNodeEdges(nodes, allEdges)

        // Step 5: Assign node types based on structure
        val typedNodes = assignNodeTypes(nodesWithEdges, chunkId)

        // Step 6: Mark 15-25% edges as hidden
        val finalNodes = markHiddenEdges(typedNodes)

        return finalNodes
    }

    // ==================== GRID LAYOUT ====================

    /**
     * Generate grid layout - regular NxM arrangement
     * Creates nodes at integer grid positions with predictable structure
     */
    private fun generateGridNodes(
        chunkId: String,
        layout: GraphLayout.Grid
    ): List<GraphNodeComponent> {
        val nodes = mutableListOf<GraphNodeComponent>()

        for (y in 0 until layout.height) {
            for (x in 0 until layout.width) {
                val nodeId = "$chunkId:grid_${x}_${y}"
                nodes.add(
                    GraphNodeComponent(
                        id = nodeId,
                        position = x to y,
                        type = NodeType.Linear, // Temporary, will reassign
                        chunkId = chunkId
                    )
                )
            }
        }

        return nodes
    }

    // ==================== BSP LAYOUT ====================

    /**
     * Generate BSP layout - recursive space partitioning
     * Creates hierarchical room structure via binary splits
     */
    private fun generateBSPNodes(
        chunkId: String,
        layout: GraphLayout.BSP
    ): List<GraphNodeComponent> {
        val rooms = mutableListOf<BSPRoom>()

        // Start with full space (arbitrary 50x50 units)
        val root = BSPRoom(0, 0, 50, 50)
        subdivideRoom(root, layout.minRoomSize, layout.maxDepth, 0, rooms)

        // Create nodes from rooms (one node per room center)
        return rooms.mapIndexed { index, room ->
            val centerX = room.x + room.width / 2
            val centerY = room.y + room.height / 2
            val nodeId = "$chunkId:bsp_$index"
            GraphNodeComponent(
                id = nodeId,
                position = centerX to centerY,
                type = NodeType.Linear, // Temporary
                chunkId = chunkId
            )
        }
    }

    /**
     * Recursively subdivide BSP room
     */
    private fun subdivideRoom(
        room: BSPRoom,
        minSize: Int,
        maxDepth: Int,
        depth: Int,
        output: MutableList<BSPRoom>
    ) {
        if (depth >= maxDepth || room.width < minSize * 2 || room.height < minSize * 2) {
            // Leaf room - add to output
            output.add(room)
            return
        }

        // Choose split direction (horizontal or vertical)
        val splitHorizontal = if (room.width > room.height) {
            rng.nextBoolean()
        } else {
            room.height > room.width
        }

        if (splitHorizontal) {
            // Split horizontally (divide height)
            val splitY = room.y + minSize + rng.nextInt(room.height - minSize * 2 + 1)
            val top = BSPRoom(room.x, room.y, room.width, splitY - room.y)
            val bottom = BSPRoom(room.x, splitY, room.width, room.y + room.height - splitY)
            subdivideRoom(top, minSize, maxDepth, depth + 1, output)
            subdivideRoom(bottom, minSize, maxDepth, depth + 1, output)
        } else {
            // Split vertically (divide width)
            val splitX = room.x + minSize + rng.nextInt(room.width - minSize * 2 + 1)
            val left = BSPRoom(room.x, room.y, splitX - room.x, room.height)
            val right = BSPRoom(splitX, room.y, room.x + room.width - splitX, room.height)
            subdivideRoom(left, minSize, maxDepth, depth + 1, output)
            subdivideRoom(right, minSize, maxDepth, depth + 1, output)
        }
    }

    /**
     * BSP room representation
     */
    private data class BSPRoom(val x: Int, val y: Int, val width: Int, val height: Int)

    // ==================== FLOOD-FILL LAYOUT ====================

    /**
     * Generate flood-fill layout - organic growth
     * Creates irregular, cave-like structures via random expansion
     */
    private fun generateFloodFillNodes(
        chunkId: String,
        layout: GraphLayout.FloodFill
    ): List<GraphNodeComponent> {
        val nodes = mutableListOf<GraphNodeComponent>()
        val occupied = mutableSetOf<Pair<Int, Int>>()

        // Start at origin
        val startPos = 0 to 0
        occupied.add(startPos)
        nodes.add(
            GraphNodeComponent(
                id = "$chunkId:flood_0",
                position = startPos,
                type = NodeType.Linear, // Temporary
                chunkId = chunkId
            )
        )

        // Flood-fill expansion
        val candidates = mutableListOf(startPos)
        var nodeIndex = 1

        while (nodes.size < layout.nodeCount && candidates.isNotEmpty()) {
            // Pick random candidate
            val current = candidates.removeAt(rng.nextInt(candidates.size))

            // Try expanding to neighbors
            val neighbors = listOf(
                current.first + 1 to current.second,
                current.first - 1 to current.second,
                current.first to current.second + 1,
                current.first to current.second - 1
            ).filter { it !in occupied }

            // Add some neighbors based on density
            for (neighbor in neighbors.shuffled(rng).take((neighbors.size * layout.density).toInt().coerceAtLeast(1))) {
                if (nodes.size >= layout.nodeCount) break

                occupied.add(neighbor)
                nodes.add(
                    GraphNodeComponent(
                        id = "$chunkId:flood_$nodeIndex",
                        position = neighbor,
                        type = NodeType.Linear, // Temporary
                        chunkId = chunkId
                    )
                )
                nodeIndex++
                candidates.add(neighbor)
            }
        }

        return nodes
    }

    // ==================== KRUSKAL MST ====================

    /**
     * Kruskal's algorithm for Minimum Spanning Tree
     * Ensures all nodes are connected with minimal edges
     * Returns list of edges as (fromId, toId) pairs
     */
    private fun kruskalMST(nodes: List<GraphNodeComponent>): List<Pair<String, String>> {
        if (nodes.size == 1) return emptyList()

        // Create all possible edges with weights (Euclidean distance)
        val allEdges = mutableListOf<Edge>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val weight = calculateDistance(nodes[i], nodes[j])
                allEdges.add(Edge(nodes[i].id, nodes[j].id, weight))
            }
        }

        // Sort edges by weight
        allEdges.sortBy { it.weight }

        // Union-Find for cycle detection
        val parent = nodes.associate { it.id to it.id }.toMutableMap()

        fun find(id: String): String {
            if (parent[id] != id) {
                parent[id] = find(parent[id]!!)
            }
            return parent[id]!!
        }

        fun union(id1: String, id2: String) {
            val root1 = find(id1)
            val root2 = find(id2)
            parent[root2] = root1
        }

        // Build MST
        val mst = mutableListOf<Pair<String, String>>()
        for (edge in allEdges) {
            if (find(edge.from) != find(edge.to)) {
                mst.add(edge.from to edge.to)
                union(edge.from, edge.to)

                // MST has exactly (n-1) edges
                if (mst.size == nodes.size - 1) break
            }
        }

        return mst
    }

    /**
     * Calculate Euclidean distance between nodes
     * Uses positions if available, otherwise returns constant
     */
    private fun calculateDistance(n1: GraphNodeComponent, n2: GraphNodeComponent): Double {
        val pos1 = n1.position ?: return 1.0
        val pos2 = n2.position ?: return 1.0

        val dx = (pos1.first - pos2.first).toDouble()
        val dy = (pos1.second - pos2.second).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Edge with weight for MST
     */
    private data class Edge(val from: String, val to: String, val weight: Double)

    // ==================== LOOP EDGES ====================

    /**
     * Add extra edges for loops to guarantee avg degree >= 3.0
     * Creates alternative paths and exploration choices
     * Promotes average degree of 3.0-3.5 for engaging navigation
     * Returns additional edges beyond MST
     */
    private fun addLoopEdges(
        nodes: List<GraphNodeComponent>,
        mstEdges: List<Pair<String, String>>
    ): List<Pair<String, String>> {
        val n = nodes.size

        // Calculate minimum edges needed for avg degree >= 3.0
        // Total degree needed: 3.0 * N
        // MST provides: 2 * (N-1) total degree
        // Need additional: 3.0*N - 2*(N-1) = N + 2 total degree
        // Each edge adds 2 to total degree, so need (N+2)/2 edges minimum
        val minExtraEdges = ((n + 2) / 2.0).toInt().coerceAtLeast(1)

        // Add 10-20% buffer for variety (avg degree 3.1-3.4)
        val buffer = (minExtraEdges * (0.10 + rng.nextDouble() * 0.10)).toInt().coerceAtLeast(1)
        val targetCount = minExtraEdges + buffer

        val loopEdges = mutableListOf<Pair<String, String>>()
        val existingEdges = mstEdges.toSet()

        // Build adjacency for distance check
        val adjacency = buildAdjacencyMap(mstEdges)

        // Collect candidates - prefer edges between non-adjacent nodes (creates longer loops)
        val candidates = mutableListOf<Pair<String, String>>()
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val edge = nodes[i].id to nodes[j].id
                val reverseEdge = nodes[j].id to nodes[i].id

                // Skip if edge already exists
                if (edge in existingEdges || reverseEdge in existingEdges) continue

                val distance = shortestPath(nodes[i].id, nodes[j].id, adjacency)
                if (distance > 2) {
                    candidates.add(edge)
                }
            }
        }

        // Add random loop edges from candidates
        loopEdges.addAll(candidates.shuffled(rng).take(targetCount.coerceAtMost(candidates.size)))

        // If we don't have enough candidates (rare with small graphs), add any remaining edges
        if (loopEdges.size < minExtraEdges) {
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

        return loopEdges
    }

    /**
     * Build adjacency map from edge list
     */
    private fun buildAdjacencyMap(edges: List<Pair<String, String>>): Map<String, List<String>> {
        val adj = mutableMapOf<String, MutableList<String>>()
        for ((from, to) in edges) {
            adj.getOrPut(from) { mutableListOf() }.add(to)
            adj.getOrPut(to) { mutableListOf() }.add(from)
        }
        return adj
    }

    /**
     * BFS shortest path distance
     */
    private fun shortestPath(from: String, to: String, adjacency: Map<String, List<String>>): Int {
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

    // ==================== NODE EDGE BUILDING ====================

    /**
     * Build EdgeData for each node from edge list
     * Converts undirected edges to bidirectional EdgeData
     */
    private fun buildNodeEdges(
        nodes: List<GraphNodeComponent>,
        edges: List<Pair<String, String>>
    ): List<GraphNodeComponent> {
        val nodeMap = nodes.associateBy { it.id }
        val edgeMap = mutableMapOf<String, MutableList<EdgeData>>()

        for ((from, to) in edges) {
            val fromNode = nodeMap[from] ?: continue
            val toNode = nodeMap[to] ?: continue

            // Determine direction label
            val direction = calculateDirection(fromNode, toNode)
            val reverseDirection = calculateDirection(toNode, fromNode)

            // Add bidirectional edges
            edgeMap.getOrPut(from) { mutableListOf() }.add(
                EdgeData(targetId = to, direction = direction)
            )
            edgeMap.getOrPut(to) { mutableListOf() }.add(
                EdgeData(targetId = from, direction = reverseDirection)
            )
        }

        // Update nodes with edges
        return nodes.map { node ->
            node.copy(neighbors = edgeMap[node.id] ?: emptyList())
        }
    }

    /**
     * Calculate direction label between nodes
     * Uses position if available, otherwise generic label
     */
    private fun calculateDirection(from: GraphNodeComponent, to: GraphNodeComponent): String {
        val fromPos = from.position
        val toPos = to.position

        if (fromPos == null || toPos == null) {
            // No position - use generic direction
            return "passage"
        }

        val dx = toPos.first - fromPos.first
        val dy = toPos.second - fromPos.second

        // Cardinal directions
        return when {
            dx > 0 && dy == 0 -> "east"
            dx < 0 && dy == 0 -> "west"
            dy > 0 && dx == 0 -> "south"
            dy < 0 && dx == 0 -> "north"
            dx > 0 && dy > 0 -> "southeast"
            dx < 0 && dy > 0 -> "southwest"
            dx > 0 && dy < 0 -> "northeast"
            dx < 0 && dy < 0 -> "northwest"
            else -> "passage"
        }
    }

    // ==================== NODE TYPE ASSIGNMENT ====================

    /**
     * Assign node types based on graph structure
     * Rules:
     * - 1-2 Hubs (first node or central positions)
     * - 1 Boss (farthest from entry)
     * - 2+ Frontiers (boundary nodes)
     * - 20% Dead-ends (degree-1 nodes)
     * - Rest: Linear (degree 2) or Branching (degree 3+)
     */
    private fun assignNodeTypes(
        nodes: List<GraphNodeComponent>,
        chunkId: String
    ): List<GraphNodeComponent> {
        if (nodes.isEmpty()) return nodes

        val mutableNodes = nodes.toMutableList()

        // 1. First node is always a Hub (entry point)
        mutableNodes[0] = mutableNodes[0].copy(type = NodeType.Hub)

        // 2. Find farthest node from entry for Boss
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

        // 3. Identify boundary nodes for Frontiers (guarantee at least 2)
        val minFrontiers = 2
        val targetFrontiers = minFrontiers.coerceAtLeast(mutableNodes.size / 10)
        var frontiersAssigned = 0

        // First try boundary nodes (degree <= 2)
        val boundaryCandidates = findBoundaryNodes(mutableNodes).shuffled(rng)
        for (boundaryId in boundaryCandidates) {
            if (frontiersAssigned >= targetFrontiers) break
            val index = mutableNodes.indexOfFirst { it.id == boundaryId }
            if (index >= 0 && mutableNodes[index].type != NodeType.Hub && mutableNodes[index].type != NodeType.Boss) {
                mutableNodes[index] = mutableNodes[index].copy(type = NodeType.Frontier)
                frontiersAssigned++
            }
        }

        // If we don't have enough, expand to any unassigned node
        if (frontiersAssigned < minFrontiers) {
            val remainingCandidates = mutableNodes.filter {
                it.type == NodeType.Linear  // Still unassigned
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

        // 4. Assign Dead-ends (20% of degree-1 nodes, excluding Boss/Hub/Frontier)
        val deadEndCandidates = mutableNodes.filter {
            it.degree() == 1 && it.type !in listOf(NodeType.Hub, NodeType.Boss, NodeType.Frontier)
        }.shuffled(rng)
        val deadEndCount = (mutableNodes.size * 0.2).toInt().coerceAtLeast(1).coerceAtMost(deadEndCandidates.size)

        for (node in deadEndCandidates.take(deadEndCount)) {
            val index = mutableNodes.indexOfFirst { it.id == node.id }
            if (index >= 0) {
                mutableNodes[index] = mutableNodes[index].copy(type = NodeType.DeadEnd)
            }
        }

        // 5. Assign remaining: Linear (degree 2) or Branching (degree 3+)
        for (i in mutableNodes.indices) {
            if (mutableNodes[i].type != NodeType.Linear) continue // Already assigned

            val degree = mutableNodes[i].degree()
            mutableNodes[i] = when {
                degree == 2 -> mutableNodes[i].copy(type = NodeType.Linear)
                degree >= 3 -> mutableNodes[i].copy(type = NodeType.Branching)
                else -> mutableNodes[i] // Keep Linear for degree 1 (edge case)
            }
        }

        return mutableNodes
    }

    /**
     * Build adjacency map from nodes with edges
     */
    private fun buildAdjacencyFromNodes(nodes: List<GraphNodeComponent>): Map<String, List<String>> {
        return nodes.associate { node ->
            node.id to node.neighbors.map { it.targetId }
        }
    }

    /**
     * BFS to calculate distances from start node
     */
    private fun bfsDistances(startId: String, adjacency: Map<String, List<String>>): Map<String, Int> {
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

    /**
     * Find boundary nodes (candidates for Frontiers)
     * Nodes with low degree or at graph periphery
     */
    private fun findBoundaryNodes(nodes: List<GraphNodeComponent>): List<String> {
        // Simple heuristic: nodes with degree <= 2
        return nodes.filter { it.degree() <= 2 }.map { it.id }
    }

    // ==================== HIDDEN EDGES ====================

    /**
     * Mark 15-25% of edges as hidden
     * Adds Perception difficulty based on chunk depth
     * Returns nodes with updated EdgeData (hidden flags set)
     */
    private fun markHiddenEdges(nodes: List<GraphNodeComponent>): List<GraphNodeComponent> {
        val hiddenPercentage = 0.15 + rng.nextDouble() * 0.10 // 15-25%

        // Count total edges (bidirectional, so divide by 2 conceptually, but we mark edges per node)
        val totalEdgeRefs = nodes.sumOf { it.neighbors.size }
        val targetHiddenCount = (totalEdgeRefs * hiddenPercentage).toInt().coerceAtLeast(1)

        // Collect all edge references (nodeId, edgeIndex)
        val edgeRefs = nodes.flatMapIndexed { nodeIndex, node ->
            node.neighbors.indices.map { edgeIndex -> nodeIndex to edgeIndex }
        }

        // Randomly select edges to hide
        val hiddenRefs = edgeRefs.shuffled(rng).take(targetHiddenCount).toSet()

        // Update nodes with hidden edges
        return nodes.mapIndexed { nodeIndex, node ->
            val updatedNeighbors = node.neighbors.mapIndexed { edgeIndex, edge ->
                if ((nodeIndex to edgeIndex) in hiddenRefs) {
                    // Mark as hidden with Perception difficulty
                    val difficulty = 10 + difficultyLevel * 5 + rng.nextInt(10) // 10-30 range
                    edge.copy(
                        hidden = true,
                        conditions = edge.conditions + Condition.SkillCheck("Perception", difficulty)
                    )
                } else {
                    edge
                }
            }
            node.copy(neighbors = updatedNeighbors)
        }
    }
}
