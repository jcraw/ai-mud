package com.jcraw.app

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.VectorStore
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.*
import com.jcraw.sophia.llm.LLMClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GameServerTest {
    private lateinit var worldState: WorldState
    private lateinit var gameServer: GameServer
    private lateinit var memoryManager: MemoryManager
    private val mockLLMClient = MockLLMClient()

    @BeforeEach
    fun setup() {
        // Create a simple test dungeon
        val entranceRoom = Room(
            id = "entrance",
            name = "Entrance Hall",
            description = "A grand entrance hall",
            traits = listOf("stone walls", "torches"),
            exits = mapOf(Direction.NORTH to "corridor"),
            entities = listOf(
                Entity.Item("sword", "Iron Sword", "A sharp blade", true, itemType = ItemType.WEAPON, damageBonus = 5),
                Entity.NPC("guard", "Guard", "A stern guard", false, 50, 50)
            )
        )

        val corridorRoom = Room(
            id = "corridor",
            name = "Corridor",
            description = "A long corridor",
            traits = listOf("dark", "narrow"),
            exits = mapOf(Direction.SOUTH to "entrance"),
            entities = emptyList()
        )

        worldState = WorldState(
            rooms = mapOf(
                "entrance" to entranceRoom,
                "corridor" to corridorRoom
            ),
            players = emptyMap()
        )

        // Create memory manager with mock LLM
        val vectorStore = VectorStore(mockLLMClient)
        memoryManager = MemoryManager(vectorStore)

        // Create game server
        gameServer = GameServer(
            worldState = worldState,
            memoryManager = memoryManager,
            roomDescriptionGenerator = RoomDescriptionGenerator(mockLLMClient),
            npcInteractionGenerator = NPCInteractionGenerator(mockLLMClient),
            combatResolver = CombatResolver(mockLLMClient),
            combatNarrator = CombatNarrator(mockLLMClient),
            skillCheckResolver = SkillCheckResolver()
        )
    }

    @Test
    fun `should add player session to server`() = runBlocking {
        val session = createMockSession("player1", "Alice")

        gameServer.addPlayerSession(session, "entrance")

        val updatedState = gameServer.getWorldState()
        assertEquals(1, updatedState.players.size)
        assertNotNull(updatedState.getPlayer("player1"))
        assertEquals("Alice", updatedState.getPlayer("player1")?.name)
    }

    @Test
    fun `should remove player session from server`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        gameServer.removePlayerSession("player1")

        val updatedState = gameServer.getWorldState()
        assertEquals(0, updatedState.players.size)
    }

    @Test
    fun `multiple players can be in same room`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "entrance")

        val updatedState = gameServer.getWorldState()
        assertEquals(2, updatedState.players.size)
        assertEquals("entrance", updatedState.getPlayer("player1")?.currentRoomId)
        assertEquals("entrance", updatedState.getPlayer("player2")?.currentRoomId)
    }

    @Test
    fun `multiple players can be in different rooms`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "corridor")

        val updatedState = gameServer.getWorldState()
        assertEquals(2, updatedState.players.size)
        assertEquals("entrance", updatedState.getPlayer("player1")?.currentRoomId)
        assertEquals("corridor", updatedState.getPlayer("player2")?.currentRoomId)
    }

    @Test
    fun `player can move between rooms`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        val response = gameServer.processIntent("player1", Intent.Move(Direction.NORTH))

        val updatedState = gameServer.getWorldState()
        assertEquals("corridor", updatedState.getPlayer("player1")?.currentRoomId)
        assertTrue(response.contains("Corridor") || response.contains("corridor"))
    }

    @Test
    fun `player can pick up items`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        val response = gameServer.processIntent("player1", Intent.Take("Iron Sword"))

        val updatedState = gameServer.getWorldState()
        val player = updatedState.getPlayer("player1")!!
        assertEquals(1, player.inventory.size)
        assertEquals("Iron Sword", player.inventory[0].name)
        assertTrue(response.contains("take") || response.contains("Take"))
    }

    @Test
    fun `player can equip weapons`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        gameServer.processIntent("player1", Intent.Take("Iron Sword"))
        val response = gameServer.processIntent("player1", Intent.Equip("Iron Sword"))

        val updatedState = gameServer.getWorldState()
        val player = updatedState.getPlayer("player1")!!
        assertNotNull(player.equippedWeapon)
        assertEquals("Iron Sword", player.equippedWeapon?.name)
        assertEquals(5, player.getWeaponDamageBonus())
        assertTrue(response.contains("equip"))
    }

    @Test
    fun `player can view inventory`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        val response = gameServer.processIntent("player1", Intent.Inventory)

        assertTrue(response.contains("Inventory") || response.contains("inventory"))
        assertTrue(response.contains("Health"))
    }

    @Test
    fun `players have independent combat states`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "entrance")

        // Player 1 attacks guard
        gameServer.processIntent("player1", Intent.Attack("guard"))

        val updatedState = gameServer.getWorldState()
        val player1 = updatedState.getPlayer("player1")!!
        val player2 = updatedState.getPlayer("player2")!!

        // Player 1 should be in combat
        assertTrue(player1.isInCombat())
        // Player 2 should not be in combat
        assertTrue(!player2.isInCombat())
    }

    @Test
    fun `events are broadcast to players in same room`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "entrance")

        // Player 1 picks up sword - should notify Player 2
        gameServer.processIntent("player1", Intent.Take("Iron Sword"))

        // Give events time to propagate
        val events = session2.processEvents()

        // Player 2 should see the action
        assertTrue(events.isNotEmpty())
        assertTrue(events.any { it.contains("Alice") && it.contains("picks up") })
    }

    @Test
    fun `events are not broadcast to players in different rooms`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "corridor")

        // Player 1 picks up sword - Player 2 is in different room
        gameServer.processIntent("player1", Intent.Take("Iron Sword"))

        // Give events time to propagate
        val events = session2.processEvents()

        // Player 2 should NOT see the action (different room)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `player movement broadcasts to both rooms`() = runBlocking {
        val session1 = createMockSession("player1", "Alice")
        val session2 = createMockSession("player2", "Bob")
        val session3 = createMockSession("player3", "Charlie")

        gameServer.addPlayerSession(session1, "entrance")
        gameServer.addPlayerSession(session2, "entrance")
        gameServer.addPlayerSession(session3, "corridor")

        // Player 1 moves from entrance to corridor
        gameServer.processIntent("player1", Intent.Move(Direction.NORTH))

        // Player 2 should see Alice leave
        val events2 = session2.processEvents()
        assertTrue(events2.any { it.contains("Alice") && it.contains("leaves") })

        // Player 3 should see Alice enter
        val events3 = session3.processEvents()
        assertTrue(events3.any { it.contains("Alice") && it.contains("entered") })
    }

    @Test
    fun `help command works`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        val response = gameServer.processIntent("player1", Intent.Help)

        assertTrue(response.contains("Commands"))
        assertTrue(response.contains("Movement"))
    }

    @Test
    fun `unknown command returns error message`() = runBlocking {
        val session = createMockSession("player1", "Alice")
        gameServer.addPlayerSession(session, "entrance")

        val response = gameServer.processIntent("player1", Intent.Unknown)

        assertTrue(response.contains("don't understand") || response.contains("help"))
    }

    private fun createMockSession(playerId: PlayerId, playerName: String): PlayerSession {
        val input = BufferedReader(StringReader(""))
        val output = PrintWriter(StringWriter())
        return PlayerSession(playerId, playerName, input, output)
    }
}

/**
 * Mock LLM client for testing that returns simple canned responses
 */
class MockLLMClient : LLMClient {
    override suspend fun complete(prompt: String, systemPrompt: String?): String {
        return when {
            prompt.contains("room description") -> "A simple room description."
            prompt.contains("NPC dialogue") -> "The NPC says something."
            prompt.contains("combat") -> "The combat continues."
            else -> "A generic response."
        }
    }

    override suspend fun getEmbedding(text: String): List<Float> {
        // Return a simple fixed embedding for testing
        return List(1536) { 0.1f }
    }
}
