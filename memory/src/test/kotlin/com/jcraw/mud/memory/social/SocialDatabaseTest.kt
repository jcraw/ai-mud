package com.jcraw.mud.memory.social

import com.jcraw.mud.core.KnowledgeEntry
import com.jcraw.mud.core.KnowledgeSource
import com.jcraw.mud.core.SocialComponent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialDatabaseTest {
    private lateinit var database: SocialDatabase
    private val testDbPath = "test_social.db"

    @BeforeEach
    fun setup() {
        // Delete existing test database
        File(testDbPath).delete()
        database = SocialDatabase(testDbPath)
    }

    @AfterEach
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @Test
    fun `database creates schema on initialization`() {
        val conn = database.getConnection()
        assertNotNull(conn)
        assertTrue(!conn.isClosed)

        // Verify tables exist by querying them
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")
            val tables = mutableListOf<String>()
            while (rs.next()) {
                tables.add(rs.getString("name"))
            }

            assertTrue(tables.contains("knowledge_entries"))
            assertTrue(tables.contains("social_events"))
            assertTrue(tables.contains("social_components"))
        }
    }

    @Test
    fun `clearAll removes all data`() {
        val knowledgeRepo = SqliteKnowledgeRepository(database)
        val eventRepo = SqliteSocialEventRepository(database)
        val componentRepo = SqliteSocialComponentRepository(database)

        // Insert test data
        knowledgeRepo.save(
            KnowledgeEntry(
                id = "k1",
                entityId = "npc1",
                topic = "test-knowledge",
                question = "What is your test knowledge?",
                content = "Test knowledge",
                isCanon = false,
                source = KnowledgeSource.PLAYER_TAUGHT,
                timestamp = System.currentTimeMillis(),
                tags = mapOf("category" to "fact")
            )
        )
        eventRepo.save(
            SocialEventRecord(
                npcId = "npc1",
                eventType = "TEST",
                dispositionDelta = 10,
                description = "Test event",
                timestamp = System.currentTimeMillis()
            )
        )
        componentRepo.save(
            "npc1",
            SocialComponent(
                disposition = 50,
                personality = "test"
            )
        )

        // Clear all
        database.clearAll()

        // Verify everything is gone
        assertTrue(knowledgeRepo.findByNpcId("npc1").getOrNull()?.isEmpty() == true)
        assertTrue(eventRepo.findByNpcId("npc1").getOrNull()?.isEmpty() == true)
        assertNull(componentRepo.findByNpcId("npc1").getOrNull())
    }
}

class KnowledgeRepositoryTest {
    private lateinit var database: SocialDatabase
    private lateinit var repository: KnowledgeRepository
    private val testDbPath = "test_knowledge.db"

    @BeforeEach
    fun setup() {
        File(testDbPath).delete()
        database = SocialDatabase(testDbPath)
        repository = SqliteKnowledgeRepository(database)
    }

    @AfterEach
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @Test
    fun `save and findById returns saved entry`() {
        val entry = KnowledgeEntry(
            id = "k1",
            entityId = "npc1",
            topic = "dragon location",
            question = "Where does the dragon sleep?",
            content = "The dragon sleeps in the mountain",
            isCanon = false,
            source = KnowledgeSource.PLAYER_TAUGHT,
            timestamp = System.currentTimeMillis(),
            tags = mapOf("category" to "rumor")
        )

        repository.save(entry)
        val result = repository.findById("k1").getOrNull()

        assertNotNull(result)
        assertEquals("k1", result.id)
        assertEquals("npc1", result.entityId)
        assertEquals("dragon location", result.topic)
        assertEquals("Where does the dragon sleep?", result.question)
        assertEquals("The dragon sleeps in the mountain", result.content)
        assertEquals("rumor", result.tags["category"])
    }

