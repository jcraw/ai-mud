package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertIs

/**
 * Tests for MonsterAIHandler - AI decision-making for NPCs
 *
 * Tests cover:
 * - Temperature calculation based on wisdom
 * - Prompt generation based on intelligence
 * - Fallback decision-making when LLM unavailable
 * - Decision parsing from LLM responses
 */
class MonsterAIHandlerTest {

    private class MockLLMClient(
        private val responseContent: String
    ) : LLMClient {
        var lastTemperature: Double? = null
        var lastSystemPrompt: String? = null

        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            lastTemperature = temperature
            lastSystemPrompt = systemPrompt

            return OpenAIResponse(
                id = "test",
                `object` = "chat.completion",
                created = System.currentTimeMillis() / 1000,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage(
                            role = "assistant",
                            content = responseContent
                        ),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return listOf(0.1, 0.2, 0.3)
        }

        override fun close() {
            // No-op for tests
        }
    }

    @Test
    fun `calculateTemperature returns 1_2 for low wisdom`() {
        val handler = MonsterAIHandler(null)
        val temperature = handler::class.java
            .getDeclaredMethod("calculateTemperature", Int::class.java)
            .apply { isAccessible = true }
            .invoke(handler, 10) as Double

        assertEquals(1.2, temperature, 0.01)
    }

    @Test
    fun `calculateTemperature returns 0_7 for medium wisdom`() {
        val handler = MonsterAIHandler(null)
        val temperature = handler::class.java
            .getDeclaredMethod("calculateTemperature", Int::class.java)
            .apply { isAccessible = true }
            .invoke(handler, 30) as Double

        assertEquals(0.7, temperature, 0.01)
    }

    @Test
    fun `calculateTemperature returns 0_3 for high wisdom`() {
        val handler = MonsterAIHandler(null)
        val temperature = handler::class.java
            .getDeclaredMethod("calculateTemperature", Int::class.java)
            .apply { isAccessible = true }
            .invoke(handler, 60) as Double

        assertEquals(0.3, temperature, 0.01)
    }

    @Test
    fun `fallbackDecision flees when HP critical`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val npc = Entity.NPC(
            id = "goblin1",
            name = "Goblin",
            description = "A goblin",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 5,
                    maxHp = 50,
                    actionTimerEnd = 0L
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("goblin1", worldState)

        assertIs<AIDecision.Flee>(decision, "Should flee when HP is critical (10%)")
    }

