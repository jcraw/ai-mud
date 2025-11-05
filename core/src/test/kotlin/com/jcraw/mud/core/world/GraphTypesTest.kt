package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for GraphTypes (NodeType sealed class and EdgeData)
 * Focus on sealed class exhaustiveness and EdgeData behavior
 */
class GraphTypesTest {

    @Test
    fun `NodeType Hub has correct string representation`() {
        val nodeType: NodeType = NodeType.Hub
        assertEquals("Hub", nodeType.toString())
    }

    @Test
    fun `NodeType Linear has correct string representation`() {
        val nodeType: NodeType = NodeType.Linear
        assertEquals("Linear", nodeType.toString())
    }

    @Test
    fun `NodeType Branching has correct string representation`() {
        val nodeType: NodeType = NodeType.Branching
        assertEquals("Branching", nodeType.toString())
    }

    @Test
    fun `NodeType DeadEnd has correct string representation`() {
        val nodeType: NodeType = NodeType.DeadEnd
        assertEquals("DeadEnd", nodeType.toString())
    }

    @Test
    fun `NodeType Boss has correct string representation`() {
        val nodeType: NodeType = NodeType.Boss
        assertEquals("Boss", nodeType.toString())
    }

    @Test
    fun `NodeType Frontier has correct string representation`() {
        val nodeType: NodeType = NodeType.Frontier
        assertEquals("Frontier", nodeType.toString())
    }

    @Test
    fun `NodeType Questable has correct string representation`() {
        val nodeType: NodeType = NodeType.Questable
        assertEquals("Questable", nodeType.toString())
    }

    @Test
    fun `NodeType when expression is exhaustive`() {
        val nodeTypes = listOf(
            NodeType.Hub,
            NodeType.Linear,
            NodeType.Branching,
            NodeType.DeadEnd,
            NodeType.Boss,
            NodeType.Frontier,
            NodeType.Questable
        )

        // Test that we can handle all node types in when expression
        nodeTypes.forEach { nodeType ->
            val handled = when (nodeType) {
                NodeType.Hub -> "hub"
                NodeType.Linear -> "linear"
                NodeType.Branching -> "branching"
                NodeType.DeadEnd -> "deadend"
                NodeType.Boss -> "boss"
                NodeType.Frontier -> "frontier"
                NodeType.Questable -> "questable"
            }
            assertTrue(handled.isNotEmpty())
        }
    }

    @Test
    fun `EdgeData creates with required fields`() {
        val edge = EdgeData(
            targetId = "node-2",
            direction = "north"
        )

        assertEquals("node-2", edge.targetId)
        assertEquals("north", edge.direction)
        assertFalse(edge.hidden)
        assertTrue(edge.conditions.isEmpty())
    }

    @Test
    fun `EdgeData can be created with hidden flag`() {
        val edge = EdgeData(
            targetId = "node-2",
            direction = "north",
            hidden = true
        )

        assertTrue(edge.hidden)
    }

    @Test
    fun `EdgeData can be created with conditions`() {
        val condition = Condition.SkillCheck("Perception", 15)
        val edge = EdgeData(
            targetId = "node-2",
            direction = "north",
            conditions = listOf(condition)
        )

        assertEquals(1, edge.conditions.size)
        assertEquals("Perception", (edge.conditions[0] as Condition.SkillCheck).skill)
        assertEquals(15, (edge.conditions[0] as Condition.SkillCheck).difficulty)
    }

    @Test
    fun `EdgeData edgeId generates correct format`() {
        val edge = EdgeData(
            targetId = "node-2",
            direction = "north"
        )

        val edgeId = edge.edgeId("node-1")
        assertEquals("node-1->node-2", edgeId)
    }

    @Test
    fun `EdgeData edgeId is unique per source-target pair`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-3", direction = "south")

        val id1 = edge1.edgeId("node-1")
        val id2 = edge2.edgeId("node-1")

