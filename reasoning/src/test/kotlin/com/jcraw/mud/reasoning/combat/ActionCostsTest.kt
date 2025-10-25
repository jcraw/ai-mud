package com.jcraw.mud.reasoning.combat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ActionCostsTest {

    @Test
    fun `base costs are defined correctly`() {
        assertEquals(6, ActionCosts.MELEE_ATTACK)
        assertEquals(5, ActionCosts.RANGED_ATTACK)
        assertEquals(8, ActionCosts.SPELL_CAST)
        assertEquals(4, ActionCosts.ITEM_USE)
        assertEquals(10, ActionCosts.MOVE)
        assertEquals(3, ActionCosts.SOCIAL)
        assertEquals(4, ActionCosts.DEFEND)
        assertEquals(5, ActionCosts.HIDE)
        assertEquals(6, ActionCosts.FLEE)
    }

    @Test
    fun `calculateCost with zero speed returns base cost`() {
        assertEquals(6L, ActionCosts.calculateCost(6, 0))
        assertEquals(10L, ActionCosts.calculateCost(10, 0))
    }

    @Test
    fun `calculateCost with speed level 10 reduces cost by half`() {
        // Speed 10 = 1 + (10/10) = 2.0x multiplier
        // Base 6 / 2 = 3
        assertEquals(3L, ActionCosts.calculateCost(6, 10))
        // Base 10 / 2 = 5
        assertEquals(5L, ActionCosts.calculateCost(10, 10))
    }

    @Test
    fun `calculateCost with speed level 50 provides significant reduction`() {
        // Speed 50 = 1 + (50/10) = 6.0x multiplier
        // Base 6 / 6 = 1, but minimum is 2
        assertEquals(2L, ActionCosts.calculateCost(6, 50))
        // Base 18 / 6 = 3
        assertEquals(3L, ActionCosts.calculateCost(18, 50))
    }

    @Test
    fun `calculateCost enforces minimum of 2 ticks`() {
        // Even with very high speed, minimum is 2
        assertEquals(2L, ActionCosts.calculateCost(2, 100))
        assertEquals(2L, ActionCosts.calculateCost(4, 100))
    }

    @Test
    fun `calculateCost with speed level 100 applies 11x multiplier`() {
        // Speed 100 = 1 + (100/10) = 11.0x multiplier
        // Base 22 / 11 = 2
        assertEquals(2L, ActionCosts.calculateCost(22, 100))
        // Base 33 / 11 = 3
        assertEquals(3L, ActionCosts.calculateCost(33, 100))
    }

    @Test
    fun `calculateCost requires positive base cost`() {
        assertThrows<IllegalArgumentException> {
            ActionCosts.calculateCost(0, 10)
        }
        assertThrows<IllegalArgumentException> {
            ActionCosts.calculateCost(-5, 10)
        }
    }

    @Test
    fun `calculateCost requires non-negative speed level`() {
        assertThrows<IllegalArgumentException> {
            ActionCosts.calculateCost(6, -1)
        }
    }

    @Test
    fun `calculateCost handles boundary cases`() {
        // Speed 0 (slowest)
        assertEquals(6L, ActionCosts.calculateCost(6, 0))

        // Speed 1 (slightly faster)
        // 1 + (1/10) = 1.1x multiplier
        // 6 / 1.1 = 5.45... -> 5
        assertEquals(5L, ActionCosts.calculateCost(6, 1))

        // Large base cost
        assertEquals(50L, ActionCosts.calculateCost(100, 10))
    }
}