    @Test
    fun `fallbackDecision attacks when HP good`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val npc = Entity.NPC(
            id = "orc1",
            name = "Orc",
            description = "An orc",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 40,
                    maxHp = 50,
                    actionTimerEnd = 0L
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Stronghold",
            traits = listOf("fortified"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("orc1", worldState)

        assertIs<AIDecision.Attack>(decision, "Should attack when HP is good (80%)")
    }

    @Test
    fun `fallbackDecision uses item when HP moderate`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val npc = Entity.NPC(
            id = "bandit1",
            name = "Bandit",
            description = "A bandit",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 30,
                    maxHp = 50,
                    actionTimerEnd = 0L
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Forest",
            traits = listOf("trees"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("bandit1", worldState)

        assertIs<AIDecision.UseItem>(decision, "Should try to use item when HP is moderate (60%)")
    }

    @Test
    fun `decideAction returns error when NPC not found`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val worldState = WorldState(
            rooms = mapOf(),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("nonexistent", worldState)

        assertIs<AIDecision.Error>(decision)
        assertTrue(decision.reasoning.contains("not found"))
    }

    @Test
    fun `decideAction returns error when NPC has no combat component`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val npc = Entity.NPC(
            id = "merchant1",
            name = "Merchant",
            description = "A peaceful merchant",
            components = mapOf()
        )

        val room = Room(
            id = "room1",
            name = "Shop",
            traits = listOf("cozy"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("merchant1", worldState)

        assertIs<AIDecision.Error>(decision)
        assertTrue(decision.reasoning.contains("no combat component"))
    }

    @Test
    fun `LLM decision parsing works with Attack action`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Attack", "target": "player", "reasoning": "Aggressive stance"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "warrior1",
            name = "Warrior",
            description = "A warrior",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 50,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent(
                    skills = mapOf(
                        "Intelligence" to SkillState(level = 30, unlocked = true),
                        "Wisdom" to SkillState(level = 30, unlocked = true)
                    )
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Arena",
            traits = listOf("open"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("warrior1", worldState)

        assertIs<AIDecision.Attack>(decision)
        assertEquals("player", decision.target)
        assertEquals("Aggressive stance", decision.reasoning)
    }

    @Test
    fun `LLM decision parsing works with Defend action`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Defend", "reasoning": "Protect myself"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "guard1",
            name = "Guard",
            description = "A guard",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 40,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent()
            )
        )

        val room = Room(
            id = "room1",
            name = "Castle",
            traits = listOf("fortified"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("guard1", worldState)

        assertIs<AIDecision.Defend>(decision)
        assertEquals("Protect myself", decision.reasoning)
    }

    @Test
    fun `LLM decision parsing works with Flee action`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Flee", "reasoning": "I'm outnumbered"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "coward1",
            name = "Coward",
            description = "A cowardly creature",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 20,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent()
            )
        )

        val room = Room(
            id = "room1",
            name = "Battlefield",
            traits = listOf("chaotic"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("coward1", worldState)

        assertIs<AIDecision.Flee>(decision)
        assertEquals("I'm outnumbered", decision.reasoning)
    }

    @Test
    fun `LLM uses correct temperature based on wisdom`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Attack", "target": "player", "reasoning": "Test"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "wise1",
            name = "Wise Sage",
            description = "A wise sage",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 50,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent(
                    skills = mapOf(
                        "Intelligence" to SkillState(level = 50, unlocked = true),
                        "Wisdom" to SkillState(level = 60, unlocked = true)
                    )
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Temple",
            traits = listOf("peaceful"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        handler.decideAction("wise1", worldState)

        assertNotNull(mockLLM.lastTemperature, "Temperature should be set")
        assertEquals(0.3, mockLLM.lastTemperature!!, 0.01, "Should use temperature 0.3 for high wisdom (60)")
    }

    @Test
    fun `LLM prompt complexity scales with intelligence - low`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Attack", "target": "player", "reasoning": "Me smash!"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "dumb1",
            name = "Dumb Brute",
            description = "A dumb brute",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 50,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent(
                    skills = mapOf(
                        "Intelligence" to SkillState(level = 10, unlocked = true),
                        "Wisdom" to SkillState(level = 10, unlocked = true)
                    )
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Dungeon",
            traits = listOf("dark"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        handler.decideAction("dumb1", worldState)

        assertNotNull(mockLLM.lastSystemPrompt)
        assertTrue(mockLLM.lastSystemPrompt!!.contains("limited intelligence") ||
                   mockLLM.lastSystemPrompt!!.contains("impulsive"),
                   "Low intelligence prompt should emphasize simplicity")
    }

    @Test
    fun `LLM prompt complexity scales with intelligence - high`() = runBlocking {
        val mockLLM = MockLLMClient("""
            {"action": "Attack", "target": "player", "reasoning": "Strategic advantage"}
        """.trimIndent())

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "smart1",
            name = "Genius Tactician",
            description = "A genius",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 50,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent(
                    skills = mapOf(
                        "Intelligence" to SkillState(level = 60, unlocked = true),
                        "Wisdom" to SkillState(level = 60, unlocked = true)
                    )
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "War Room",
            traits = listOf("strategic"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        handler.decideAction("smart1", worldState)

        assertNotNull(mockLLM.lastSystemPrompt)
        assertTrue(mockLLM.lastSystemPrompt!!.contains("strategic") ||
                   mockLLM.lastSystemPrompt!!.contains("optimal"),
                   "High intelligence prompt should emphasize strategy")
    }

    @Test
    fun `LLM fallback to rule-based on parse failure`() = runBlocking {
        val mockLLM = MockLLMClient("This is not valid JSON at all!")

        val handler = MonsterAIHandler(mockLLM)

        val npc = Entity.NPC(
            id = "npc1",
            name = "NPC",
            description = "An NPC",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 40,
                    maxHp = 50,
                    actionTimerEnd = 0L
                ),
                ComponentType.SKILL to SkillComponent()
            )
        )

        val room = Room(
            id = "room1",
            name = "Room",
            traits = listOf(),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("npc1", worldState)

        // Should fall back to rule-based decision (Attack at 80% HP)
        assertIs<AIDecision.Attack>(decision)
    }

    @Test
    fun `personality modifies decision - cowardly NPC flees early`() = runBlocking {
        val handler = MonsterAIHandler(null)

        val npc = Entity.NPC(
            id = "coward1",
            name = "Coward",
            description = "A cowardly goblin",
            components = mapOf(
                ComponentType.COMBAT to CombatComponent(
                    currentHp = 25,
                    maxHp = 50, // 50% HP
                    actionTimerEnd = 0L
                ),
                ComponentType.SOCIAL to SocialComponent(
                    disposition = -100,
                    personality = "cowardly",
                    traits = listOf("cowardly")
                )
            )
        )

        val room = Room(
            id = "room1",
            name = "Cave",
            traits = listOf("dark"),
            entities = listOf(npc)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf("player1" to PlayerState(
                id = "player1",
                name = "Hero",
                currentRoomId = "room1"
            ))
        )

        val decision = handler.decideAction("coward1", worldState)

        // Cowardly NPCs flee at 50% HP (not 30%)
        assertIs<AIDecision.Flee>(decision)
    }
}
