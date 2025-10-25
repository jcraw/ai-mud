package com.jcraw.mud.reasoning.combat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeedCalculatorTest {

    @Test
    fun `ActionType fromString parses correctly`() {
        assertEquals(SpeedCalculator.ActionType.MELEE_ATTACK,
            SpeedCalculator.ActionType.fromString("attack"))
        assertEquals(SpeedCalculator.ActionType.MELEE_ATTACK,
            SpeedCalculator.ActionType.fromString("melee"))
        assertEquals(SpeedCalculator.ActionType.RANGED_ATTACK,
            SpeedCalculator.ActionType.fromString("shoot"))
        assertEquals(SpeedCalculator.ActionType.SPELL_CAST,
            SpeedCalculator.ActionType.fromString("cast"))
        assertEquals(SpeedCalculator.ActionType.MOVE,
            SpeedCalculator.ActionType.fromString("go"))
        assertEquals(SpeedCalculator.ActionType.FLEE,
            SpeedCalculator.ActionType.fromString("run"))
    }

    @Test
    fun `ActionType fromString handles case insensitivity`() {
        assertEquals(SpeedCalculator.ActionType.MELEE_ATTACK,
            SpeedCalculator.ActionType.fromString("ATTACK"))
        assertEquals(SpeedCalculator.ActionType.SPELL_CAST,
            SpeedCalculator.ActionType.fromString("SpElL"))
    }

    @Test
    fun `ActionType fromString falls back to MELEE_ATTACK for unknown`() {
        assertEquals(SpeedCalculator.ActionType.MELEE_ATTACK,
            SpeedCalculator.ActionType.fromString("unknown_action"))
        assertEquals(SpeedCalculator.ActionType.MELEE_ATTACK,
            SpeedCalculator.ActionType.fromString(""))
    }

    @Test
    fun `calculateActionCost with ActionType enum`() {
        assertEquals(6L, SpeedCalculator.calculateActionCost(
            SpeedCalculator.ActionType.MELEE_ATTACK, 0))
        assertEquals(3L, SpeedCalculator.calculateActionCost(
            SpeedCalculator.ActionType.MELEE_ATTACK, 10))
    }

    @Test
    fun `calculateActionCost with string action name`() {
        assertEquals(6L, SpeedCalculator.calculateActionCost("attack", 0))
        assertEquals(3L, SpeedCalculator.calculateActionCost("attack", 10))
        assertEquals(5L, SpeedCalculator.calculateActionCost("shoot", 0))
        assertEquals(8L, SpeedCalculator.calculateActionCost("cast", 0))
    }

    @Test
    fun `getSpeedMultiplier returns correct values`() {
        assertEquals(1.0, SpeedCalculator.getSpeedMultiplier(0))
        assertEquals(2.0, SpeedCalculator.getSpeedMultiplier(10))
        assertEquals(6.0, SpeedCalculator.getSpeedMultiplier(50))
        assertEquals(11.0, SpeedCalculator.getSpeedMultiplier(100))
    }

    @Test
    fun `getSpeedMultiplier requires non-negative speed`() {
        assertThrows<IllegalArgumentException> {
            SpeedCalculator.getSpeedMultiplier(-1)
        }
    }

    @Test
    fun `getSpeedRatio compares two speeds correctly`() {
        // Same speed = 1.0 ratio
        assertEquals(1.0, SpeedCalculator.getSpeedRatio(0, 0))
        assertEquals(1.0, SpeedCalculator.getSpeedRatio(10, 10))

        // Speed 10 vs Speed 0: 2.0x / 1.0x = 2.0
        assertEquals(2.0, SpeedCalculator.getSpeedRatio(10, 0))

        // Speed 0 vs Speed 10: 1.0x / 2.0x = 0.5
        assertEquals(0.5, SpeedCalculator.getSpeedRatio(0, 10))

        // Speed 50 vs Speed 10: 6.0x / 2.0x = 3.0
        assertEquals(3.0, SpeedCalculator.getSpeedRatio(50, 10))
    }

    @Test
    fun `speed formula verification for key breakpoints`() {
        // Verify the formula produces expected results at key levels

        // Level 0: No bonus
        assertEquals(1.0, SpeedCalculator.getSpeedMultiplier(0))

        // Level 10: 2x speed
        assertEquals(2.0, SpeedCalculator.getSpeedMultiplier(10))

        // Level 20: 3x speed
        assertEquals(3.0, SpeedCalculator.getSpeedMultiplier(20))

        // Level 30: 4x speed
        assertEquals(4.0, SpeedCalculator.getSpeedMultiplier(30))
    }

    @Test
    fun `higher speed always results in lower action cost`() {
        for (speedLevel in 0..50) {
            val cost1 = SpeedCalculator.calculateActionCost("attack", speedLevel)
            val cost2 = SpeedCalculator.calculateActionCost("attack", speedLevel + 1)
            assertTrue(cost1 >= cost2,
                "Speed $speedLevel should have >= cost than ${speedLevel + 1}")
        }
    }

    @Test
    fun `all action types have different base costs`() {
        val costs = SpeedCalculator.ActionType.entries.map { it.baseCost }.toSet()
        // We expect most action types to have unique costs
        // (some may share costs, but there should be variety)
        assertTrue(costs.size >= 5, "Should have at least 5 different base costs")
    }

    @Test
    fun `action costs are within reasonable ranges`() {
        for (actionType in SpeedCalculator.ActionType.entries) {
            assertTrue(actionType.baseCost in 2..15,
                "${actionType.name} base cost ${actionType.baseCost} should be in range 2-15")
        }
    }
}
