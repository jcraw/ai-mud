package com.jcraw.mud.core

import kotlin.test.*

class RespawnComponentTest {

    @Test
    fun `shouldRespawn returns false when not killed yet`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_1"
        )

        assertFalse(component.shouldRespawn(1000L))
    }

    @Test
    fun `shouldRespawn returns false when not enough time elapsed`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertFalse(component.shouldRespawn(400L)) // Only 300 turns elapsed
    }

    @Test
    fun `shouldRespawn returns true when enough time elapsed`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertTrue(component.shouldRespawn(600L)) // 500 turns elapsed
        assertTrue(component.shouldRespawn(1000L)) // More than enough time
    }

    @Test
    fun `shouldRespawn returns true when exactly at threshold`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertTrue(component.shouldRespawn(600L)) // Exactly 500 turns
    }

    @Test
    fun `markKilled updates lastKilled time`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_1"
        )

        val updated = component.markKilled(250L)

        assertEquals(250L, updated.lastKilled)
        assertEquals(500L, updated.respawnTurns)
        assertEquals("goblin_1", updated.originalEntityId)
    }

    @Test
    fun `resetTimer sets lastKilled to 0`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        val reset = component.resetTimer()

        assertEquals(0L, reset.lastKilled)
    }

    @Test
    fun `turnsUntilRespawn returns -1 when not killed`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_1"
        )

        assertEquals(-1L, component.turnsUntilRespawn(1000L))
    }

    @Test
    fun `turnsUntilRespawn calculates remaining time correctly`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertEquals(300L, component.turnsUntilRespawn(300L)) // 200 elapsed, 300 remaining
        assertEquals(100L, component.turnsUntilRespawn(500L)) // 400 elapsed, 100 remaining
    }

    @Test
    fun `turnsUntilRespawn returns 0 when ready to respawn`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertEquals(0L, component.turnsUntilRespawn(600L)) // Exactly at threshold
        assertEquals(0L, component.turnsUntilRespawn(1000L)) // Past threshold
    }

    @Test
    fun `respawnTurns of 0 means instant respawn`() {
        val component = RespawnComponent(
            respawnTurns = 0L,
            lastKilled = 100L,
            originalEntityId = "goblin_1"
        )

        assertTrue(component.shouldRespawn(100L))
        assertTrue(component.shouldRespawn(101L))
    }

    @Test
    fun `component has correct componentType`() {
        val component = RespawnComponent(
            respawnTurns = 500L,
            lastKilled = 0L,
            originalEntityId = "goblin_1"
        )

        assertEquals(ComponentType.RESPAWN, component.componentType)
    }

    @Test
    fun `large respawnTurns values work correctly`() {
        val component = RespawnComponent(
            respawnTurns = Long.MAX_VALUE,
            lastKilled = 100L,
            originalEntityId = "boss_1"
        )

        assertFalse(component.shouldRespawn(1000L))
        assertFalse(component.shouldRespawn(10000L))
    }
}