        assertTrue(id1 != id2)
    }

    @Test
    fun `EdgeData with skill check condition`() {
        val condition = Condition.SkillCheck("Agility", 12)
        val edge = EdgeData(
            targetId = "node-2",
            direction = "narrow ledge",
            hidden = false,
            conditions = listOf(condition)
        )

        assertEquals(1, edge.conditions.size)
        assertFalse(edge.hidden)
        assertEquals("narrow ledge", edge.direction)
    }

    @Test
    fun `EdgeData with item required condition`() {
        val condition = Condition.ItemRequired("climbing_gear")
        val edge = EdgeData(
            targetId = "node-2",
            direction = "cliff face",
            conditions = listOf(condition)
        )

        assertEquals(1, edge.conditions.size)
        assertEquals("climbing_gear", (edge.conditions[0] as Condition.ItemRequired).itemTag)
    }

    @Test
    fun `EdgeData with multiple conditions`() {
        val conditions = listOf(
            Condition.SkillCheck("Agility", 15),
            Condition.ItemRequired("rope")
        )
        val edge = EdgeData(
            targetId = "node-2",
            direction = "dangerous path",
            conditions = conditions
        )

        assertEquals(2, edge.conditions.size)
    }

    @Test
    fun `EdgeData is immutable data class`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = edge1.copy(hidden = true)

        assertFalse(edge1.hidden)
        assertTrue(edge2.hidden)
        assertEquals(edge1.targetId, edge2.targetId)
        assertEquals(edge1.direction, edge2.direction)
    }

    @Test
    fun `EdgeData equality works correctly`() {
        val edge1 = EdgeData(targetId = "node-2", direction = "north")
        val edge2 = EdgeData(targetId = "node-2", direction = "north")
        val edge3 = EdgeData(targetId = "node-3", direction = "north")

        assertEquals(edge1, edge2)
        assertTrue(edge1 != edge3)
    }

    @Test
    fun `hidden EdgeData with high difficulty condition`() {
        val condition = Condition.SkillCheck("Perception", 25)
        val edge = EdgeData(
            targetId = "node-secret",
            direction = "hidden passage",
            hidden = true,
            conditions = listOf(condition)
        )

        assertTrue(edge.hidden)
        assertEquals(25, (edge.conditions[0] as Condition.SkillCheck).difficulty)
    }

    @Test
    fun `EdgeData direction can be cardinal`() {
        val edges = listOf(
            EdgeData(targetId = "n", direction = "north"),
            EdgeData(targetId = "s", direction = "south"),
            EdgeData(targetId = "e", direction = "east"),
            EdgeData(targetId = "w", direction = "west"),
            EdgeData(targetId = "u", direction = "up"),
            EdgeData(targetId = "d", direction = "down")
        )

        assertEquals(6, edges.size)
        assertTrue(edges.all { it.direction.isNotEmpty() })
    }

    @Test
    fun `EdgeData direction can be descriptive`() {
        val edges = listOf(
            EdgeData(targetId = "n", direction = "narrow corridor"),
            EdgeData(targetId = "n", direction = "spiral staircase"),
            EdgeData(targetId = "n", direction = "hidden passage"),
            EdgeData(targetId = "n", direction = "crumbling bridge"),
            EdgeData(targetId = "n", direction = "glowing portal")
        )

        assertEquals(5, edges.size)
        assertTrue(edges.all { it.direction.length > 5 })
    }

    @Test
    fun `EdgeData can represent tunnel created by player`() {
        val edge = EdgeData(
            targetId = "node-new",
            direction = "tunnel north",
            hidden = false,
            conditions = emptyList()
        )

        assertEquals("tunnel north", edge.direction)
        assertFalse(edge.hidden)
        assertTrue(edge.conditions.isEmpty())
    }

    @Test
    fun `EdgeData can represent breakout edge to new biome`() {
        val condition = Condition.SkillCheck("Agility", 12)
        val edge = EdgeData(
            targetId = "region-caverns-1",
            direction = "unstable wall",
            hidden = false,
            conditions = listOf(condition)
        )

        assertEquals("unstable wall", edge.direction)
        assertEquals(1, edge.conditions.size)
    }

    @Test
    fun `NodeType instances are singletons`() {
        val hub1 = NodeType.Hub
        val hub2 = NodeType.Hub

        assertTrue(hub1 === hub2)
    }
}
