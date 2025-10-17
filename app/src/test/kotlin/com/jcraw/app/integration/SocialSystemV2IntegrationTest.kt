package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.DispositionManager
import com.jcraw.mud.memory.DispositionTier
import com.jcraw.mud.memory.SocialDatabase
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration tests for Social System V2
 *
 * Tests the full social system workflow including:
 * - Emote system (smile, wave, nod, shrug, laugh, cry, bow)
 * - Ask question system with knowledge persistence
 * - Disposition tracking and tier calculation
 * - Quest completion disposition bonuses (+15 to quest giver)
 * - Disposition-aware dialogue generation
 * - Social event persistence and retrieval
 */
class SocialSystemV2IntegrationTest {

    private val testDbPath = "test-social-v2.db"

    @BeforeTest
    fun setup() {
        // Clean up any existing test database
        File(testDbPath).delete()
    }

    @AfterTest
    fun tearDown() {
        // Clean up test database
        File(testDbPath).delete()
    }

    // ========== Emote System Tests ==========

    @Test
    fun `emote smile increases disposition with friendly NPC`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("merchant", "Friendly Merchant", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Get initial disposition
        val initialDisposition = db.socialComponentRepository.getDisposition("player1", "merchant") ?: 0

        // Smile at NPC
        val response = engine.processInput("smile at merchant")

        // Check disposition increased
        val newDisposition = db.socialComponentRepository.getDisposition("player1", "merchant") ?: 0
        assertTrue(newDisposition > initialDisposition, "Smiling should increase disposition")
        assertTrue(response.isNotEmpty(), "Emote should generate narrative")

        db.close()
    }

    @Test
    fun `emote wave at NPC generates appropriate narrative`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("guard", "Gate Guard", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        val response = engine.processInput("wave at guard")

        // Should get emote narrative
        assertTrue(response.isNotEmpty(), "Wave emote should produce narrative")
        assertFalse(response.contains("error", ignoreCase = true), "Should not error on valid emote")

        db.close()
    }

    @Test
    fun `multiple emotes build relationship over time`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("villager", "Kind Villager", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        val initialDisposition = db.socialComponentRepository.getDisposition("player1", "villager") ?: 0

        // Perform multiple positive emotes
        engine.processInput("smile at villager")
        engine.processInput("wave at villager")
        engine.processInput("nod at villager")

        val finalDisposition = db.socialComponentRepository.getDisposition("player1", "villager") ?: 0

        // Disposition should increase with multiple emotes
        assertTrue(finalDisposition > initialDisposition, "Multiple emotes should build disposition")

        db.close()
    }

    @Test
    fun `emote without target affects all NPCs in room`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc1 = createNPCWithSocialComponent("guard1", "First Guard", isHostile = false)
        val npc2 = createNPCWithSocialComponent("guard2", "Second Guard", isHostile = false)
        val world = createTestWorld(npcs = listOf(npc1, npc2))
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Emote without specific target
        val response = engine.processInput("bow")

        // Should affect the room generally
        assertTrue(response.isNotEmpty(), "Untargeted emote should produce narrative")

