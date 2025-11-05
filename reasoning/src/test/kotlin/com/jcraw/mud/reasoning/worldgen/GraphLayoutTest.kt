package com.jcraw.mud.reasoning.worldgen

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for GraphLayout - layout algorithm configuration
 *
 * Focus on:
 * - Validation constraints
 * - Helper methods (forBiome, forNodeCount)
 * - Edge case handling
 */
class GraphLayoutTest {

    // ==================== GRID LAYOUT ====================

    @Test
    fun `Grid layout calculates correct node count`() {
        val layout = GraphLayout.Grid(width = 6, height = 4)

        assertEquals(24, layout.nodeCount())
    }

    @Test
    fun `Grid layout validates positive dimensions`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.Grid(width = 0, height = 5)
        }

        assertThrows<IllegalArgumentException> {
            GraphLayout.Grid(width = 5, height = -1)
        }
    }

    @Test
    fun `Grid layout enforces max 100 nodes`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.Grid(width = 11, height = 10) // 110 nodes
        }

        // Should not throw
        val valid = GraphLayout.Grid(width = 10, height = 10) // 100 nodes
        assertEquals(100, valid.nodeCount())
    }

    @Test
    fun `Grid layout toString is readable`() {
        val layout = GraphLayout.Grid(width = 5, height = 7)

        assertEquals("Grid(5x7)", layout.toString())
    }

    // ==================== BSP LAYOUT ====================

    @Test
    fun `BSP layout estimates node count`() {
        val layout = GraphLayout.BSP(minRoomSize = 4, maxDepth = 3)

        val estimate = layout.estimateNodeCount()

        // 2^3 = 8 rooms minimum, plus some extra from minRoomSize
        assertTrue(estimate >= 8, "BSP should estimate at least 8 nodes for depth 3")
    }

    @Test
    fun `BSP layout validates minRoomSize`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.BSP(minRoomSize = 1, maxDepth = 3)
        }

        // Should not throw
        val valid = GraphLayout.BSP(minRoomSize = 2, maxDepth = 3)
        assertTrue(valid.estimateNodeCount() > 0)
    }

    @Test
    fun `BSP layout validates maxDepth range`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.BSP(minRoomSize = 3, maxDepth = 0)
        }

        assertThrows<IllegalArgumentException> {
            GraphLayout.BSP(minRoomSize = 3, maxDepth = 7)
        }

        // Should not throw
        val valid = GraphLayout.BSP(minRoomSize = 3, maxDepth = 6)
        assertTrue(valid.estimateNodeCount() > 0)
    }

    @Test
    fun `BSP layout toString is readable`() {
        val layout = GraphLayout.BSP(minRoomSize = 5, maxDepth = 4)

        assertEquals("BSP(minRoom=5, depth=4)", layout.toString())
    }

    // ==================== FLOOD-FILL LAYOUT ====================

    @Test
    fun `FloodFill layout accepts valid node count range`() {
        val small = GraphLayout.FloodFill(nodeCount = 5, density = 0.3)
        val large = GraphLayout.FloodFill(nodeCount = 100, density = 0.5)

        // Should not throw
        assertTrue(small.nodeCount >= 5)
        assertTrue(large.nodeCount <= 100)
    }

    @Test
    fun `FloodFill layout validates node count range`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.FloodFill(nodeCount = 4, density = 0.4) // Too small
        }

        assertThrows<IllegalArgumentException> {
            GraphLayout.FloodFill(nodeCount = 101, density = 0.4) // Too large
        }
    }

    @Test
    fun `FloodFill layout validates density range`() {
        assertThrows<IllegalArgumentException> {
            GraphLayout.FloodFill(nodeCount = 20, density = 0.05) // Too low
        }

        assertThrows<IllegalArgumentException> {
            GraphLayout.FloodFill(nodeCount = 20, density = 1.5) // Too high
        }

        // Should not throw
        val valid = GraphLayout.FloodFill(nodeCount = 20, density = 1.0)
        assertEquals(20, valid.nodeCount)
    }

    @Test
    fun `FloodFill layout toString is readable`() {
        val layout = GraphLayout.FloodFill(nodeCount = 30, density = 0.45)

        assertEquals("FloodFill(nodes=30, density=0.45)", layout.toString())
    }

    // ==================== BIOME SELECTION ====================

    @Test
    fun `forBiome selects Grid for dungeon theme`() {
        val layout = GraphLayout.forBiome("Ancient Dungeon")

        assertTrue(layout is GraphLayout.Grid, "Dungeon should use Grid layout")
    }

    @Test
    fun `forBiome selects BSP for building theme`() {
        val layout = GraphLayout.forBiome("Abandoned Building")

        assertTrue(layout is GraphLayout.BSP, "Building should use BSP layout")
    }

    @Test
    fun `forBiome selects BSP for temple theme`() {
        val layout = GraphLayout.forBiome("Lost Temple")

        assertTrue(layout is GraphLayout.BSP, "Temple should use BSP layout")
    }

    @Test
    fun `forBiome selects FloodFill for cave theme`() {
        val layout = GraphLayout.forBiome("Dark Caverns")

        assertTrue(layout is GraphLayout.FloodFill, "Cave should use FloodFill layout")
    }

    @Test
    fun `forBiome selects FloodFill for mine theme`() {
        val layout = GraphLayout.forBiome("Abandoned Mine")

        assertTrue(layout is GraphLayout.FloodFill, "Mine should use FloodFill layout")
    }

    @Test
    fun `forBiome selects FloodFill for forest theme`() {
        val layout = GraphLayout.forBiome("Enchanted Forest")

        assertTrue(layout is GraphLayout.FloodFill, "Forest should use FloodFill layout")
    }

    @Test
    fun `forBiome selects Grid for tower theme`() {
        val layout = GraphLayout.forBiome("Wizard's Tower")

        assertTrue(layout is GraphLayout.Grid, "Tower should use Grid layout")
        val grid = layout as GraphLayout.Grid
        assertTrue(grid.height > grid.width, "Tower should be taller than wide")
    }

    @Test
    fun `forBiome selects BSP for ruins theme`() {
        val layout = GraphLayout.forBiome("Ancient Ruins")

        assertTrue(layout is GraphLayout.BSP, "Ruins should use BSP layout")
    }

    @Test
    fun `forBiome defaults to Grid for unknown theme`() {
        val layout = GraphLayout.forBiome("Unknown Theme XYZ")

        assertTrue(layout is GraphLayout.Grid, "Unknown theme should default to Grid")
        assertEquals(GraphLayout.Grid(5, 5), layout)
    }

    @Test
    fun `forBiome is case-insensitive`() {
        val lowercase = GraphLayout.forBiome("dungeon")
        val uppercase = GraphLayout.forBiome("DUNGEON")
        val mixedCase = GraphLayout.forBiome("DuNgEoN")

        assertTrue(lowercase is GraphLayout.Grid)
        assertTrue(uppercase is GraphLayout.Grid)
        assertTrue(mixedCase is GraphLayout.Grid)
    }

    // ==================== NODE COUNT SELECTION ====================

    @Test
    fun `forNodeCount selects small Grid for 10 nodes`() {
        val layout = GraphLayout.forNodeCount(10)

        assertTrue(layout is GraphLayout.Grid)
        val grid = layout as GraphLayout.Grid
        assertEquals(9, grid.nodeCount(), "3x3 grid for small count")
    }

    @Test
    fun `forNodeCount selects medium Grid for 25 nodes`() {
        val layout = GraphLayout.forNodeCount(25)

        assertTrue(layout is GraphLayout.Grid)
        val grid = layout as GraphLayout.Grid
        assertEquals(25, grid.nodeCount(), "5x5 grid for medium count")
    }

    @Test
    fun `forNodeCount selects BSP for 50 nodes`() {
        val layout = GraphLayout.forNodeCount(50)

        assertTrue(layout is GraphLayout.BSP)
    }

    @Test
    fun `forNodeCount selects FloodFill for large counts`() {
        val layout = GraphLayout.forNodeCount(75)

        assertTrue(layout is GraphLayout.FloodFill)
        val floodFill = layout as GraphLayout.FloodFill
        assertEquals(75, floodFill.nodeCount)
    }

    @Test
    fun `forNodeCount clamps to max 100 nodes`() {
        val layout = GraphLayout.forNodeCount(200)

        assertTrue(layout is GraphLayout.FloodFill)
        val floodFill = layout as GraphLayout.FloodFill
        assertEquals(100, floodFill.nodeCount, "Should clamp to max 100")
    }

    @Test
    fun `forNodeCount handles minimum 5 nodes`() {
        val layout = GraphLayout.forNodeCount(3)

        assertTrue(layout is GraphLayout.Grid)
        val grid = layout as GraphLayout.Grid
        assertEquals(9, grid.nodeCount(), "Should use at least 3x3 grid")
    }
}