    @Test
    fun `findByNpcId returns all entries for NPC`() {
        val entry1 = KnowledgeEntry("k1", "npc1", "fact topic", "What fact?", "Fact 1", false, KnowledgeSource.OBSERVED, 1000L, mapOf("category" to "fact"))
        val entry2 = KnowledgeEntry("k2", "npc1", "rumor topic", "Tell me a rumor", "Fact 2", false, KnowledgeSource.PLAYER_TAUGHT, 2000L, mapOf("category" to "rumor"))
        val entry3 = KnowledgeEntry("k3", "npc2", "other topic", "Other question", "Fact 3", true, KnowledgeSource.PREDEFINED, 3000L, mapOf("category" to "fact"))

        repository.save(entry1)
        repository.save(entry2)
        repository.save(entry3)

        val results = repository.findByNpcId("npc1").getOrNull()
        assertNotNull(results)
        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "k1" })
        assertTrue(results.any { it.id == "k2" })
    }

    @Test
    fun `findByCategory filters by category`() {
        val entry1 = KnowledgeEntry("k1", "npc1", "fact topic", "Share a fact", "Fact 1", false, KnowledgeSource.OBSERVED, 1000L, mapOf("category" to "fact"))
        val entry2 = KnowledgeEntry("k2", "npc1", "rumor topic", "Share a rumor", "Rumor 1", false, KnowledgeSource.PLAYER_TAUGHT, 2000L, mapOf("category" to "rumor"))
        val entry3 = KnowledgeEntry("k3", "npc1", "second fact", "Another fact?", "Fact 2", true, KnowledgeSource.PREDEFINED, 3000L, mapOf("category" to "fact"))

        repository.save(entry1)
        repository.save(entry2)
        repository.save(entry3)

        val results = repository.findByCategory("npc1", "fact").getOrNull()
        assertNotNull(results)
        assertEquals(2, results.size)
        assertTrue(results.all { it.tags["category"] == "fact" })
    }

    @Test
    fun `delete removes entry`() {
        val entry = KnowledgeEntry("k1", "npc1", "test topic", "Test question", "Content", false, KnowledgeSource.PLAYER_TAUGHT, 1000L, mapOf("category" to "fact"))
        repository.save(entry)

        val beforeDelete = repository.findById("k1").getOrNull()
        assertNotNull(beforeDelete)

        repository.delete("k1")

        val afterDelete = repository.findById("k1").getOrNull()
        assertNull(afterDelete)
    }

    @Test
    fun `deleteAllForNpc removes all NPC entries`() {
        repository.save(KnowledgeEntry("k1", "npc1", "topic1", "Q1", "C1", false, KnowledgeSource.PLAYER_TAUGHT, 1000L, mapOf("category" to "fact")))
        repository.save(KnowledgeEntry("k2", "npc1", "topic2", "Q2", "C2", false, KnowledgeSource.PLAYER_TAUGHT, 2000L, mapOf("category" to "rumor")))
        repository.save(KnowledgeEntry("k3", "npc2", "topic3", "Q3", "C3", true, KnowledgeSource.PREDEFINED, 3000L, mapOf("category" to "fact")))

        repository.deleteAllForNpc("npc1")

        val npc1Results = repository.findByNpcId("npc1").getOrNull()
        val npc2Results = repository.findByNpcId("npc2").getOrNull()

        assertTrue(npc1Results?.isEmpty() == true)
        assertEquals(1, npc2Results?.size)
    }
}

class SocialEventRepositoryTest {
    private lateinit var database: SocialDatabase
    private lateinit var repository: SocialEventRepository
    private val testDbPath = "test_events.db"

    @BeforeEach
    fun setup() {
        File(testDbPath).delete()
        database = SocialDatabase(testDbPath)
        repository = SqliteSocialEventRepository(database)
    }

    @AfterEach
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @Test
    fun `save returns generated ID`() {
        val event = SocialEventRecord(
            npcId = "npc1",
            eventType = "HELP_PROVIDED",
            dispositionDelta = 20,
            description = "You helped the guard",
            timestamp = System.currentTimeMillis()
        )

        val id = repository.save(event).getOrNull()
        assertNotNull(id)
        assertTrue(id > 0)
    }

