package com.jcraw.mud.core.world

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.GraphNodeComponent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for GraphNodeComponent
 * Focus on behavior and contracts - immutability, validation, edge operations
 */
class GraphNodeComponentTest {

    @Test
    fun `component type is GRAPH_NODE`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            chunkId = "chunk-1"
        )
        assertEquals(ComponentType.GRAPH_NODE, node.componentType)
    }

    @Test
    fun `addEdge adds edge to neighbors`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            chunkId = "chunk-1"
        )
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val updated = node.addEdge(edge)

        assertEquals(1, updated.neighbors.size)
        assertEquals("node-2", updated.neighbors[0].targetId)
        assertEquals("north", updated.neighbors[0].direction)
    }

    @Test
    fun `addEdge is immutable operation`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            chunkId = "chunk-1"
        )
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val updated = node.addEdge(edge)

        assertEquals(0, node.neighbors.size)
        assertEquals(1, updated.neighbors.size)
    }

    @Test
    fun `addEdge fails for duplicate edge`() {
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            node.addEdge(edge)
        }
        assertTrue(exception.message!!.contains("already exists"))
    }

    @Test
    fun `removeEdge removes edge by target ID`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        val updated = node.removeEdge("node-2")
        assertEquals(1, updated.neighbors.size)
        assertEquals("node-3", updated.neighbors[0].targetId)
    }

    @Test
    fun `removeEdge is immutable operation`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        val updated = node.removeEdge("node-2")
        assertEquals(2, node.neighbors.size)
        assertEquals(1, updated.neighbors.size)
    }

    @Test
    fun `removeEdge fails if target not found`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            chunkId = "chunk-1"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            node.removeEdge("node-2")
        }
        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun `removeEdgeByDirection removes edge by direction`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        val updated = node.removeEdgeByDirection("north")
        assertEquals(1, updated.neighbors.size)
        assertEquals("south", updated.neighbors[0].direction)
    }

    @Test
    fun `removeEdgeByDirection fails if direction not found`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            chunkId = "chunk-1"
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            node.removeEdgeByDirection("north")
        }
        assertTrue(exception.message!!.contains("not found"))
    }

    @Test
    fun `getEdge returns edge by direction case-insensitive`() {
        val edge = EdgeData(targetId = "node-2", direction = "North")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val found = node.getEdge("north")
        assertNotNull(found)
        assertEquals("node-2", found.targetId)
    }

    @Test
    fun `getEdge returns null if direction not found`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            chunkId = "chunk-1"
        )

        val found = node.getEdge("north")
        assertNull(found)
    }

    @Test
    fun `getVisibleEdges returns non-hidden edges`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north", hidden = false)
        val edge2 = EdgeData(targetId = "node-3", direction = "south", hidden = true)
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        val visible = node.getVisibleEdges(emptySet())
        assertEquals(1, visible.size)
        assertEquals("node-2", visible[0].targetId)
    }

    @Test
    fun `getVisibleEdges includes revealed hidden edges`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north", hidden = false)
        val edge2 = EdgeData(targetId = "node-3", direction = "south", hidden = true)
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        val revealedExits = setOf("node-1->node-3")
        val visible = node.getVisibleEdges(revealedExits)
        assertEquals(2, visible.size)
    }

    @Test
    fun `getHiddenEdges returns only unrevealed hidden edges`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north", hidden = false)
        val edge2 = EdgeData(targetId = "node-3", direction = "south", hidden = true)
        val edge3 = EdgeData(targetId = "node-4", direction = "east", hidden = true)
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2, edge3),
            chunkId = "chunk-1"
        )

        val revealedExits = setOf("node-1->node-3")
        val hidden = node.getHiddenEdges(revealedExits)
        assertEquals(1, hidden.size)
        assertEquals("node-4", hidden[0].targetId)
    }

    @Test
    fun `degree returns number of edges`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")
        val edge3 = EdgeData(targetId = "node-4", direction = "east")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = listOf(edge1, edge2, edge3),
            chunkId = "chunk-1"
        )

        assertEquals(3, node.degree())
    }

    @Test
    fun `isDeadEnd returns true for degree 1`() {
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.DeadEnd,
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        assertTrue(node.isDeadEnd())
    }

    @Test
    fun `isDeadEnd returns false for degree greater than 1`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            neighbors = listOf(edge1, edge2),
            chunkId = "chunk-1"
        )

        assertFalse(node.isDeadEnd())
    }

    @Test
    fun `isHub returns true for Hub type`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            chunkId = "chunk-1"
        )

        assertTrue(node.isHub())
    }

    @Test
    fun `isHub returns true for degree 4 or more`() {
        val edges = listOf(
            EdgeData(targetId = "node-2", direction = "north"),
            EdgeData(targetId = "node-3", direction = "south"),
            EdgeData(targetId = "node-4", direction = "east"),
            EdgeData(targetId = "node-5", direction = "west")
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = edges,
            chunkId = "chunk-1"
        )

        assertTrue(node.isHub())
    }

    @Test
    fun `isFrontier returns true for Frontier type`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Frontier,
            chunkId = "chunk-1"
        )

        assertTrue(node.isFrontier())
    }

    @Test
    fun `validate returns true for valid node`() {
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.DeadEnd,
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        assertTrue(node.validate())
    }

    @Test
    fun `validate returns false for empty ID`() {
        val node = GraphNodeComponent(
            id = "",
            type = NodeType.Hub,
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for blank ID`() {
        val node = GraphNodeComponent(
            id = "   ",
            type = NodeType.Hub,
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for DeadEnd with degree not 1`() {
        val edges = listOf(
            EdgeData(targetId = "node-2", direction = "north"),
            EdgeData(targetId = "node-3", direction = "south")
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.DeadEnd,
            neighbors = edges,
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for Linear with degree not 2`() {
        val edge = EdgeData(targetId = "node-2", direction = "north")
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for Hub with degree less than 3`() {
        val edges = listOf(
            EdgeData(targetId = "node-2", direction = "north"),
            EdgeData(targetId = "node-3", direction = "south")
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            neighbors = edges,
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for duplicate edges`() {
        val edges = listOf(
            EdgeData(targetId = "node-2", direction = "north"),
            EdgeData(targetId = "node-2", direction = "north")
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            neighbors = edges,
            chunkId = "chunk-1"
        )

        assertFalse(node.validate())
    }

    @Test
    fun `validate returns false for empty chunk ID`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            chunkId = ""
        )

        assertFalse(node.validate())
    }

    @Test
    fun `multiple edges can be added`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            chunkId = "chunk-1"
        )

        val updated = node
            .addEdge(EdgeData(targetId = "node-2", direction = "north"))
            .addEdge(EdgeData(targetId = "node-3", direction = "south"))
            .addEdge(EdgeData(targetId = "node-4", direction = "east"))

        assertEquals(3, updated.neighbors.size)
        assertEquals(3, updated.degree())
    }

    @Test
    fun `position can be null for organic layouts`() {
        val edges = listOf(
            EdgeData(targetId = "node-2", direction = "north"),
            EdgeData(targetId = "node-3", direction = "south"),
            EdgeData(targetId = "node-4", direction = "east")
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            position = null,
            neighbors = edges,
            chunkId = "chunk-1"
        )

        assertNull(node.position)
        assertTrue(node.validate())
    }

    @Test
    fun `position can be set for grid layouts`() {
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Hub,
            position = Pair(5, 10),
            chunkId = "chunk-1"
        )

        assertNotNull(node.position)
        assertEquals(5, node.position!!.first)
        assertEquals(10, node.position!!.second)
    }

    // ==================== GEOMETRIC NAVIGATION TESTS ====================

    @Test
    fun `getEdgeGeometric returns exact match when label matches`() {
        val edge = EdgeData(
            targetId = "node-2",
            direction = "north",
            geometricAngle = 3 * Math.PI / 2, // north
            fromPosition = Pair(5, 5),
            toPosition = Pair(5, 4)
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            position = Pair(5, 5),
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val found = node.getEdgeGeometric("north")
        assertNotNull(found)
        assertEquals("node-2", found.targetId)
    }

    @Test
    fun `getEdgeGeometric uses geometric fallback when label doesnt match`() {
        // Edge labeled "passage-1" but geometrically points east
        val edge = EdgeData(
            targetId = "node-2",
            direction = "passage-1",
            geometricAngle = 0.0, // east
            fromPosition = Pair(5, 5),
            toPosition = Pair(6, 5)
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            position = Pair(5, 5),
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val found = node.getEdgeGeometric("east")
        assertNotNull(found)
        assertEquals("node-2", found.targetId)
    }

    @Test
    fun `getEdgeGeometric maintains spatial coherence for cardinal directions`() {
        // Test E, SE, S, SW, W, NW, N, NE
        val testCases = listOf(
            Triple("east", 0.0, Pair(6, 5)),
            Triple("southeast", Math.PI / 4, Pair(6, 6)),
            Triple("south", Math.PI / 2, Pair(5, 6)),
            Triple("southwest", 3 * Math.PI / 4, Pair(4, 6)),
            Triple("west", Math.PI, Pair(4, 5)),
            Triple("northwest", 5 * Math.PI / 4, Pair(4, 4)),
            Triple("north", 3 * Math.PI / 2, Pair(5, 4)),
            Triple("northeast", 7 * Math.PI / 4, Pair(6, 4))
        )

        for ((direction, angle, targetPos) in testCases) {
            val edge = EdgeData(
                targetId = "node-$direction",
                direction = "passage-1", // Wrong label
                geometricAngle = angle,
                fromPosition = Pair(5, 5),
                toPosition = targetPos
            )
            val node = GraphNodeComponent(
                id = "node-center",
                type = NodeType.Hub,
                position = Pair(5, 5),
                neighbors = listOf(edge),
                chunkId = "chunk-1"
            )

            val found = node.getEdgeGeometric(direction)
            assertNotNull(found, "Should find edge for $direction")
            assertEquals("node-$direction", found.targetId, "Wrong target for $direction")
        }
    }

    @Test
    fun `getEdgeGeometric returns null when no position available`() {
        val edge = EdgeData(
            targetId = "node-2",
            direction = "passage-1"
            // No geometric data
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            position = null, // No position
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val found = node.getEdgeGeometric("east")
        assertNull(found)
    }

    @Test
    fun `getEdgeGeometric returns null when angle difference too large`() {
        // Edge points north but we're looking for south
        val edge = EdgeData(
            targetId = "node-2",
            direction = "passage-1",
            geometricAngle = 3 * Math.PI / 2, // north
            fromPosition = Pair(5, 5),
            toPosition = Pair(5, 4)
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Linear,
            position = Pair(5, 5),
            neighbors = listOf(edge),
            chunkId = "chunk-1"
        )

        val found = node.getEdgeGeometric("south")
        assertNull(found, "Should not match opposite direction")
    }

    @Test
    fun `getEdgeGeometric selects closest angle when multiple edges`() {
        val edgeNorth = EdgeData(
            targetId = "node-north",
            direction = "passage-1",
            geometricAngle = 3 * Math.PI / 2, // north
            fromPosition = Pair(5, 5),
            toPosition = Pair(5, 4)
        )
        val edgeNortheast = EdgeData(
            targetId = "node-northeast",
            direction = "passage-2",
            geometricAngle = 7 * Math.PI / 4, // northeast
            fromPosition = Pair(5, 5),
            toPosition = Pair(6, 4)
        )
        val node = GraphNodeComponent(
            id = "node-1",
            type = NodeType.Branching,
            position = Pair(5, 5),
            neighbors = listOf(edgeNorth, edgeNortheast),
            chunkId = "chunk-1"
        )

        val foundNorth = node.getEdgeGeometric("north")
        assertNotNull(foundNorth)
        assertEquals("node-north", foundNorth.targetId)

        val foundNortheast = node.getEdgeGeometric("northeast")
        assertNotNull(foundNortheast)
        assertEquals("node-northeast", foundNortheast.targetId)
    }

    @Test
    fun `getEdgeGeometric supports spatial coherence for grid navigation`() {
        // Simulate: Start at (1,1), go E to (2,1), go S to (2,2)
        // From (2,2), should be able to go NW back to (1,1)

        // From position (2, 2), NW should point to (1, 1)
        // Angle from (2,2) to (1,1): dx=-1, dy=-1
        // atan2(-dy, dx) = atan2(1, -1) = 3π/4 (northwest in screen coords)
        // But we flip Y: northwest = 5π/4
        val edgeToOrigin = EdgeData(
            targetId = "origin",
            direction = "passage-3", // Wrong label
            geometricAngle = 5 * Math.PI / 4, // northwest
            fromPosition = Pair(2, 2),
            toPosition = Pair(1, 1)
        )
        val nodeAtTwoTwo = GraphNodeComponent(
            id = "node-2-2",
            type = NodeType.Branching,
            position = Pair(2, 2),
            neighbors = listOf(edgeToOrigin),
            chunkId = "chunk-1"
        )

        val found = nodeAtTwoTwo.getEdgeGeometric("northwest")
        assertNotNull(found, "Should find NW edge from (2,2) to (1,1)")
        assertEquals("origin", found.targetId)
    }
}
