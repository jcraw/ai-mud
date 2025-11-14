package com.jcraw.mud.reasoning.worldgen

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.DisplayName

/**
 * Test suite for GraphValidator
 * Tests validation logic for graph connectivity, loops, degree, and frontiers
 */
class GraphValidatorTest {
    private val validator = GraphValidator()

    // ==================== VALID GRAPH TESTS ====================

    @Test
    @DisplayName("Valid graph with all requirements passes validation")
    fun testValidGraph() {
        // Create 5-node graph with loop, good connectivity, frontiers
        // Average degree: (3 + 3 + 4 + 3 + 2) / 5 = 3.0
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east"),
                    EdgeData("node5", "northeast")  // Added edge to increase avg degree
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west"),
                    EdgeData("node4", "south"),
                    EdgeData("node5", "east")
                )
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "north"),
                    EdgeData("node3", "north"),
                    EdgeData("node5", "east")
                )
            ),
            GraphNodeComponent(
                id = "node5",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "southwest"),  // Reverse of added edge
                    EdgeData("node3", "west"),
                    EdgeData("node4", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        if (result is ValidationResult.Failure) {
            println("Validation failures: ${result.reasons}")
        }
        assertTrue(result is ValidationResult.Success, "Valid graph should pass validation")
    }

    @Test
    @DisplayName("Empty graph fails validation")
    fun testEmptyGraph() {
        val nodes = emptyList<GraphNodeComponent>()

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("empty") })
    }

    @Test
    @DisplayName("Single node graph passes validation (edge case)")
    fun testSingleNodeGraph() {
        // Single node with no edges - technically connected and valid for frontier
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = emptyList()
            )
        )

        val result = validator.validate(nodes)

        // Should fail: no loops, low avg degree, only 1 frontier
        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.size >= 2) // At least 2 issues
    }

    // ==================== CONNECTIVITY TESTS ====================

    @Test
    @DisplayName("Disconnected graph fails connectivity check")
    fun testDisconnectedGraph() {
        // Two separate components: node1-node2 and node3-node4
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(EdgeData("node2", "north"))
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(EdgeData("node1", "south"))
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(EdgeData("node4", "north"))
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(EdgeData("node3", "south"))
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("not fully connected") })
    }

    @Test
    @DisplayName("Fully connected graph passes connectivity check")
    fun testFullyConnectedGraph() {
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        // May still fail on other checks, but shouldn't fail connectivity
        if (result is ValidationResult.Failure) {
            assertFalse(result.reasons.any { it.contains("not fully connected") })
        }
    }

    // ==================== LOOP DETECTION TESTS ====================

    @Test
    @DisplayName("Tree structure (no loops) fails loop check")
    fun testTreeNoLoops() {
        // Tree: node1 -> node2 -> node3, node1 -> node4
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node4", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "north")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "south")
                )
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("No loops") || it.contains("tree") })
    }

    @Test
    @DisplayName("Graph with cycle passes loop check")
    fun testGraphWithCycle() {
        // Triangle: node1 <-> node2 <-> node3 <-> node1
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        // Should pass loop check but may fail on frontier count
        if (result is ValidationResult.Failure) {
            assertFalse(result.reasons.any { it.contains("No loops") || it.contains("tree") })
        }
    }

    @Test
    @DisplayName("Complex graph with multiple cycles passes loop check")
    fun testMultipleCycles() {
        // Graph with 2 cycles
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east"),
                    EdgeData("node4", "north")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west"),
                    EdgeData("node4", "north")
                )
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "south"),
                    EdgeData("node3", "south")
                )
            ),
            GraphNodeComponent(
                id = "node5",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node4", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        // Should pass loop check
        if (result is ValidationResult.Failure) {
            assertFalse(result.reasons.any { it.contains("No loops") || it.contains("tree") })
        }
    }

    // ==================== AVERAGE DEGREE TESTS ====================

    @Test
    @DisplayName("Low average degree graph fails degree check")
    fun testLowAverageDegree() {
        // Chain: node1 <-> node2 <-> node3 (avg degree = 1.33)
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(EdgeData("node2", "north"))
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "north")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(EdgeData("node2", "south"))
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("Average degree") && it.contains("< 3.0") })
    }

    @Test
    @DisplayName("High average degree graph passes degree check")
    fun testHighAverageDegree() {
        // Fully connected 4-node graph (avg degree = 3.0)
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "north"),
                    EdgeData("node2", "north"),
                    EdgeData("node3", "north")
                )
            )
        )

        val result = validator.validate(nodes)

        // Should pass degree check
        if (result is ValidationResult.Failure) {
            assertFalse(result.reasons.any { it.contains("Average degree") && it.contains("< 3.0") })
        }
    }

    // ==================== FRONTIER COUNT TESTS ====================

    @Test
    @DisplayName("Graph with 0 frontiers fails frontier check")
    fun testNoFrontiers() {
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("Frontier count") && it.contains("< 2") })
    }

    @Test
    @DisplayName("Graph with 1 frontier fails frontier check")
    fun testOneFrontier() {
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure)
        val failure = result as ValidationResult.Failure
        assertTrue(failure.reasons.any { it.contains("Frontier count") && it.contains("< 2") })
    }

    @Test
    @DisplayName("Graph with 2+ frontiers passes frontier check")
    fun testMultipleFrontiers() {
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Branching,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west"),
                    EdgeData("node4", "south")
                )
            ),
            GraphNodeComponent(
                id = "node4",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "north"),
                    EdgeData("node3", "north")
                )
            )
        )

        val result = validator.validate(nodes)

        // Should pass frontier check
        if (result is ValidationResult.Failure) {
            assertFalse(result.reasons.any { it.contains("Frontier count") && it.contains("< 2") })
        }
    }

    // ==================== BIDIRECTIONAL DIRECTION CONSISTENCY TESTS ====================

    @Test
    @DisplayName("Bidirectional edges have opposite directions")
    fun testBidirectionalDirectionConsistency() {
        // Create graph where edge pairs should have opposite directions
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "south"),  // Opposite of north
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),  // Opposite of east
                    EdgeData("node2", "west")   // Opposite of east
                )
            )
        )

        val result = validator.validate(nodes)

        // Should pass bidirectional direction check
        if (result is ValidationResult.Failure) {
            assertFalse(
                result.reasons.any { it.contains("bidirectional") || it.contains("direction") },
                "Graph with opposite directions should pass bidirectional check"
            )
        }
    }

    @Test
    @DisplayName("Inconsistent bidirectional directions fail validation")
    fun testInconsistentBidirectionalDirections() {
        // Create graph where edge pairs DON'T have opposite directions
        // This is the bug we're trying to catch!
        val nodes = listOf(
            GraphNodeComponent(
                id = "node1",
                type = NodeType.Hub,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node2", "north"),  // Goes north to node2
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node2",
                type = NodeType.Linear,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "northeast"),  // BUG: Should be "south", not "northeast"
                    EdgeData("node3", "east")
                )
            ),
            GraphNodeComponent(
                id = "node3",
                type = NodeType.Frontier,
                chunkId = "test",
                neighbors = listOf(
                    EdgeData("node1", "west"),
                    EdgeData("node2", "west")
                )
            )
        )

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Failure, "Graph with inconsistent directions should fail")
        val failure = result as ValidationResult.Failure
        assertTrue(
            failure.reasons.any { it.contains("bidirectional direction") },
            "Should report bidirectional direction inconsistency"
        )
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("Large valid graph passes all checks")
    fun testLargeValidGraph() {
        // Create 10-node graph with good properties
        val nodes = (1..10).map { i ->
            val neighbors = mutableListOf<EdgeData>()

            // Connect to previous 2 nodes (creates loops)
            if (i > 1) neighbors.add(EdgeData("node${i-1}", "west"))
            if (i > 2) neighbors.add(EdgeData("node${i-2}", "northwest"))

            // Connect to next 2 nodes (bidirectional)
            if (i < 10) neighbors.add(EdgeData("node${i+1}", "east"))
            if (i < 9) neighbors.add(EdgeData("node${i+2}", "southeast"))

            val nodeType = when {
                i == 1 -> NodeType.Hub
                i == 10 -> NodeType.Boss
                i in 8..9 -> NodeType.Frontier
                else -> NodeType.Branching
            }

            GraphNodeComponent(
                id = "node$i",
                type = nodeType,
                chunkId = "test",
                neighbors = neighbors
            )
        }

        val result = validator.validate(nodes)

        assertTrue(result is ValidationResult.Success, "Large graph with good structure should pass")
    }

    @Test
    @DisplayName("ValidationResult.toString() returns readable format")
    fun testValidationResultToString() {
        val success = ValidationResult.Success
        assertEquals("Success", success.toString())

        val failure = ValidationResult.Failure(listOf("Issue 1", "Issue 2"))
        assertTrue(failure.toString().contains("Issue 1"))
        assertTrue(failure.toString().contains("Issue 2"))
    }
}
