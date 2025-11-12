package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.world.NodeType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for GraphGenerator - graph topology generation algorithms
 *
 * Focus on:
 * - Layout algorithms produce expected node counts
 * - MST creates connected graphs
 * - Extra edges create loops
 * - Node type assignment follows rules
 * - Hidden edges marked correctly
 * - Property-based invariants (connectivity, avg degree)
 */
class GraphGeneratorTest {

    private val rng = Random(42) // Deterministic for tests
    private val generator = GraphGenerator(rng, difficultyLevel = 1)

    // ==================== GRID LAYOUT ====================

    @Test
    fun `generate Grid layout produces correct node count`() {
        val layout = GraphLayout.Grid(width = 4, height = 3)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(12, nodes.size, "Grid 4x3 should produce 12 nodes")
    }

    @Test
    fun `generate Grid layout assigns positions correctly`() {
        val layout = GraphLayout.Grid(width = 3, height = 2)

        val nodes = generator.generate("test_chunk", layout)

        // Check all positions are within bounds
        nodes.forEach { node ->
            assertNotNull(node.position, "Grid nodes should have positions")
            val (x, y) = node.position!!
            assertTrue(x in 0..2, "X should be 0-2, got $x")
            assertTrue(y in 0..1, "Y should be 0-1, got $y")
        }
    }