    @Test
    fun `findByNpcId returns all events for NPC`() {
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "EVENT1", dispositionDelta = 10, description = "Desc1", timestamp = 1000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "EVENT2", dispositionDelta = -5, description = "Desc2", timestamp = 2000L))
        repository.save(SocialEventRecord(npcId = "npc2", eventType = "EVENT3", dispositionDelta = 15, description = "Desc3", timestamp = 3000L))

        val results = repository.findByNpcId("npc1").getOrNull()
        assertNotNull(results)
        assertEquals(2, results.size)
    }

    @Test
    fun `findRecentByNpcId limits results`() {
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "E1", dispositionDelta = 10, description = "D1", timestamp = 1000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "E2", dispositionDelta = 10, description = "D2", timestamp = 2000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "E3", dispositionDelta = 10, description = "D3", timestamp = 3000L))

        val results = repository.findRecentByNpcId("npc1", 2).getOrNull()
        assertNotNull(results)
        assertEquals(2, results.size)
        // Should return most recent (sorted by timestamp DESC)
        assertEquals("E3", results[0].eventType)
        assertEquals("E2", results[1].eventType)
    }

    @Test
    fun `findByEventType filters by type`() {
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "HELP", dispositionDelta = 10, description = "D1", timestamp = 1000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "ATTACK", dispositionDelta = -50, description = "D2", timestamp = 2000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "HELP", dispositionDelta = 15, description = "D3", timestamp = 3000L))

        val results = repository.findByEventType("npc1", "HELP").getOrNull()
        assertNotNull(results)
        assertEquals(2, results.size)
        assertTrue(results.all { it.eventType == "HELP" })
    }

    @Test
    fun `deleteAllForNpc removes all NPC events`() {
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "E1", dispositionDelta = 10, description = "D1", timestamp = 1000L))
        repository.save(SocialEventRecord(npcId = "npc1", eventType = "E2", dispositionDelta = 10, description = "D2", timestamp = 2000L))
        repository.save(SocialEventRecord(npcId = "npc2", eventType = "E3", dispositionDelta = 10, description = "D3", timestamp = 3000L))

        repository.deleteAllForNpc("npc1")

        val npc1Results = repository.findByNpcId("npc1").getOrNull()
        val npc2Results = repository.findByNpcId("npc2").getOrNull()

        assertTrue(npc1Results?.isEmpty() == true)
        assertEquals(1, npc2Results?.size)
    }
}

class SocialComponentRepositoryTest {
    private lateinit var database: SocialDatabase
    private lateinit var repository: SocialComponentRepository
    private val testDbPath = "test_components.db"

    @BeforeEach
    fun setup() {
        File(testDbPath).delete()
        database = SocialDatabase(testDbPath)
        repository = SqliteSocialComponentRepository(database)
    }

    @AfterEach
    fun cleanup() {
        database.close()
        File(testDbPath).delete()
    }

    @Test
    fun `save and findByNpcId returns component`() {
        val component = SocialComponent(
            disposition = 50,
            personality = "gruff warrior",
            traits = listOf("honorable", "brave"),
            conversationCount = 5,
            lastInteractionTime = 12345L
        )

        repository.save("npc1", component)
        val result = repository.findByNpcId("npc1").getOrNull()

        assertNotNull(result)
        assertEquals(50, result.disposition)
        assertEquals("gruff warrior", result.personality)
        assertEquals(listOf("honorable", "brave"), result.traits)
        assertEquals(5, result.conversationCount)
        assertEquals(12345L, result.lastInteractionTime)
    }

    @Test
    fun `save replaces existing component`() {
        val component1 = SocialComponent(disposition = 30, personality = "friendly")
        val component2 = SocialComponent(disposition = 60, personality = "very friendly")

        repository.save("npc1", component1)
        repository.save("npc1", component2)

        val result = repository.findByNpcId("npc1").getOrNull()
        assertNotNull(result)
        assertEquals(60, result.disposition)
        assertEquals("very friendly", result.personality)
    }

    @Test
    fun `findAll returns all components`() {
        repository.save("npc1", SocialComponent(disposition = 30, personality = "p1"))
        repository.save("npc2", SocialComponent(disposition = 60, personality = "p2"))
        repository.save("npc3", SocialComponent(disposition = -20, personality = "p3"))

        val results = repository.findAll().getOrNull()
        assertNotNull(results)
        assertEquals(3, results.size)
        assertTrue(results.containsKey("npc1"))
        assertTrue(results.containsKey("npc2"))
        assertTrue(results.containsKey("npc3"))
    }

    @Test
    fun `delete removes component`() {
        val component = SocialComponent(disposition = 50, personality = "test")
        repository.save("npc1", component)

        val beforeDelete = repository.findByNpcId("npc1").getOrNull()
        assertNotNull(beforeDelete)

        repository.delete("npc1")

        val afterDelete = repository.findByNpcId("npc1").getOrNull()
        assertNull(afterDelete)
    }

    @Test
    fun `handles empty traits list`() {
        val component = SocialComponent(
            disposition = 0,
            personality = "neutral",
            traits = emptyList()
        )

        repository.save("npc1", component)
        val result = repository.findByNpcId("npc1").getOrNull()

        assertNotNull(result)
        assertTrue(result.traits.isEmpty())
    }
}
