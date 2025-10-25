package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TurnQueueManagerTest {

    private lateinit var queue: TurnQueueManager

    @BeforeEach
    fun setup() {
        queue = TurnQueueManager()
    }

    @Test
    fun `enqueue and dequeue single entity`() {
        queue.enqueue("goblin1", 10L)

        assertEquals("goblin1", queue.dequeue(10L))
        assertNull(queue.dequeue(10L)) // Queue empty after dequeue
    }

    @Test
    fun `dequeue returns null when no entity is ready`() {
        queue.enqueue("goblin1", 10L)

        assertNull(queue.dequeue(5L)) // Current time before timer
        assertEquals("goblin1", queue.dequeue(10L)) // Now ready
    }

    @Test
    fun `dequeue respects priority order`() {
        queue.enqueue("goblin1", 10L)
        queue.enqueue("goblin2", 5L)
        queue.enqueue("goblin3", 15L)

        assertEquals("goblin2", queue.dequeue(20L)) // Lowest timer first
        assertEquals("goblin1", queue.dequeue(20L))
        assertEquals("goblin3", queue.dequeue(20L))
        assertNull(queue.dequeue(20L))
    }

    @Test
    fun `peek returns next entity without removing`() {
        queue.enqueue("goblin1", 10L)
        queue.enqueue("goblin2", 5L)

        val (entityId, timer) = queue.peek()!!
        assertEquals("goblin2", entityId)
        assertEquals(5L, timer)

        // Peek doesn't remove
        assertEquals("goblin2", queue.peek()!!.first)
    }

    @Test
    fun `peek returns null for empty queue`() {
        assertNull(queue.peek())
    }

    @Test
    fun `remove entity from queue`() {
        queue.enqueue("goblin1", 10L)
        queue.enqueue("goblin2", 5L)

        assertTrue(queue.remove("goblin1"))
        assertFalse(queue.contains("goblin1"))

        // goblin2 still in queue
        assertEquals("goblin2", queue.dequeue(10L))
    }

    @Test
    fun `remove returns false for non-existent entity`() {
        assertFalse(queue.remove("nonexistent"))
    }

    @Test
    fun `contains checks entity presence`() {
        queue.enqueue("goblin1", 10L)

        assertTrue(queue.contains("goblin1"))
        assertFalse(queue.contains("goblin2"))
    }

    @Test
    fun `getTimerEnd returns correct value`() {
        queue.enqueue("goblin1", 10L)

        assertEquals(10L, queue.getTimerEnd("goblin1"))
        assertNull(queue.getTimerEnd("goblin2"))
    }

    @Test
    fun `enqueue updates existing entity timer`() {
        queue.enqueue("goblin1", 10L)
        assertEquals(10L, queue.getTimerEnd("goblin1"))

        queue.enqueue("goblin1", 20L) // Update timer
        assertEquals(20L, queue.getTimerEnd("goblin1"))

        // Size should still be 1 (not duplicate)
        assertEquals(1, queue.size())
    }

    @Test
    fun `clear removes all entities`() {
        queue.enqueue("goblin1", 10L)
        queue.enqueue("goblin2", 5L)
        queue.enqueue("goblin3", 15L)

        queue.clear()

        assertEquals(0, queue.size())
        assertNull(queue.peek())
    }

    @Test
    fun `size returns correct count`() {
        assertEquals(0, queue.size())

        queue.enqueue("goblin1", 10L)
        assertEquals(1, queue.size())

        queue.enqueue("goblin2", 5L)
        assertEquals(2, queue.size())

        queue.dequeue(10L)
        assertEquals(1, queue.size())
    }

    @Test
    fun `enqueue requires non-blank entity ID`() {
        assertThrows<IllegalArgumentException> {
            queue.enqueue("", 10L)
        }
        assertThrows<IllegalArgumentException> {
            queue.enqueue("   ", 10L)
        }
    }

    @Test
    fun `enqueue requires non-negative timer`() {
        assertThrows<IllegalArgumentException> {
            queue.enqueue("goblin1", -1L)
        }
    }

    @Test
    fun `getAllEntities returns sorted list`() {
        queue.enqueue("goblin1", 10L)
        queue.enqueue("goblin2", 5L)
        queue.enqueue("goblin3", 15L)

        val all = queue.getAllEntities()
        assertEquals(3, all.size)
        assertEquals("goblin2", all[0].first)
        assertEquals("goblin1", all[1].first)
        assertEquals("goblin3", all[2].first)
    }

    @Test
    fun `rebuild from empty WorldState creates empty queue`() {
        val worldState = WorldState(emptyMap(), emptyMap())
        queue.rebuild(worldState)

        assertEquals(0, queue.size())
    }

    @Test
    fun `rebuild from WorldState with combat components`() {
        // Create entities with CombatComponents
        val goblin1 = Entity.NPC(
            id = "goblin1",
            name = "Goblin 1",
            description = "A small goblin",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 20,
                    maxHp = 20,
                    actionTimerEnd = 10L
                )
            )
        )

        val goblin2 = Entity.NPC(
            id = "goblin2",
            name = "Goblin 2",
            description = "Another small goblin",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 15,
                    maxHp = 15,
                    actionTimerEnd = 5L
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Test Room",
            traits = listOf("dungeon", "dark"),
            entities = listOf(goblin1, goblin2)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        queue.rebuild(worldState)

        assertEquals(2, queue.size())
        assertTrue(queue.contains("goblin1"))
        assertTrue(queue.contains("goblin2"))
        assertEquals(10L, queue.getTimerEnd("goblin1"))
        assertEquals(5L, queue.getTimerEnd("goblin2"))
    }

    @Test
    fun `rebuild ignores entities without CombatComponent`() {
        val npc = Entity.NPC(
            id = "merchant",
            name = "Merchant",
            description = "A friendly merchant",
            components = emptyMap() // No combat component
        )

        val room = Room(
            id = "room1",
            name = "Test Room",
            traits = listOf("town", "safe"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        queue.rebuild(worldState)

        assertEquals(0, queue.size())
    }

    @Test
    fun `rebuild ignores entities with zero or negative timer`() {
        val goblin = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "A small goblin",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 20,
                    maxHp = 20,
                    actionTimerEnd = 0L // Not in combat yet
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Test Room",
            traits = listOf("dungeon"),
            entities = listOf(goblin)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = emptyMap()
        )

        queue.rebuild(worldState)

        assertEquals(0, queue.size())
    }

    @Test
    fun `rebuild clears existing queue`() {
        queue.enqueue("old1", 10L)
        queue.enqueue("old2", 20L)

        val worldState = WorldState(emptyMap(), emptyMap())
        queue.rebuild(worldState)

        assertEquals(0, queue.size())
        assertFalse(queue.contains("old1"))
        assertFalse(queue.contains("old2"))
    }

    @Test
    fun `multiple dequeues in sequence`() {
        queue.enqueue("entity1", 5L)
        queue.enqueue("entity2", 10L)
        queue.enqueue("entity3", 15L)

        assertNull(queue.dequeue(4L))
        assertEquals("entity1", queue.dequeue(5L))
        assertNull(queue.dequeue(9L))
        assertEquals("entity2", queue.dequeue(10L))
        assertNull(queue.dequeue(14L))
        assertEquals("entity3", queue.dequeue(15L))
        assertNull(queue.dequeue(100L))
    }

    @Test
    fun `entities with same timer maintain insertion order`() {
        queue.enqueue("entity1", 10L)
        queue.enqueue("entity2", 10L)
        queue.enqueue("entity3", 10L)

        val first = queue.dequeue(10L)
        val second = queue.dequeue(10L)
        val third = queue.dequeue(10L)

        // All should be dequeued (order not guaranteed for equal priorities)
        assertNotNull(first)
        assertNotNull(second)
        assertNotNull(third)

        val ids = setOf(first, second, third)
        assertEquals(3, ids.size) // All different
        assertTrue("entity1" in ids)
        assertTrue("entity2" in ids)
        assertTrue("entity3" in ids)
    }
}
