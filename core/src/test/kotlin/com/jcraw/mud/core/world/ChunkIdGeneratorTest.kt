package com.jcraw.mud.core.world

import com.jcraw.mud.core.world.ChunkLevel
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ChunkIdGenerator - hierarchical ID generation for world chunks.
 *
 * Focus on format validation, uniqueness, and parsing correctness.
 */
class ChunkIdGeneratorTest {

    @Test
    fun `generate WORLD level returns special root ID`() {
        val id = ChunkIdGenerator.generate(ChunkLevel.WORLD, null)

        assertEquals("WORLD_root", id)
    }

    @Test
    fun `generate REGION level includes parent and UUID`() {
        val parentId = "WORLD_root"
        val id = ChunkIdGenerator.generate(ChunkLevel.REGION, parentId)

        assertTrue(id.startsWith("REGION_WORLD_root_"))
        assertTrue(id.split("_").last().length == 8) // UUID truncated to 8 chars
    }

    @Test
    fun `generate ZONE level with null parent uses orphan placeholder`() {
        val id = ChunkIdGenerator.generate(ChunkLevel.ZONE, null)

        assertTrue(id.startsWith("ZONE_orphan_"))
    }

    @Test
    fun `generate produces unique IDs for same inputs`() {
        val parentId = "REGION_abc_123"
        val id1 = ChunkIdGenerator.generate(ChunkLevel.ZONE, parentId)
        val id2 = ChunkIdGenerator.generate(ChunkLevel.ZONE, parentId)

        assertNotEquals(id1, id2, "UUIDs should make IDs unique")
    }

    @Test
    fun `parse extracts correct ChunkLevel from ID`() {
        val id = "SPACE_subzone123_a1b2c3d4"

        val level = ChunkIdGenerator.parse(id)

        assertEquals(ChunkLevel.SPACE, level)
    }

    @Test
    fun `parse returns null for invalid format`() {
        val invalidId = "INVALID_FORMAT"

        val level = ChunkIdGenerator.parse(invalidId)

        assertNull(level)
    }

    @Test
    fun `parse returns null for empty string`() {
        val level = ChunkIdGenerator.parse("")

        assertNull(level)
    }

    @Test
    fun `extractParentId returns null for WORLD level`() {
        val worldId = "WORLD_root"

        val parentId = ChunkIdGenerator.extractParentId(worldId)

        assertNull(parentId)
    }

    @Test
    fun `extractParentId returns correct parent for nested hierarchy`() {
        // ID format: SPACE_SUBZONE_parent123_uuid
        val id = "SPACE_SUBZONE_parent123_a1b2c3d4"

        val parentId = ChunkIdGenerator.extractParentId(id)

        assertEquals("SUBZONE_parent123", parentId)
    }

    @Test
    fun `extractParentId handles complex parent IDs with multiple underscores`() {
        // Parent ID itself contains underscores (e.g., REGION_WORLD_root)
        val id = "ZONE_REGION_WORLD_root_xyz_a1b2c3d4"

        val parentId = ChunkIdGenerator.extractParentId(id)

        assertEquals("REGION_WORLD_root_xyz", parentId, "Should join all parts except level and final UUID")
    }
}
