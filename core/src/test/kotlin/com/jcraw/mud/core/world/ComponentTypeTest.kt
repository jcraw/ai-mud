package com.jcraw.mud.core.world

import com.jcraw.mud.core.ComponentType
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Tests for ComponentType enum with world generation types
 */
class ComponentTypeTest {

    @Test
    fun `enum includes WORLD_CHUNK type`() {
        val types = ComponentType.entries
        assertTrue(types.contains(ComponentType.WORLD_CHUNK))
    }

    @Test
    fun `enum includes SPACE_PROPERTIES type`() {
        val types = ComponentType.entries
        assertTrue(types.contains(ComponentType.SPACE_PROPERTIES))
    }

    @Test
    fun `no duplicate values in enum`() {
        val types = ComponentType.entries
        val uniqueTypes = types.toSet()
        assertEquals(types.size, uniqueTypes.size)
    }

    @Test
    fun `WORLD_CHUNK has correct name`() {
        assertEquals("WORLD_CHUNK", ComponentType.WORLD_CHUNK.name)
    }

    @Test
    fun `SPACE_PROPERTIES has correct name`() {
        assertEquals("SPACE_PROPERTIES", ComponentType.SPACE_PROPERTIES.name)
    }
}
