package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.RoomId
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for TrapData
 */
class TrapDataTest {

    private fun createTestPlayer(perceptionLevel: Int = 0): PlayerState {
        return PlayerState(
            id = "test-player",
            name = "Test Player",
            currentRoomId = "room-1",
            skills = mapOf("Perception" to perceptionLevel)
        )
    }

    @Test
    fun `trap can be triggered`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 10
        )
        val triggered = trap.trigger()
        assertTrue(triggered.triggered)
    }

    @Test
    fun `triggered trap cannot be rolled again`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 10,
            triggered = true
        )
        val player = createTestPlayer(perceptionLevel = 5)
        val result = trap.roll(player)
        assertTrue(result is TrapResult.Failure)
        assertEquals("Trap already triggered", (result as TrapResult.Failure).reason)
    }

    @Test
    fun `trap roll returns Success when not triggered`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 5
        )
        val player = createTestPlayer(perceptionLevel = 10)
        val result = trap.roll(player)
        assertTrue(result is TrapResult.Success)
    }

    @Test
    fun `high perception likely avoids trap`() {
        // Test probabilistically - run multiple times
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 5
        )
        val player = createTestPlayer(perceptionLevel = 20)

        var avoidsCount = 0
        repeat(10) {
            val result = trap.roll(player)
            if (result is TrapResult.Success && result.avoided) {
                avoidsCount++
            }
        }

        // With Perception 20 vs DC 5, should avoid most of the time (needs roll >= -15)
        assertTrue(avoidsCount > 5, "Expected most rolls to avoid trap with high Perception")
    }

    @Test
    fun `low perception less likely to avoid trap`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 20
        )
        val player = createTestPlayer(perceptionLevel = 0)

        var failsCount = 0
        repeat(10) {
            val result = trap.roll(player)
            if (result is TrapResult.Success && !result.avoided) {
                failsCount++
            }
        }

        // With Perception 0 vs DC 20, should fail most of the time (needs roll >= 20)
        assertTrue(failsCount > 5, "Expected most rolls to fail with low Perception")
    }

    @Test
    fun `damage is zero when trap avoided`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 1
        )
        val player = createTestPlayer(perceptionLevel = 20)

        repeat(10) {
            val result = trap.roll(player)
            if (result is TrapResult.Success && result.avoided) {
                assertEquals(0, result.damage)
            }
        }
    }

    @Test
    fun `damage is positive when trap not avoided`() {
        val trap = TrapData(
            id = "trap-1",
            type = "pit",
            difficulty = 30
        )
        val player = createTestPlayer(perceptionLevel = 0)

        repeat(10) {
            val result = trap.roll(player)
            if (result is TrapResult.Success && !result.avoided) {
                assertTrue(result.damage > 0, "Expected positive damage when trap triggers")
            }
        }
    }

    @Test
    fun `damage scales with difficulty`() {
        val easyTrap = TrapData(id = "trap-1", type = "pit", difficulty = 5)
        val hardTrap = TrapData(id = "trap-2", type = "spike", difficulty = 20)
        val player = createTestPlayer(perceptionLevel = 0)

        // Sample damage from both traps
        val easyDamages = mutableListOf<Int>()
        val hardDamages = mutableListOf<Int>()

        repeat(20) {
            val easyResult = easyTrap.roll(player)
            val hardResult = hardTrap.roll(player)

            if (easyResult is TrapResult.Success && !easyResult.avoided) {
                easyDamages.add(easyResult.damage)
            }
            if (hardResult is TrapResult.Success && !hardResult.avoided) {
                hardDamages.add(hardResult.damage)
            }
        }

        // Hard trap should generally do more damage
        if (easyDamages.isNotEmpty() && hardDamages.isNotEmpty()) {
            val easyAvg = easyDamages.average()
            val hardAvg = hardDamages.average()
            assertTrue(hardAvg > easyAvg, "Expected harder trap to do more damage on average")
        }
    }

    @Test
    fun `trigger is immutable operation`() {
        val original = TrapData(id = "trap-1", type = "pit", difficulty = 10)
        val triggered = original.trigger()

        assertFalse(original.triggered)
        assertTrue(triggered.triggered)
        assertNotSame(original, triggered)
    }
}
