package com.jcraw.mud.core.world

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ResourceNode
 */
class ResourceNodeTest {

    @Test
    fun `harvest reduces quantity`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5
        )
        val (updated, harvested) = node.harvest(2)
        assertEquals(3, updated.quantity)
        assertEquals(2, harvested)
    }

    @Test
    fun `harvest cannot exceed available quantity`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 3
        )
        val (updated, harvested) = node.harvest(5)
        assertEquals(0, updated.quantity)
        assertEquals(3, harvested)
    }

    @Test
    fun `harvest default amount is 1`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5
        )
        val (updated, harvested) = node.harvest()
        assertEquals(4, updated.quantity)
        assertEquals(1, harvested)
    }

    @Test
    fun `depleted node returns true for isDepleted`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 0
        )
        assertTrue(node.isDepleted())
    }

    @Test
    fun `node with quantity is not depleted`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5
        )
        assertFalse(node.isDepleted())
    }

    @Test
    fun `node with respawn time can respawn`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5,
            respawnTime = 100
        )
        assertTrue(node.canRespawn())
    }

    @Test
    fun `node without respawn time cannot respawn`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5,
            respawnTime = null
        )
        assertFalse(node.canRespawn())
    }

    @Test
    fun `tick increments time since harvest when depleted`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 0,
            respawnTime = 100,
            timeSinceHarvest = 0
        )
        val (updated, respawned) = node.tick(10)
        assertEquals(10, updated.timeSinceHarvest)
        assertFalse(respawned)
    }

    @Test
    fun `tick does not increment time when node has quantity`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 5,
            respawnTime = 100,
            timeSinceHarvest = 0
        )
        val (updated, respawned) = node.tick(10)
        assertEquals(0, updated.timeSinceHarvest)
        assertFalse(respawned)
    }

    @Test
    fun `tick does not increment time when respawn time is null`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 0,
            respawnTime = null,
            timeSinceHarvest = 0
        )
        val (updated, respawned) = node.tick(10)
        assertEquals(0, updated.timeSinceHarvest)
        assertFalse(respawned)
    }

    @Test
    fun `node respawns when time reaches respawn time`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 0,
            respawnTime = 100,
            timeSinceHarvest = 95
        )
        val (updated, respawned) = node.tick(5)
        assertTrue(respawned)
        assertTrue(updated.quantity > 0)
        assertEquals(0, updated.timeSinceHarvest)
    }

    @Test
    fun `harvest on depleted renewable node resets timer`() {
        val node = ResourceNode(
            id = "node-1",
            templateId = "iron_ore",
            quantity = 1,
            respawnTime = 100,
            timeSinceHarvest = 50
        )
        val (updated, harvested) = node.harvest(1)
        assertEquals(0, updated.quantity)
        assertEquals(0, updated.timeSinceHarvest)
    }
}
