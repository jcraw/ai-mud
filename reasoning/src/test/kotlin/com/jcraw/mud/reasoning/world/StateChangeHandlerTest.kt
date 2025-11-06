package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.world.*
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Tests for StateChangeHandler.
 * Validates state mutations and description regeneration logic.
 */
class StateChangeHandlerTest {

    private val mockLLMClient = object : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", "Updated description based on changes"),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(10, 20, 30)
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return List(1536) { 0.1 }
        }

        override fun close() {}
    }

    private val handler = StateChangeHandler(mockLLMClient)
    private val testPlayer = PlayerState(name = "TestPlayer")

    private fun createTestSpace(): SpacePropertiesComponent {
        return SpacePropertiesComponent(
            name = "Test Room",
            description = "A dark room",
            exits = emptyMap(),
            brightness = 50,
            terrainType = TerrainType.NORMAL,
            traps = emptyList(),
            resources = emptyList(),
            entities = emptyList(),
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
    }

    @Test
    fun `DestroyObstacle adds state flag`() {
        val space = createTestSpace()
        val action = WorldAction.DestroyObstacle("boulder_destroyed")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(mapOf("boulder_destroyed" to true), result.stateFlags)
    }

    @Test
    fun `DestroyObstacle preserves existing flags`() {
        val space = createTestSpace().copy(stateFlags = mapOf("door_unlocked" to true))
        val action = WorldAction.DestroyObstacle("vines_cut")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(
            mapOf("door_unlocked" to true, "vines_cut" to true),
            result.stateFlags
        )
    }

    @Test
    fun `TriggerTrap marks trap as triggered`() {
        val trap = TrapData(
            id = "trap_1",
            type = "pit",
            difficulty = 10,
            triggered = false
        )
        val space = createTestSpace().copy(traps = listOf(trap))
        val action = WorldAction.TriggerTrap("trap_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertTrue(result.traps.first().triggered)
    }

    @Test
    fun `TriggerTrap only affects specified trap`() {
        val trap1 = TrapData("trap_1", "pit", 10, false)
        val trap2 = TrapData("trap_2", "arrow", 12, false)
        val space = createTestSpace().copy(traps = listOf(trap1, trap2))
        val action = WorldAction.TriggerTrap("trap_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertTrue(result.traps[0].triggered)
        assertFalse(result.traps[1].triggered)
    }

    @Test
    fun `HarvestResource removes resource from list`() {
        val resource = ResourceNode(
            id = "node_1",
            templateId = "iron_ore",
            quantity = 5,
            respawnTime = null
        )
        val space = createTestSpace().copy(resources = listOf(resource))
        val action = WorldAction.HarvestResource("node_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertTrue(result.resources.isEmpty())
    }

    @Test
    fun `HarvestResource preserves other resources`() {
        val resource1 = ResourceNode("node_1", "iron_ore", 5, null)
        val resource2 = ResourceNode("node_2", "gold_ore", 3, null)
        val space = createTestSpace().copy(resources = listOf(resource1, resource2))
        val action = WorldAction.HarvestResource("node_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(1, result.resources.size)
        assertEquals("node_2", result.resources.first().id)
    }

    @Test
    fun `PlaceItem adds item to dropped list`() {
        val item = ItemInstance("item_1", "sword", 5, null, 1)
        val space = createTestSpace()
        val action = WorldAction.PlaceItem(item)

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(1, result.itemsDropped.size)
        assertEquals("item_1", result.itemsDropped.first().id)
    }

    @Test
    fun `PlaceItem preserves existing items`() {
        val item1 = ItemInstance("item_1", "sword", 5, null, 1)
        val item2 = ItemInstance("item_2", "shield", 4, null, 1)
        val space = createTestSpace().copy(itemsDropped = listOf(item1))
        val action = WorldAction.PlaceItem(item2)

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(2, result.itemsDropped.size)
    }

    @Test
    fun `RemoveItem removes item from list`() {
        val item = ItemInstance("item_1", "sword", 5, null, 1)
        val space = createTestSpace().copy(itemsDropped = listOf(item))
        val action = WorldAction.RemoveItem("item_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertTrue(result.itemsDropped.isEmpty())
    }

    @Test
    fun `RemoveItem preserves other items`() {
        val item1 = ItemInstance("item_1", "sword", 5, null, 1)
        val item2 = ItemInstance("item_2", "shield", 4, null, 1)
        val space = createTestSpace().copy(itemsDropped = listOf(item1, item2))
        val action = WorldAction.RemoveItem("item_1")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(1, result.itemsDropped.size)
        assertEquals("item_2", result.itemsDropped.first().id)
    }

    @Test
    fun `UnlockExit removes conditions`() {
        val exit = ExitData(
            targetId = "space_1",
            direction = "north",
            description = "Locked door",
            conditions = listOf(ExitData.Condition.ItemRequired("key")),
            isHidden = false,
            hiddenDifficulty = null
        )
        val space = createTestSpace().copy(exits = mapOf("north" to exit))
        val action = WorldAction.UnlockExit("north", "key")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertTrue(result.exits["north"]!!.conditions.isEmpty())
    }

    @Test
    fun `UnlockExit reveals hidden exit`() {
        val exit = ExitData(
            targetId = "space_1",
            direction = "east",
            description = "Hidden passage",
            conditions = emptyList(),
            isHidden = true,
            hiddenDifficulty = 15
        )
        val space = createTestSpace().copy(exits = mapOf("east" to exit))
        val action = WorldAction.UnlockExit("east")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertFalse(result.exits["east"]!!.isHidden)
    }

    @Test
    fun `UnlockExit is case insensitive`() {
        val exit = ExitData("space_1", "NORTH", "Door", emptyList(), false, null)
        val space = createTestSpace().copy(exits = mapOf("NORTH" to exit))
        val action = WorldAction.UnlockExit("north")

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertNotNull(result.exits.entries.find { it.key.equals("north", ignoreCase = true) })
    }

    @Test
    fun `SetFlag adds flag with value`() {
        val space = createTestSpace()
        val action = WorldAction.SetFlag("puzzle_solved", true)

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(true, result.stateFlags["puzzle_solved"])
    }

    @Test
    fun `SetFlag can set false value`() {
        val space = createTestSpace().copy(stateFlags = mapOf("alarm_active" to true))
        val action = WorldAction.SetFlag("alarm_active", false)

        val result = handler.applyChange(space, action, testPlayer).getOrThrow()

        assertEquals(false, result.stateFlags["alarm_active"])
    }

    @Test
    fun `shouldRegenDescription detects flag changes`() {
        val oldFlags = mapOf("door_open" to false)
        val newFlags = mapOf("door_open" to true)

        val result = handler.shouldRegenDescription(oldFlags, newFlags)

        assertTrue(result)
    }

    @Test
    fun `shouldRegenDescription detects new flags`() {
        val oldFlags = emptyMap<String, Boolean>()
        val newFlags = mapOf("boulder_destroyed" to true)

        val result = handler.shouldRegenDescription(oldFlags, newFlags)

        assertTrue(result)
    }

    @Test
    fun `shouldRegenDescription returns false for no changes`() {
        val flags = mapOf("door_open" to true, "lever_pulled" to false)

        val result = handler.shouldRegenDescription(flags, flags)

        assertFalse(result)
    }

    @Test
    fun `regenDescription calls LLM with proper context`() = runBlocking {
        val space = createTestSpace().copy(
            description = "A dark room with a large boulder blocking the path.",
            stateFlags = mapOf("boulder_destroyed" to true)
        )
        val oldFlags = emptyMap<String, Boolean>()
        val lore = "This is an ancient crypt"

        val result = handler.regenDescription(space, oldFlags, lore).getOrThrow()

        assertEquals("Updated description based on changes", result)
    }

    @Test
    fun `regenDescription handles LLM failure gracefully`() = runBlocking {
        val failingLLM = object : LLMClient {
            override suspend fun chatCompletion(
                modelId: String,
                systemPrompt: String,
                userContext: String,
                maxTokens: Int,
                temperature: Double
            ): OpenAIResponse {
                throw Exception("LLM error")
            }

            override suspend fun createEmbedding(text: String, model: String): List<Double> {
                throw Exception("LLM error")
            }

            override fun close() {}
        }
        val failingHandler = StateChangeHandler(failingLLM)
        val space = createTestSpace()

        val result = failingHandler.regenDescription(space, emptyMap(), "lore")

        assertTrue(result.isFailure)
    }
}
