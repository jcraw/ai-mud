package com.jcraw.mud.core.world

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ChunkLevel enum
 */
class ChunkLevelTest {

    @Test
    fun `WORLD has depth 0`() {
        assertEquals(0, ChunkLevel.WORLD.depth)
    }

    @Test
    fun `REGION has depth 1`() {
        assertEquals(1, ChunkLevel.REGION.depth)
    }

    @Test
    fun `ZONE has depth 2`() {
        assertEquals(2, ChunkLevel.ZONE.depth)
    }

    @Test
    fun `SUBZONE has depth 3`() {
        assertEquals(3, ChunkLevel.SUBZONE.depth)
    }

    @Test
    fun `SPACE has depth 4`() {
        assertEquals(4, ChunkLevel.SPACE.depth)
    }

    @Test
    fun `depths are ordered correctly`() {
        assertTrue(ChunkLevel.WORLD.depth < ChunkLevel.REGION.depth)
        assertTrue(ChunkLevel.REGION.depth < ChunkLevel.ZONE.depth)
        assertTrue(ChunkLevel.ZONE.depth < ChunkLevel.SUBZONE.depth)
        assertTrue(ChunkLevel.SUBZONE.depth < ChunkLevel.SPACE.depth)
    }

    @Test
    fun `SPACE cannot have children`() {
        assertFalse(ChunkLevel.SPACE.canHaveChildren())
    }

    @Test
    fun `WORLD can have children`() {
        assertTrue(ChunkLevel.WORLD.canHaveChildren())
    }

    @Test
    fun `WORLD can be parent of REGION`() {
        assertTrue(ChunkLevel.WORLD.canBeParentOf(ChunkLevel.REGION))
    }

    @Test
    fun `WORLD cannot be parent of ZONE`() {
        assertFalse(ChunkLevel.WORLD.canBeParentOf(ChunkLevel.ZONE))
    }

    @Test
    fun `REGION can be parent of ZONE`() {
        assertTrue(ChunkLevel.REGION.canBeParentOf(ChunkLevel.ZONE))
    }

    @Test
    fun `SPACE cannot be parent of any level`() {
        assertFalse(ChunkLevel.SPACE.canBeParentOf(ChunkLevel.WORLD))
        assertFalse(ChunkLevel.SPACE.canBeParentOf(ChunkLevel.REGION))
        assertFalse(ChunkLevel.SPACE.canBeParentOf(ChunkLevel.ZONE))
        assertFalse(ChunkLevel.SPACE.canBeParentOf(ChunkLevel.SUBZONE))
        assertFalse(ChunkLevel.SPACE.canBeParentOf(ChunkLevel.SPACE))
    }
}
