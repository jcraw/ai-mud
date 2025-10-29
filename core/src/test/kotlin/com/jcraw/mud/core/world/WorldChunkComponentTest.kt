package com.jcraw.mud.core.world

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.WorldChunkComponent
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for WorldChunkComponent
 */
class WorldChunkComponentTest {

    @Test
    fun `component type is WORLD_CHUNK`() {
        val chunk = WorldChunkComponent(level = ChunkLevel.WORLD)
        assertEquals(ComponentType.WORLD_CHUNK, chunk.componentType)
    }

    @Test
    fun `addChild adds child ID`() {
        val chunk = WorldChunkComponent(level = ChunkLevel.WORLD)
        val updated = chunk.addChild("child-1")
        assertTrue(updated.children.contains("child-1"))
    }

    @Test
    fun `addChild is immutable operation`() {
        val chunk = WorldChunkComponent(level = ChunkLevel.WORLD)
        val updated = chunk.addChild("child-1")
        assertTrue(chunk.children.isEmpty())
        assertTrue(updated.children.contains("child-1"))
    }

    @Test
    fun `addChild fails for SPACE level`() {
        val chunk = WorldChunkComponent(level = ChunkLevel.SPACE)
        try {
            chunk.addChild("child-1")
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Cannot add children to SPACE"))
        }
    }

    @Test
    fun `removeChild removes child ID`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            children = listOf("child-1", "child-2")
        )
        val updated = chunk.removeChild("child-1")
        assertFalse(updated.children.contains("child-1"))
        assertTrue(updated.children.contains("child-2"))
    }

    @Test
    fun `validate returns true for valid WORLD chunk`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = null,
            children = listOf("child-1"),
            mobDensity = 0.5,
            difficultyLevel = 5,
            sizeEstimate = 10
        )
        assertTrue(chunk.validate())
    }

    @Test
    fun `validate returns false for SPACE with children`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.SPACE,
            children = listOf("child-1")
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for non-WORLD without parent`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = null
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for WORLD with parent`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            parentId = "parent-1"
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for negative mob density`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            mobDensity = -0.1
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for mob density greater than 1`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            mobDensity = 1.1
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for difficulty less than 1`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            difficultyLevel = 0
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `validate returns false for size estimate less than 1`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            sizeEstimate = 0
        )
        assertFalse(chunk.validate())
    }

    @Test
    fun `withInheritedLore updates lore`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.REGION,
            parentId = "world-1",
            lore = "Old lore"
        )
        val updated = chunk.withInheritedLore("New lore with variations")
        assertEquals("New lore with variations", updated.lore)
    }

    @Test
    fun `withTheme updates theme`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.ZONE,
            parentId = "region-1",
            biomeTheme = "forest"
        )
        val updated = chunk.withTheme("dark forest")
        assertEquals("dark forest", updated.biomeTheme)
    }

    @Test
    fun `validate accepts mob density exactly 0`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            mobDensity = 0.0
        )
        assertTrue(chunk.validate())
    }

    @Test
    fun `validate accepts mob density exactly 1`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            mobDensity = 1.0
        )
        assertTrue(chunk.validate())
    }

    @Test
    fun `validate accepts difficulty exactly 1`() {
        val chunk = WorldChunkComponent(
            level = ChunkLevel.WORLD,
            difficultyLevel = 1
        )
        assertTrue(chunk.validate())
    }

    @Test
    fun `multiple children can be added`() {
        val chunk = WorldChunkComponent(level = ChunkLevel.WORLD)
        val updated = chunk
            .addChild("child-1")
            .addChild("child-2")
            .addChild("child-3")
        assertEquals(3, updated.children.size)
        assertTrue(updated.children.containsAll(listOf("child-1", "child-2", "child-3")))
    }
}
