package com.jcraw.mud.core.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.RoomId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ExitData and Condition
 */
class ExitDataTest {

    private fun createTestPlayer(skillLevel: Int = 0, hasItems: Boolean = false): PlayerState {
        val player = PlayerState(
            id = "test-player",
            name = "Test Player",
            currentRoomId = "room-1",
            skills = mapOf("Perception" to skillLevel)
        )
        return if (hasItems) {
            player.copy(equippedWeapon = com.jcraw.mud.core.Entity.Item("sword", "Sword", "A sword"))
        } else {
            player
        }
    }

    @Test
    fun `SkillCheck succeeds when player skill meets difficulty`() {
        val condition = Condition.SkillCheck("Perception", 10)
        val player = createTestPlayer(skillLevel = 10)
        assertTrue(condition.meetsCondition(player))
    }

    @Test
    fun `SkillCheck succeeds when player skill exceeds difficulty`() {
        val condition = Condition.SkillCheck("Perception", 10)
        val player = createTestPlayer(skillLevel = 15)
        assertTrue(condition.meetsCondition(player))
    }

    @Test
    fun `SkillCheck fails when player skill below difficulty`() {
        val condition = Condition.SkillCheck("Perception", 10)
        val player = createTestPlayer(skillLevel = 5)
        assertFalse(condition.meetsCondition(player))
    }

    @Test
    fun `SkillCheck describes requirement correctly`() {
        val condition = Condition.SkillCheck("Perception", 15)
        assertEquals("requires Perception 15", condition.describe())
    }

    @Test
    fun `ItemRequired succeeds when player has items`() {
        val condition = Condition.ItemRequired("climbing_gear")
        val player = createTestPlayer(hasItems = true)
        assertTrue(condition.meetsCondition(player))
    }

    @Test
    fun `ItemRequired fails when player has no items`() {
        val condition = Condition.ItemRequired("climbing_gear")
        val player = createTestPlayer(hasItems = false)
        assertFalse(condition.meetsCondition(player))
    }

    @Test
    fun `ItemRequired describes requirement correctly`() {
        val condition = Condition.ItemRequired("key")
        assertEquals("requires key", condition.describe())
    }

    @Test
    fun `ExitData meetsConditions returns true when all conditions met`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "north",
            description = "A passage to the north",
            conditions = listOf(
                Condition.SkillCheck("Perception", 5)
            )
        )
        val player = createTestPlayer(skillLevel = 10)
        assertTrue(exit.meetsConditions(player))
    }

    @Test
    fun `ExitData meetsConditions returns false when condition not met`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "north",
            description = "A hidden passage",
            conditions = listOf(
                Condition.SkillCheck("Perception", 15)
            )
        )
        val player = createTestPlayer(skillLevel = 5)
        assertFalse(exit.meetsConditions(player))
    }

    @Test
    fun `ExitData meetsConditions requires all conditions to pass`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "up",
            description = "A treacherous climb",
            conditions = listOf(
                Condition.SkillCheck("Perception", 5),
                Condition.ItemRequired("climbing_gear")
            )
        )
        val player = createTestPlayer(skillLevel = 10, hasItems = false)
        assertFalse(exit.meetsConditions(player))
    }

    @Test
    fun `ExitData describeWithConditions shows condition hints when not met`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "north",
            description = "A hidden passage",
            conditions = listOf(
                Condition.SkillCheck("Perception", 15)
            )
        )
        val player = createTestPlayer(skillLevel = 5)
        val description = exit.describeWithConditions(player)
        assertTrue(description.contains("requires Perception 15"))
    }

    @Test
    fun `ExitData describeWithConditions omits hints when conditions met`() {
        val exit = ExitData(
            targetId = "room-2",
            direction = "north",
            description = "A passage",
            conditions = listOf(
                Condition.SkillCheck("Perception", 5)
            )
        )
        val player = createTestPlayer(skillLevel = 10)
        val description = exit.describeWithConditions(player)
        assertEquals("A passage", description)
    }
}
