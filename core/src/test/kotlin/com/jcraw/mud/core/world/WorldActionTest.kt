package com.jcraw.mud.core.world

import com.jcraw.mud.core.ItemInstance
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Tests for WorldAction sealed class.
 * Validates serialization and exhaustive pattern matching.
 */
class WorldActionTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `DestroyObstacle serialization roundtrip`() {
        val action: WorldAction = WorldAction.DestroyObstacle(
            flag = "boulder_destroyed",
            skillRequired = "Strength",
            difficulty = 15
        )

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `DestroyObstacle with nulls serialization`() {
        val action: WorldAction = WorldAction.DestroyObstacle(flag = "vines_cut")

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `TriggerTrap serialization roundtrip`() {
        val action: WorldAction = WorldAction.TriggerTrap(trapId = "trap_123")

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `HarvestResource serialization roundtrip`() {
        val action: WorldAction = WorldAction.HarvestResource(nodeId = "node_456")

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `PlaceItem serialization roundtrip`() {
        val item = ItemInstance(
            id = "item_789",
            templateId = "sword",
            quality = 5,
            charges = null,
            quantity = 1
        )
        val action: WorldAction = WorldAction.PlaceItem(item = item)

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `RemoveItem serialization roundtrip`() {
        val action: WorldAction = WorldAction.RemoveItem(itemId = "item_999")

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `UnlockExit serialization roundtrip`() {
        val action: WorldAction = WorldAction.UnlockExit(
            exitDirection = "north",
            keyItem = "rusty_key"
        )

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `UnlockExit without key serialization`() {
        val action: WorldAction = WorldAction.UnlockExit(exitDirection = "up")

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `SetFlag serialization roundtrip`() {
        val action: WorldAction = WorldAction.SetFlag(flag = "puzzle_solved", value = true)

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `SetFlag with false value`() {
        val action: WorldAction = WorldAction.SetFlag(flag = "door_locked", value = false)

        val serialized = json.encodeToString<WorldAction>(action)
        val deserialized = json.decodeFromString<WorldAction>(serialized)

        assertEquals(action, deserialized)
    }

    @Test
    fun `exhaustive when statement compiles`() {
        // This test ensures all action types are handled
        val actions = listOf<WorldAction>(
            WorldAction.DestroyObstacle("flag"),
            WorldAction.TriggerTrap("trap"),
            WorldAction.HarvestResource("node"),
            WorldAction.PlaceItem(ItemInstance("id", "template", 1, null, 1)),
            WorldAction.RemoveItem("item"),
            WorldAction.UnlockExit("direction"),
            WorldAction.SetFlag("flag")
        )

        actions.forEach { action ->
            val result = when (action) {
                is WorldAction.DestroyObstacle -> "destroy"
                is WorldAction.TriggerTrap -> "trigger"
                is WorldAction.HarvestResource -> "harvest"
                is WorldAction.PlaceItem -> "place"
                is WorldAction.RemoveItem -> "remove"
                is WorldAction.UnlockExit -> "unlock"
                is WorldAction.SetFlag -> "setflag"
            }
            // If this compiles, exhaustive check works
            assertIs<String>(result)
        }
    }

    @Test
    fun `type discrimination via sealed class`() {
        val action: WorldAction = WorldAction.DestroyObstacle("boulder")

        when (action) {
            is WorldAction.DestroyObstacle -> assertEquals("boulder", action.flag)
            else -> error("Wrong type")
        }
    }
}