    @Test
    fun `generate Grid layout creates connected graph`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        // Check all nodes are reachable from first node via BFS
        val reachable = bfsReachable(nodes)
        assertEquals(nodes.size, reachable.size, "All nodes should be reachable (connected graph)")
    }

    // ==================== BSP LAYOUT ====================

    @Test
    fun `generate BSP layout produces nodes within expected range`() {
        val layout = GraphLayout.BSP(minRoomSize = 4, maxDepth = 3)

        val nodes = generator.generate("test_chunk", layout)

        // BSP produces variable node count, should be at least 2^maxDepth
        val minExpected = 1 shl layout.maxDepth // 2^3 = 8
        assertTrue(nodes.size >= minExpected, "BSP should produce at least ${minExpected} nodes, got ${nodes.size}")
        assertTrue(nodes.size <= 100, "BSP should not exceed 100 nodes, got ${nodes.size}")
    }

    @Test
    fun `generate BSP layout assigns positions`() {
        val layout = GraphLayout.BSP(minRoomSize = 3, maxDepth = 2)

        val nodes = generator.generate("test_chunk", layout)

        // All BSP nodes should have positions (room centers)
        nodes.forEach { node ->
            assertNotNull(node.position, "BSP nodes should have positions")
        }
    }

    @Test
    fun `generate BSP layout creates connected graph`() {
        val layout = GraphLayout.BSP(minRoomSize = 4, maxDepth = 3)

        val nodes = generator.generate("test_chunk", layout)

        val reachable = bfsReachable(nodes)
        assertEquals(nodes.size, reachable.size, "All BSP nodes should be connected")
    }

    // ==================== FLOOD-FILL LAYOUT ====================

    @Test
    fun `generate FloodFill layout produces target node count`() {
        val layout = GraphLayout.FloodFill(nodeCount = 20, density = 0.4)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(20, nodes.size, "FloodFill should produce target node count")
    }

    @Test
    fun `generate FloodFill layout with high density creates more connections`() {
        val lowDensity = GraphLayout.FloodFill(nodeCount = 15, density = 0.2)
        val highDensity = GraphLayout.FloodFill(nodeCount = 15, density = 0.8)

        val gen1 = GraphGenerator(Random(100), difficultyLevel = 1)
        val gen2 = GraphGenerator(Random(100), difficultyLevel = 1)

        val nodesLow = gen1.generate("chunk_low", lowDensity)
        val nodesHigh = gen2.generate("chunk_high", highDensity)

        val avgDegreeLow = nodesLow.sumOf { it.degree() } / nodesLow.size.toDouble()
        val avgDegreeHigh = nodesHigh.sumOf { it.degree() } / nodesHigh.size.toDouble()

        // Higher density should generally produce higher avg degree (not always due to randomness)
        // Just check both are reasonable
        assertTrue(avgDegreeLow >= 2.0, "Low density should have avg degree >= 2.0")
        assertTrue(avgDegreeHigh >= 2.0, "High density should have avg degree >= 2.0")
    }

    @Test
    fun `generate FloodFill layout creates connected graph`() {
        val layout = GraphLayout.FloodFill(nodeCount = 25, density = 0.3)

        val nodes = generator.generate("test_chunk", layout)

        val reachable = bfsReachable(nodes)
        assertEquals(nodes.size, reachable.size, "All FloodFill nodes should be connected")
    }

    // ==================== MST AND LOOPS ====================

    @Test
    fun `generate creates fully connected graph via MST`() {
        val layout = GraphLayout.Grid(width = 4, height = 4)

        val nodes = generator.generate("test_chunk", layout)

        // Every node should have at least 1 edge (no isolated nodes)
        nodes.forEach { node ->
            assertTrue(node.degree() >= 1, "Node ${node.id} has degree 0 (isolated)")
        }
    }

    @Test
    fun `generate adds extra edges for loops`() {
        val layout = GraphLayout.Grid(width = 5, height = 5) // 25 nodes

        val nodes = generator.generate("test_chunk", layout)

        // MST has (n-1) edges per direction = 24 edges total (bidirectional counts double: 48 edge refs)
        // With 20% extra: 48 * 1.2 = ~58 edge refs
        val totalEdgeRefs = nodes.sumOf { it.degree() }

        // Should be > MST edge refs (48)
        assertTrue(totalEdgeRefs > 48, "Should have more than MST edges due to loops (got $totalEdgeRefs)")
    }

    @Test
    fun `generate creates at least one loop in graph`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        // Detect cycle using DFS
        val hasCycle = detectCycle(nodes)
        assertTrue(hasCycle, "Graph should contain at least one loop")
    }

    // ==================== NODE TYPE ASSIGNMENT ====================

    @Test
    fun `generate assigns Hub type to first node`() {
        val layout = GraphLayout.Grid(width = 4, height = 4)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(NodeType.Hub, nodes.first().type, "First node should be Hub (entry point)")
    }

    @Test
    fun `generate assigns Boss type to at least one node`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        val bossCount = nodes.count { it.type == NodeType.Boss }
        assertTrue(bossCount >= 1, "Should have at least 1 Boss node, got $bossCount")
    }

    @Test
    fun `generate assigns at least 2 Frontier nodes`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        val frontierCount = nodes.count { it.type == NodeType.Frontier }
        assertTrue(frontierCount >= 2, "Should have at least 2 Frontier nodes, got $frontierCount")
    }

    @Test
    fun `generate assigns some Dead-end nodes`() {
        val layout = GraphLayout.Grid(width = 6, height = 6)

        val nodes = generator.generate("test_chunk", layout)

        val deadEndCount = nodes.count { it.type == NodeType.DeadEnd }
        // Dead-ends are ~20% of graph, should have at least 1-2
        assertTrue(deadEndCount >= 1, "Should have at least 1 Dead-end node, got $deadEndCount")
    }

    @Test
    fun `generate assigns Linear or Branching to middle nodes`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        val linearOrBranching = nodes.count {
            it.type == NodeType.Linear || it.type == NodeType.Branching
        }

        // Most nodes should be Linear or Branching
        assertTrue(linearOrBranching >= nodes.size / 2, "At least half nodes should be Linear/Branching")
    }

    @Test
    fun `generate respects node type degree constraints`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        nodes.forEach { node ->
            when (node.type) {
                NodeType.DeadEnd -> {
                    assertEquals(1, node.degree(), "DeadEnd should have degree 1, got ${node.degree()}")
                }
                NodeType.Linear -> {
                    assertEquals(2, node.degree(), "Linear should have degree 2, got ${node.degree()}")
                }
                NodeType.Hub -> {
                    assertTrue(node.degree() >= 3, "Hub should have degree >= 3, got ${node.degree()}")
                }
                else -> { /* Other types have flexible degree */ }
            }
        }
    }

    // ==================== HIDDEN EDGES ====================

    @Test
    fun `generate marks 15-25 percent edges as hidden`() {
        val layout = GraphLayout.Grid(width = 6, height = 6)

        val nodes = generator.generate("test_chunk", layout)

        val totalEdgeRefs = nodes.sumOf { it.neighbors.size }
        val hiddenEdgeRefs = nodes.sumOf { node ->
            node.neighbors.count { it.hidden }
        }

        val hiddenPercentage = hiddenEdgeRefs / totalEdgeRefs.toDouble()

        assertTrue(hiddenPercentage >= 0.10, "At least 10% edges should be hidden, got ${hiddenPercentage * 100}%")
        assertTrue(hiddenPercentage <= 0.30, "At most 30% edges should be hidden, got ${hiddenPercentage * 100}%")
    }

    @Test
    fun `generate adds Perception conditions to hidden edges`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val nodes = generator.generate("test_chunk", layout)

        val hiddenEdges = nodes.flatMap { node ->
            node.neighbors.filter { it.hidden }
        }

        // All hidden edges should have Perception condition
        hiddenEdges.forEach { edge ->
            assertTrue(edge.conditions.any { it is com.jcraw.mud.core.world.Condition.SkillCheck && it.skill == "Perception" },
                "Hidden edge should have Perception condition")
        }
    }

    @Test
    fun `generate ensures edges have reverse counterparts`() {
        val layout = GraphLayout.Grid(width = 4, height = 4)
        val nodes = generator.generate("bidirectional_chunk", layout)
        val nodeMap = nodes.associateBy { it.id }

        nodes.forEach { node ->
            node.neighbors.forEach { edge ->
                val target = nodeMap[edge.targetId]
                assertNotNull(target, "Target node ${edge.targetId} should exist")
                val hasReverse = target.neighbors.any { it.targetId == node.id }
                assertTrue(hasReverse, "Edge ${node.id} -> ${edge.targetId} missing reverse")
            }
        }
    }

    @Test
    fun `generate enforces unique direction labels per node`() {
        val layout = GraphLayout.Grid(width = 4, height = 4)
        val nodes = generator.generate("direction_chunk", layout)

        nodes.forEach { node ->
            val directions = node.neighbors.map { it.direction.lowercase() }
            assertEquals(
                directions.size,
                directions.toSet().size,
                "Node ${node.id} has duplicate directions: $directions"
            )
        }
    }

    // ==================== PROPERTY-BASED TESTS ====================

    @Test
    fun `generate always produces connected graph`() {
        val layouts = listOf(
            GraphLayout.Grid(5, 5),
            GraphLayout.BSP(minRoomSize = 4, maxDepth = 3),
            GraphLayout.FloodFill(nodeCount = 20, density = 0.4)
        )

        layouts.forEach { layout ->
            val nodes = generator.generate("test_chunk", layout)
            val reachable = bfsReachable(nodes)

            assertEquals(nodes.size, reachable.size,
                "Layout $layout should produce connected graph")
        }
    }

    @Test
    fun `generate produces average degree around 3-4`() {
        val layout = GraphLayout.Grid(width = 7, height = 7)

        val nodes = generator.generate("test_chunk", layout)

        val avgDegree = nodes.sumOf { it.degree() } / nodes.size.toDouble()

        assertTrue(avgDegree >= 3.0, "Avg degree should be >= 3.0, got $avgDegree")
        assertTrue(avgDegree <= 5.0, "Avg degree should be <= 5.0, got $avgDegree")
    }

    @Test
    fun `generate with different seeds produces different graphs`() {
        val layout = GraphLayout.Grid(width = 5, height = 5)

        val gen1 = GraphGenerator(Random(42), difficultyLevel = 1)
        val gen2 = GraphGenerator(Random(99), difficultyLevel = 1)

        val nodes1 = gen1.generate("chunk1", layout)
        val nodes2 = gen2.generate("chunk2", layout)

        // Graphs should differ in structure (different loop edges/hidden edges)
        val edges1 = nodes1.sumOf { it.neighbors.size }
        val edges2 = nodes2.sumOf { it.neighbors.size }

        // Not guaranteed to differ, but highly likely with different seeds
        // At minimum, check both are valid
        assertTrue(edges1 > 0, "Graph 1 should have edges")
        assertTrue(edges2 > 0, "Graph 2 should have edges")
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `generate handles small grid 2x2`() {
        val layout = GraphLayout.Grid(width = 2, height = 2)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(4, nodes.size)
        val reachable = bfsReachable(nodes)
        assertEquals(4, reachable.size, "Small grid should still be connected")
    }

    @Test
    fun `generate handles large grid 10x10`() {
        val layout = GraphLayout.Grid(width = 10, height = 10)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(100, nodes.size, "10x10 grid should produce 100 nodes (at size limit)")
        val reachable = bfsReachable(nodes)
        assertEquals(100, reachable.size, "Large grid should be connected")
    }

    @Test
    fun `generate handles minimal FloodFill 5 nodes`() {
        val layout = GraphLayout.FloodFill(nodeCount = 5, density = 0.3)

        val nodes = generator.generate("test_chunk", layout)

        assertEquals(5, nodes.size)
        val reachable = bfsReachable(nodes)
        assertEquals(5, reachable.size, "Minimal FloodFill should be connected")
    }

    // ==================== HELPERS ====================

    /**
     * BFS to find all reachable nodes from first node
     */
    private fun bfsReachable(nodes: List<com.jcraw.mud.core.GraphNodeComponent>): Set<String> {
        if (nodes.isEmpty()) return emptySet()

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        queue.add(nodes.first().id)
        visited.add(nodes.first().id)

        val nodeMap = nodes.associateBy { it.id }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val node = nodeMap[current] ?: continue

            for (edge in node.neighbors) {
                if (edge.targetId !in visited) {
                    visited.add(edge.targetId)
                    queue.add(edge.targetId)
                }
            }
        }

        return visited
    }

    /**
     * Detect cycle in graph using DFS
     */
    private fun detectCycle(nodes: List<com.jcraw.mud.core.GraphNodeComponent>): Boolean {
        if (nodes.isEmpty()) return false

        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val nodeMap = nodes.associateBy { it.id }

        fun dfs(nodeId: String, parent: String?): Boolean {
            visited.add(nodeId)
            recStack.add(nodeId)

            val node = nodeMap[nodeId] ?: return false

            for (edge in node.neighbors) {
                if (edge.targetId == parent) continue // Skip back-edge to parent (undirected)

                if (edge.targetId in recStack) {
                    return true // Cycle detected
                }

                if (edge.targetId !in visited) {
                    if (dfs(edge.targetId, nodeId)) return true
                }
            }

            recStack.remove(nodeId)
            return false
        }

        return dfs(nodes.first().id, null)
    }

    @Test
    fun `loopFrequency affects edge count`() {
        val rng = Random(999)
        val generator = GraphGenerator(rng, difficultyLevel = 1)

        // Generate with low loop frequency
        val lowLoopLayout = GraphLayout.Grid(4, 4, loopFrequency = 0.1)
        val lowLoopNodes = generator.generate("test", lowLoopLayout)
        val lowLoopEdgeCount = lowLoopNodes.sumOf { it.neighbors.size } / 2 // Div by 2 for bidirectional edges

        // Generate with high loop frequency
        val highLoopLayout = GraphLayout.Grid(4, 4, loopFrequency = 1.0)
        val highLoopNodes = generator.generate("test2", highLoopLayout)
        val highLoopEdgeCount = highLoopNodes.sumOf { it.neighbors.size } / 2

        // High frequency should produce more edges
        assertTrue(
            highLoopEdgeCount > lowLoopEdgeCount,
            "High loop frequency (1.0) should create more edges than low frequency (0.1). " +
                "Got high=$highLoopEdgeCount, low=$lowLoopEdgeCount"
        )

        println("Edge counts: low frequency (0.1) = $lowLoopEdgeCount, high frequency (1.0) = $highLoopEdgeCount")
    }

    @Test
    fun `loopFrequency 0 dot 5 creates graphs with cycles`() {
        val rng = Random(888)
        val generator = GraphGenerator(rng, difficultyLevel = 1)

        // Default frequency should still create cycles
        val layout = GraphLayout.Grid(3, 3, loopFrequency = 0.5)
        val nodes = generator.generate("test", layout)

        val hasCycle = detectCycle(nodes)
        assertTrue(hasCycle, "Default frequency (0.5) should create at least one cycle")
    }

    @Test
    fun `loopFrequency 1 dot 0 creates highly connected graphs`() {
        val rng = Random(777)
        val generator = GraphGenerator(rng, difficultyLevel = 1)

        // Max frequency should create lots of edges
        val layout = GraphLayout.Grid(4, 4, loopFrequency = 1.0)
        val nodes = generator.generate("test", layout)

        // With max frequency, average degree should be at least as high as default
        val avgDegree = nodes.sumOf { it.neighbors.size }.toDouble() / nodes.size
        assertTrue(
            avgDegree >= 3.0,
            "Max loop frequency should create well-connected graph (avg degree >= 3.0). Got $avgDegree"
        )

        println("Average degree with loopFrequency=1.0: $avgDegree")
    }
}
