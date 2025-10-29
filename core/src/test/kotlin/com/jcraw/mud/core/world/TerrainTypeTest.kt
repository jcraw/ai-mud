package com.jcraw.mud.core.world

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for TerrainType enum
 */
class TerrainTypeTest {

    @Test
    fun `NORMAL has time cost multiplier 1_0`() {
        assertEquals(1.0, TerrainType.NORMAL.timeCostMultiplier)
    }

    @Test
    fun `NORMAL has no damage risk`() {
        assertEquals(0, TerrainType.NORMAL.damageRisk)
    }

    @Test
    fun `DIFFICULT has time cost multiplier 2_0`() {
        assertEquals(2.0, TerrainType.DIFFICULT.timeCostMultiplier)
    }

    @Test
    fun `DIFFICULT has damage risk 6`() {
        assertEquals(6, TerrainType.DIFFICULT.damageRisk)
    }

    @Test
    fun `IMPASSABLE has time cost multiplier 0_0`() {
        assertEquals(0.0, TerrainType.IMPASSABLE.timeCostMultiplier)
    }

    @Test
    fun `IMPASSABLE has no damage risk`() {
        assertEquals(0, TerrainType.IMPASSABLE.damageRisk)
    }

    @Test
    fun `NORMAL is passable`() {
        assertTrue(TerrainType.NORMAL.isPassable())
    }

    @Test
    fun `DIFFICULT is passable`() {
        assertTrue(TerrainType.DIFFICULT.isPassable())
    }

    @Test
    fun `IMPASSABLE is not passable`() {
        assertFalse(TerrainType.IMPASSABLE.isPassable())
    }
}