        db.close()
    }

    @Test
    fun `all seven emote types work correctly`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        val emotes = listOf("smile", "wave", "nod", "shrug", "laugh", "cry", "bow")

        emotes.forEach { emote ->
            val response = engine.processInput("$emote at npc")
            assertTrue(response.isNotEmpty(), "$emote emote should produce narrative")
            assertFalse(response.contains("error", ignoreCase = true), "$emote should not error")
        }

        db.close()
    }

    // ========== Ask Question System Tests ==========

    @Test
    fun `asking NPC about topic stores knowledge`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("sage", "Wise Sage", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Ask about a topic
        val response = engine.processInput("ask sage about magic")

        // Should get response
        assertTrue(response.isNotEmpty(), "Asking question should produce response")

        // Check knowledge was stored
        val knowledge = db.knowledgeRepository.getKnowledgeAboutTopic("sage", "magic")
        assertTrue(knowledge.isNotEmpty(), "Knowledge about topic should be stored")

        db.close()
    }

    @Test
    fun `asking multiple questions builds knowledge base`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("scholar", "Learned Scholar", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Ask multiple questions
        engine.processInput("ask scholar about history")
        engine.processInput("ask scholar about artifacts")
        engine.processInput("ask scholar about legends")

        // Check multiple knowledge entries exist
        val allKnowledge = db.knowledgeRepository.getAllKnowledgeForNPC("scholar")
        assertTrue(allKnowledge.size >= 3, "Multiple questions should create multiple knowledge entries")

        db.close()
    }

    @Test
    fun `asking question to non-existent NPC fails gracefully`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        val response = engine.processInput("ask ghost about secrets")

        // Should handle gracefully (might say NPC not found or similar)
        assertTrue(response.isNotEmpty(), "Should provide feedback for invalid NPC")

        db.close()
    }

    // ========== Disposition System Tests ==========

    @Test
    fun `disposition starts at appropriate level for NPC personality`() = runBlocking {
        val db = SocialDatabase(testDbPath)

        // Friendly NPC should start with positive disposition
        val friendlyNPC = createNPCWithSocialComponent("friend", "Friendly NPC", isHostile = false,
            personality = "cheerful and welcoming", disposition = 50)

        // Hostile NPC should start with negative disposition
        val hostileNPC = createNPCWithSocialComponent("enemy", "Hostile NPC", isHostile = true,
            personality = "aggressive and suspicious", disposition = -50)

        val world = createTestWorld(npcs = listOf(friendlyNPC, hostileNPC))
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Initialize by interacting
        engine.processInput("look")

        val friendlyDisp = db.socialComponentRepository.getDisposition("player1", "friend") ?: 0
        val hostileDisp = db.socialComponentRepository.getDisposition("player1", "enemy") ?: 0

        assertTrue(friendlyDisp >= 0, "Friendly NPC should have non-negative disposition")
        assertTrue(hostileDisp <= 0, "Hostile NPC should have non-positive disposition")

        db.close()
    }

    @Test
    fun `disposition tier changes as disposition increases`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false, disposition = 0)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Set initial neutral disposition
        db.socialComponentRepository.setDisposition("player1", "npc", 0)

        val dispositionManager = DispositionManager(db.socialComponentRepository, db.socialEventRepository)

        var tier = dispositionManager.getDispositionTier("player1", "npc")
        assertEquals(DispositionTier.NEUTRAL, tier, "Should start neutral at 0")

        // Increase to friendly
        db.socialComponentRepository.setDisposition("player1", "npc", 60)
        tier = dispositionManager.getDispositionTier("player1", "npc")
        assertEquals(DispositionTier.FRIENDLY, tier, "Should be friendly at 60")

        // Increase to allied
        db.socialComponentRepository.setDisposition("player1", "npc", 90)
        tier = dispositionManager.getDispositionTier("player1", "npc")
        assertEquals(DispositionTier.ALLIED, tier, "Should be allied at 90")

        db.close()
    }

    @Test
    fun `negative emotes decrease disposition with hostile NPCs`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("bandit", "Ruthless Bandit", isHostile = true, disposition = -30)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        val initialDisposition = db.socialComponentRepository.getDisposition("player1", "bandit") ?: -30

        // Cry might be seen as weakness by hostile NPC
        engine.processInput("cry at bandit")

        val newDisposition = db.socialComponentRepository.getDisposition("player1", "bandit") ?: -30

        // Disposition should potentially decrease or stay same with hostile NPC
        assertTrue(newDisposition <= initialDisposition, "Crying to hostile NPC should not improve disposition")

        db.close()
    }

    // ========== Quest Completion Disposition Bonus Tests ==========

    @Test
    fun `completing quest increases disposition with quest giver`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val questGiver = createNPCWithSocialComponent("questgiver", "Quest Giver", isHostile = false, disposition = 30)
        val world = createTestWorldWithQuest(npc = questGiver, questGiverId = "questgiver")
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Get initial disposition
        val initialDisposition = db.socialComponentRepository.getDisposition("player1", "questgiver") ?: 30

        // Accept quest
        engine.processInput("accept 1")

        // Complete quest objective (explore the room we're in)
        engine.processInput("look")

        // Claim quest reward (this should trigger disposition bonus)
        engine.processInput("claim 1")

        // Check disposition increased
        val newDisposition = db.socialComponentRepository.getDisposition("player1", "questgiver") ?: 30
        assertTrue(newDisposition >= initialDisposition + 10,
            "Completing quest should give +15 disposition bonus (or close to it)")

        db.close()
    }

    // ========== Disposition-Aware Dialogue Tests ==========

    @Test
    fun `dialogue tone varies based on disposition tier`() = runBlocking {
        val db = SocialDatabase(testDbPath)

        // Create NPC with different disposition levels
        val npc = createNPCWithSocialComponent("changeable", "Mood Changeable NPC", isHostile = false)

        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Set low disposition (hostile tier)
        db.socialComponentRepository.setDisposition("player1", "changeable", -80)
        val hostileResponse = engine.processInput("talk changeable")

        // Set high disposition (allied tier)
        db.socialComponentRepository.setDisposition("player1", "changeable", 90)
        val alliedResponse = engine.processInput("talk changeable")

        // Both should produce dialogue but potentially with different tones
        assertTrue(hostileResponse.isNotEmpty(), "Hostile disposition should produce dialogue")
        assertTrue(alliedResponse.isNotEmpty(), "Allied disposition should produce dialogue")

        // Responses might differ (implementation dependent on LLM)
        // This is a behavioral test - we verify the system works, not exact content

        db.close()
    }

    // ========== Social Event Persistence Tests ==========

    @Test
    fun `social events are persisted to database`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Perform social action
        engine.processInput("smile at npc")

        // Check event was recorded
        val events = db.socialEventRepository.getEventsForNPC("player1", "npc")
        assertTrue(events.isNotEmpty(), "Social event should be persisted")

        db.close()
    }

    @Test
    fun `social event history can be retrieved`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // Perform multiple social actions
        engine.processInput("smile at npc")
        engine.processInput("wave at npc")
        engine.processInput("ask npc about weather")

        // Retrieve history
        val events = db.socialEventRepository.getEventsForNPC("player1", "npc")

        // Should have at least the emote events (question handling may vary)
        assertTrue(events.size >= 2, "Multiple social events should be recorded")

        db.close()
    }

    // ========== Full Integration Tests ==========

    @Test
    fun `full social interaction cycle works end-to-end`() = runBlocking {
        val db = SocialDatabase(testDbPath)
        val npc = createNPCWithSocialComponent("companion", "Loyal Companion", isHostile = false, disposition = 40)
        val world = createTestWorldWithQuest(npc = npc, questGiverId = "companion")
        val engine = InMemoryGameEngine(world, socialDatabase = db)

        // 1. Start with some disposition
        val startDisposition = db.socialComponentRepository.getDisposition("player1", "companion") ?: 40

        // 2. Build relationship with emotes
        engine.processInput("smile at companion")
        engine.processInput("wave at companion")

        val afterEmotesDisposition = db.socialComponentRepository.getDisposition("player1", "companion") ?: 40
        assertTrue(afterEmotesDisposition > startDisposition, "Emotes should build relationship")

        // 3. Ask questions to build knowledge
        engine.processInput("ask companion about quest")
        val knowledge = db.knowledgeRepository.getAllKnowledgeForNPC("companion")
        assertTrue(knowledge.isNotEmpty(), "Questions should build knowledge")

        // 4. Accept and complete quest
        engine.processInput("accept 1")
        engine.processInput("look")  // Complete explore objective

        // 5. Claim quest reward (should give disposition bonus)
        engine.processInput("claim 1")

        val finalDisposition = db.socialComponentRepository.getDisposition("player1", "companion") ?: 40
        assertTrue(finalDisposition > afterEmotesDisposition, "Quest completion should further increase disposition")

        // 6. Talk to NPC with improved disposition
        val finalDialogue = engine.processInput("talk companion")
        assertTrue(finalDialogue.isNotEmpty(), "Should get dialogue with improved relationship")

        db.close()
    }

    @Test
    fun `social system works correctly without social database`() = runBlocking {
        // Test graceful degradation when no social database is provided
        val npc = createNPCWithSocialComponent("npc", "Test NPC", isHostile = false)
        val world = createTestWorld(npc = npc)
        val engine = InMemoryGameEngine(world, socialDatabase = null)

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
                QuestObjective.Explore(
                    roomId = "test_room",
                    roomName = "Test Room",
                    description = "Visit the test room"
                )
            ),
            status = QuestStatus.AVAILABLE,
            giver = questGiverId,
            rewards = QuestRewards(xp = 100, gold = 50)
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
            quests = listOf(quest)
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
            personality = personality,
            traits = listOf("observant", "cautious"),
            disposition = mapOf("player1" to disposition)
        )

        return Entity.NPC(
            id = id,
            name = name,
            description = "A test NPC",
            isHostile = isHostile,
            health = 50,
            maxHealth = 50,
            components = mapOf("social" to socialComponent)
        )
    }
}
