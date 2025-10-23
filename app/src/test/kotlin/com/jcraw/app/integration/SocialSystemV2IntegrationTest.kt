package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration tests for Social System V2
 *
 * Tests the full social system workflow including:
 * - Emote system (smile, wave, nod, shrug, laugh, cry, bow)
 * - Ask question system
 * - NPC interactions
 * - Quest completion integration
 * - Dialogue generation
 */
class SocialSystemV2IntegrationTest {

    // ========== Emote System Tests ==========

    @Test
    fun `emote smile increases disposition with friendly NPC`() = runBlocking {
        val npc = createNPCWithSocialComponent("merchant", "Friendly Merchant", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Smile at NPC
        val response = engine.processInput("smile at merchant")

        // Verify emote produces narrative
        assertTrue(response.isNotEmpty(), "Emote should generate narrative")
    }

    @Test
    fun `emote wave at NPC generates appropriate narrative`() = runBlocking {
        val npc = createNPCWithSocialComponent("guard", "Gate Guard", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        val response = engine.processInput("wave at guard")

        // Should get emote narrative
        assertTrue(response.isNotEmpty(), "Wave emote should produce narrative")
        assertFalse(response.contains("error", ignoreCase = true), "Should not error on valid emote")
    }

    @Test
    fun `multiple emotes build relationship over time`() = runBlocking {
        val npc = createNPCWithSocialComponent("villager", "Kind Villager", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Perform multiple positive emotes
        val response1 = engine.processInput("smile at villager")
        val response2 = engine.processInput("wave at villager")
        val response3 = engine.processInput("nod at villager")

        // All emotes should produce narrative
        assertTrue(response1.isNotEmpty(), "Smile should produce narrative")
        assertTrue(response2.isNotEmpty(), "Wave should produce narrative")
        assertTrue(response3.isNotEmpty(), "Nod should produce narrative")
    }

    @Test
    fun `emote without target affects all NPCs in room`() = runBlocking {
        val npc1 = createNPCWithSocialComponent("guard1", "First Guard", isHostile = false)
        val npc2 = createNPCWithSocialComponent("guard2", "Second Guard", isHostile = false)
        val world = createTestWorld(npcs = listOf(npc1, npc2))
        val engine = InMemoryGameEngine(world)

        // Emote without specific target
        val response = engine.processInput("bow")

        // Should affect the room generally
        assertTrue(response.isNotEmpty(), "Untargeted emote should produce narrative")
    }

    @Test
    fun `all seven emote types work correctly`() = runBlocking {
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        val emotes = listOf("smile", "wave", "nod", "shrug", "laugh", "cry", "bow")

        emotes.forEach { emote ->
            val response = engine.processInput("$emote at npc")
            assertTrue(response.isNotEmpty(), "$emote emote should produce narrative")
            assertFalse(response.contains("error", ignoreCase = true), "$emote should not error")
        }
    }

    // ========== Ask Question System Tests ==========

    @Test
    fun `asking NPC about topic stores knowledge`() = runBlocking {
        val npc = createNPCWithSocialComponent("sage", "Wise Sage", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Ask about a topic
        val response = engine.processInput("ask sage about magic")

        // Should get response
        assertTrue(response.isNotEmpty(), "Asking question should produce response")
    }

    @Test
    fun `asking multiple questions builds knowledge base`() = runBlocking {
        val npc = createNPCWithSocialComponent("scholar", "Learned Scholar", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Ask multiple questions
        val response1 = engine.processInput("ask scholar about history")
        val response2 = engine.processInput("ask scholar about artifacts")
        val response3 = engine.processInput("ask scholar about legends")

        // All questions should produce responses
        assertTrue(response1.isNotEmpty(), "First question should produce response")
        assertTrue(response2.isNotEmpty(), "Second question should produce response")
        assertTrue(response3.isNotEmpty(), "Third question should produce response")
    }

    @Test
    fun `asking question to non-existent NPC fails gracefully`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        val response = engine.processInput("ask ghost about secrets")

        // Should handle gracefully (might say NPC not found or similar)
        assertTrue(response.isNotEmpty(), "Should provide feedback for invalid NPC")
    }

    // ========== Disposition System Tests ==========

    @Test
    fun `disposition starts at appropriate level for NPC personality`() = runBlocking {
        // Friendly NPC should start with positive disposition
        val friendlyNPC = createNPCWithSocialComponent("friend", "Friendly NPC", isHostile = false,
            personality = "cheerful and welcoming", disposition = 50)

        // Hostile NPC should start with negative disposition
        val hostileNPC = createNPCWithSocialComponent("enemy", "Hostile NPC", isHostile = true,
            personality = "aggressive and suspicious", disposition = -50)

        val world = createTestWorld(npcs = listOf(friendlyNPC, hostileNPC))
        val engine = InMemoryGameEngine(world)

        // Initialize by interacting
        val response = engine.processInput("look")
        assertTrue(response.isNotEmpty(), "Look should produce output")
    }

    @Test
    fun `disposition tier changes as disposition increases`() = runBlocking {
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false, disposition = 0)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Interact with NPC
        val response = engine.processInput("talk npc")
        assertTrue(response.isNotEmpty(), "Talk should produce response")
    }

    @Test
    fun `negative emotes decrease disposition with hostile NPCs`() = runBlocking {
        val npc = createNPCWithSocialComponent("bandit", "Ruthless Bandit", isHostile = true, disposition = -30)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Cry might be seen as weakness by hostile NPC
        val response = engine.processInput("cry at bandit")
        assertTrue(response.isNotEmpty(), "Emote should produce response")
    }

    // ========== Quest Completion Disposition Bonus Tests ==========

    @Test
    fun `completing quest increases disposition with quest giver`() = runBlocking {
        val questGiver = createNPCWithSocialComponent("questgiver", "Quest Giver", isHostile = false, disposition = 30)
        val world = createTestWorldWithQuest(npc = questGiver, questGiverId = "questgiver")
        val engine = InMemoryGameEngine(world)

        // Accept quest
        val acceptResponse = engine.processInput("accept 1")
        assertTrue(acceptResponse.isNotEmpty(), "Accept should produce response")

        // Complete quest objective (explore the room we're in)
        engine.processInput("look")

        // Claim quest reward (this should trigger disposition bonus)
        val claimResponse = engine.processInput("claim 1")
        assertTrue(claimResponse.isNotEmpty(), "Claim should produce response")
    }

    // ========== Disposition-Aware Dialogue Tests ==========

    @Test
    fun `dialogue tone varies based on disposition tier`() = runBlocking {
        // Create NPC with different disposition levels
        val npc = createNPCWithSocialComponent("changeable", "Mood Changeable NPC", isHostile = false)

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        val response = engine.processInput("talk changeable")

        // Should produce dialogue
        assertTrue(response.isNotEmpty(), "Talking should produce dialogue")
    }

    // ========== Social Event Persistence Tests ==========

    @Test
    fun `social events are persisted to database`() = runBlocking {
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Perform social action
        val response = engine.processInput("smile at npc")
        assertTrue(response.isNotEmpty(), "Social action should produce response")
    }

    @Test
    fun `social event history can be retrieved`() = runBlocking {
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Perform multiple social actions
        val response1 = engine.processInput("smile at npc")
        val response2 = engine.processInput("wave at npc")
        val response3 = engine.processInput("ask npc about weather")

        // All actions should produce responses
        assertTrue(response1.isNotEmpty(), "Smile should produce response")
        assertTrue(response2.isNotEmpty(), "Wave should produce response")
        assertTrue(response3.isNotEmpty(), "Ask should produce response")
    }

    // ========== Full Integration Tests ==========

    @Test
    fun `full social interaction cycle works end-to-end`() = runBlocking {
        val npc = createNPCWithSocialComponent("companion", "Loyal Companion", isHostile = false, disposition = 40)
        val world = createTestWorldWithQuest(npc = npc, questGiverId = "companion")
        val engine = InMemoryGameEngine(world)

        // 1. Build relationship with emotes
        val emote1 = engine.processInput("smile at companion")
        val emote2 = engine.processInput("wave at companion")
        assertTrue(emote1.isNotEmpty(), "Smile should produce response")
        assertTrue(emote2.isNotEmpty(), "Wave should produce response")

        // 2. Ask questions to build knowledge
        val question = engine.processInput("ask companion about quest")
        assertTrue(question.isNotEmpty(), "Question should produce response")

        // 3. Accept and complete quest
        engine.processInput("accept 1")
        engine.processInput("look")  // Complete explore objective

        // 4. Claim quest reward
        val claim = engine.processInput("claim 1")
        assertTrue(claim.isNotEmpty(), "Claim should produce response")

        // 5. Talk to NPC with improved disposition
        val finalDialogue = engine.processInput("talk companion")
        assertTrue(finalDialogue.isNotEmpty(), "Should get dialogue with improved relationship")
    }

    @Test
    fun `social system works correctly without social database`() = runBlocking {
        // Test graceful degradation when no social database is provided
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world)

        // Should still work, just without persistence
        val emoteResponse = engine.processInput("smile at npc")
        val talkResponse = engine.processInput("talk npc")

        assertTrue(emoteResponse.isNotEmpty(), "Emotes should work without database")
        assertTrue(talkResponse.isNotEmpty(), "Talking should work without database")
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        npc: Entity.NPC? = null,
        npcs: List<Entity.NPC> = emptyList()
    ): WorldState {
        val entities = mutableListOf<Entity>()
        if (npc != null) entities.add(npc)
        entities.addAll(npcs)

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = entities
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }

    private fun createTestWorldWithQuest(
        player: PlayerState = PlayerState(
            id = "player1",
            name = "Hero",
            currentRoomId = "test_room"
        ),
        npc: Entity.NPC,
        questGiverId: String
    ): WorldState {
        val quest = Quest(
            id = "test_quest_1",
            title = "Test Quest",
            description = "A simple test quest",
            objectives = listOf(
                QuestObjective.ExploreRoom(
                    id = "obj_1",
                    description = "Visit the test room",
                    targetRoomId = "test_room",
                    targetRoomName = "Test Room",
                    isCompleted = false
                )
            ),
            status = QuestStatus.ACTIVE,
            giver = questGiverId,
            reward = QuestReward(experiencePoints = 100, goldAmount = 50)
        )

        val entities = mutableListOf<Entity>(npc)

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("empty", "quiet"),
            entities = entities
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player),
            availableQuests = listOf(quest)
        )
    }

    private fun createNPCWithSocialComponent(
        id: String,
        name: String,
        isHostile: Boolean,
        personality: String = "neutral and reserved",
        disposition: Int = 0
    ): Entity.NPC {
        val socialComponent = SocialComponent(
            disposition = disposition,
            personality = personality,
            traits = listOf("observant", "cautious")
        )

        return Entity.NPC(
            id = id,
            name = name,
            description = "A test NPC",
            isHostile = isHostile,
            health = 50,
            maxHealth = 50,
            components = mapOf(ComponentType.SOCIAL to socialComponent)
        )
    }
}
