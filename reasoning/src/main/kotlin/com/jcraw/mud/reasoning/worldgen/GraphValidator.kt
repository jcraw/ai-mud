package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.world.NodeType

/**
 * Validates graph topology for quality assurance
 * Post-generation validation ensures:
 * - Full connectivity (reachability from entry)
 * - At least one loop (cycle detection)
 * - Minimum average degree (exploration choices)
 * - Sufficient frontier nodes (expansion capability)
 */
class GraphValidator {
    /**
     * Validate graph structure meets quality requirements
     *
     * @param nodes List of graph nodes to validate
     * @return ValidationResult.Success or ValidationResult.Failure with issues
     */
    fun validate(nodes: List<GraphNodeComponent>): ValidationResult {
        if (nodes.isEmpty()) {
            return ValidationResult.Failure(listOf("Graph is empty"))
        }

        val issues = mutableListOf<String>()

        // Check 1: Full connectivity (all nodes reachable from first node)
        if (!isFullyConnected(nodes)) {
            issues.add("Graph not fully connected - some nodes are unreachable")
        }

        // Check 2: At least one loop exists
        if (!hasLoop(nodes)) {
            issues.add("No loops found - graph is a tree")
        }

        // Check 3: Average degree >= 3.0 (balanced exploration)
        val avgDeg = avgDegree(nodes)
        if (avgDeg < 3.0) {
            issues.add("Average degree %.2f < 3.0 - insufficient connectivity".format(avgDeg))
        }

        // Check 4: At least 2 frontier nodes for expansion
        val frontierCnt = frontierCount(nodes)
        if (frontierCnt < 2) {
            issues.add("Frontier count $frontierCnt < 2 - insufficient expansion points")
        }

        // Check 5: Bidirectional edges have opposite directions
        val directionIssues = validateBidirectionalDirections(nodes)
        issues.addAll(directionIssues)

        return if (issues.isEmpty()) {
            ValidationResult.Success
        } else {
            ValidationResult.Failure(issues)
        }
    }

    /**
     * Check if all nodes are reachable from the first node (entry point)
     * Uses BFS traversal
     *
     * @param nodes Graph nodes
     * @return true if all nodes reachable from first node
     */
    private fun isFullyConnected(nodes: List<GraphNodeComponent>): Boolean {
        if (nodes.size <= 1) return true

        val startNode = nodes.first()
        val adjacency = buildAdjacencyMap(nodes)
        val reachable = bfsReachable(startNode.id, adjacency)

        return reachable.size == nodes.size
    }

    /**
     * BFS to find all reachable nodes from start
     *
     * @param startId Starting node ID
     * @param adjacency Adjacency map
     * @return Set of reachable node IDs
     */
    private fun bfsReachable(startId: String, adjacency: Map<String, List<String>>): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        queue.add(startId)
        visited.add(startId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            for (neighbor in adjacency[current] ?: emptyList()) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }

        return visited
    }

    /**
     * Check if graph contains at least one loop (cycle)
     * Uses DFS with visited tracking to detect back edges
     *
     * @param nodes Graph nodes
     * @return true if graph has at least one cycle
     */
    private fun hasLoop(nodes: List<GraphNodeComponent>): Boolean {
        if (nodes.size < 3) return false // Need at least 3 nodes for a cycle

        val adjacency = buildAdjacencyMap(nodes)
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        // Try DFS from each unvisited node (handles disconnected components)
        for (node in nodes) {
            if (node.id !in visited) {
                if (hasCycleDFS(node.id, null, adjacency, visited, recursionStack)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * DFS helper for cycle detection
     * Detects back edges (edges to nodes in recursion stack)
     *
     * @param nodeId Current node
     * @param parentId Parent node (to avoid false positives from bidirectional edges)
     * @param adjacency Adjacency map
     * @param visited Permanently visited nodes
     * @param recursionStack Nodes in current DFS path
     * @return true if cycle detected
     */
    private fun hasCycleDFS(
        nodeId: String,
        parentId: String?,
        adjacency: Map<String, List<String>>,
        visited: MutableSet<String>,
        recursionStack: MutableSet<String>
    ): Boolean {
        visited.add(nodeId)
        recursionStack.add(nodeId)

        for (neighbor in adjacency[nodeId] ?: emptyList()) {
            // Skip parent to avoid false positives from bidirectional edges
            if (neighbor == parentId) continue

            if (neighbor in recursionStack) {
                // Back edge found - cycle detected
                return true
            }

            if (neighbor !in visited) {
                if (hasCycleDFS(neighbor, nodeId, adjacency, visited, recursionStack)) {
                    return true
                }
            }
        }

        recursionStack.remove(nodeId)
        return false
    }

    /**
     * Calculate average node degree
     * Average degree indicates connectivity density
     * Target: >= 3.0 for balanced exploration choices
     *
     * @param nodes Graph nodes
     * @return Average degree (edges per node)
     */
    private fun avgDegree(nodes: List<GraphNodeComponent>): Double {
        if (nodes.isEmpty()) return 0.0

        val totalDegree = nodes.sumOf { it.degree() }
        return totalDegree.toDouble() / nodes.size
    }

    /**
     * Count frontier nodes
     * Frontiers enable infinite expansion via chunk cascade
     * Minimum 2 required for robustness
     *
     * @param nodes Graph nodes
     * @return Number of frontier nodes
     */
    private fun frontierCount(nodes: List<GraphNodeComponent>): Int {
        return nodes.count { it.type == NodeType.Frontier }
    }

    /**
     * Build adjacency map from graph nodes
     * Converts node list with EdgeData to adjacency list
     *
     * @param nodes Graph nodes
     * @return Map of node ID to list of neighbor IDs
     */
    private fun buildAdjacencyMap(nodes: List<GraphNodeComponent>): Map<String, List<String>> {
        return nodes.associate { node ->
            node.id to node.neighbors.map { it.targetId }
        }
    }

    /**
     * Validate that bidirectional edges have opposite directions
     * For every edge A→B with direction X, edge B→A should have opposite direction
     *
     * @param nodes Graph nodes
     * @return List of validation issues (empty if all edges are consistent)
     */
    private fun validateBidirectionalDirections(nodes: List<GraphNodeComponent>): List<String> {
        val issues = mutableListOf<String>()
        val nodeMap = nodes.associateBy { it.id }

        // Track which edge pairs we've already checked
        val checkedPairs = mutableSetOf<Pair<String, String>>()

        for (node in nodes) {
            for (edge in node.neighbors) {
                val pairKey = node.id to edge.targetId
                val reversePairKey = edge.targetId to node.id

                // Skip if we've already checked this pair
                if (pairKey in checkedPairs || reversePairKey in checkedPairs) {
                    continue
                }
                checkedPairs.add(pairKey)

                // Find the reverse edge
                val targetNode = nodeMap[edge.targetId]
                if (targetNode == null) {
                    issues.add("Node ${node.id} references non-existent node ${edge.targetId}")
                    continue
                }

                val reverseEdge = targetNode.neighbors.find { it.targetId == node.id }
                if (reverseEdge == null) {
                    issues.add("Missing bidirectional edge: ${node.id} → ${edge.targetId} exists, but reverse doesn't")
                    continue
                }

                // Check if directions are opposites
                val forwardDir = Direction.fromString(edge.direction)
                val reverseDir = Direction.fromString(reverseEdge.direction)

                if (forwardDir != null && reverseDir != null) {
                    val expectedReverse = forwardDir.opposite
                    if (expectedReverse != reverseDir) {
                        issues.add(
                            "Inconsistent bidirectional direction: " +
                            "${node.id} → ${edge.targetId} (${edge.direction}) " +
                            "but ${edge.targetId} → ${node.id} (${reverseEdge.direction}) " +
                            "- expected ${expectedReverse?.displayName}"
                        )
                    }
                }
            }
        }

        return issues
    }
}

/**
 * Result of graph validation
 */
sealed class ValidationResult {
    /**
     * Validation passed - graph meets all quality requirements
     */
    object Success : ValidationResult() {
        override fun toString(): String = "Success"
    }

    /**
     * Validation failed - graph has issues
     *
     * @param reasons List of validation failure reasons
     */
    data class Failure(val reasons: List<String>) : ValidationResult() {
        override fun toString(): String = "Failure: ${reasons.joinToString(", ")}"
    }
}
