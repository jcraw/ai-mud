package com.jcraw.mud.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Tests for WorldState core functionality
 *
 * Focus: Player management, game state, quests, and immutability
 * Note: V3 graph-based navigation tested in WorldSystemV3IntegrationTest.kt
 */
class WorldStateTest {

    // Test fixtures
    private val player1 = PlayerState(
        id = "player1",
        name = "Hero",
        currentRoomId = "space1"
    )

    private val player2 = PlayerState(
        id = "player2",
        name = "Companion",
        currentRoomId = "space1"
    )

    private val baseWorldState = WorldState(
        players = mapOf("player1" to player1)
    )

    // ========== Player Management Tests ==========

    @Test
    fun `adding player works correctly`() {
        val world = baseWorldState
        assertEquals(1, world.players.size)

        val updated = world.addPlayer(player2)

        assertEquals(2, updated.players.size)
        assertNotNull(updated.getPlayer("player2"))
    }

    @Test
    fun `removing player works correctly`() {
        val world = baseWorldState.addPlayer(player2)
        assertEquals(2, world.players.size)

        val updated = world.removePlayer("player2")

        assertEquals(1, updated.players.size)
        assertNull(updated.getPlayer("player2"))
    }

    @Test
    fun `updating player state replaces existing player`() {
        val world = baseWorldState
        val updatedPlayer = player1.copy(health = 50)

        val updated = world.updatePlayer(updatedPlayer)

        assertEquals(50, updated.getPlayer("player1")?.health)
        assertEquals(1, updated.players.size)
    }

    @Test
    fun `getting player by ID works`() {
        val world = baseWorldState.addPlayer(player2)

        val p1 = world.getPlayer("player1")
        val p2 = world.getPlayer("player2")

        assertNotNull(p1)
        assertNotNull(p2)
        assertEquals("Hero", p1.name)
        assertEquals("Companion", p2.name)
    }

    @Test
    fun `getting non-existent player returns null`() {
        val world = baseWorldState

        val player = world.getPlayer("nonexistent")

        assertNull(player)
    }

    @Test
    fun `player property returns first player`() {
        val world = baseWorldState

        val player = world.player

        assertEquals("player1", player.id)
        assertEquals("Hero", player.name)
    }

    // ========== Game State Management Tests ==========

    @Test
    fun `incrementing turn updates turn count`() {
        val world = baseWorldState
        assertEquals(0, world.turnCount)

        val updated = world.incrementTurn()

        assertEquals(1, updated.turnCount)
    }

    @Test
    fun `advancing game time updates game time`() {
        val world = baseWorldState
        assertEquals(0L, world.gameTime)

        val updated = world.advanceTime(100L)

        assertEquals(100L, updated.gameTime)
    }

    @Test
    fun `advancing game time multiple times accumulates`() {
        val world = baseWorldState
            .advanceTime(50L)
            .advanceTime(30L)

        assertEquals(80L, world.gameTime)
    }

    // ========== Quest Management Tests ==========

    @Test
    fun `adding available quest works`() {
        val world = baseWorldState
        assertEquals(0, world.availableQuests.size)

        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test",
            objectives = emptyList(),
            reward = QuestReward(experiencePoints = 50)
        )

        val updated = world.addAvailableQuest(quest)

        assertEquals(1, updated.availableQuests.size)
        assertEquals(quest, updated.getAvailableQuest("quest1"))
    }

    @Test
    fun `removing available quest works`() {
        val quest = Quest(
            id = "quest1",
            title = "Test Quest",
            description = "A test",
            objectives = emptyList(),
            reward = QuestReward(experiencePoints = 50)
        )

        val world = baseWorldState.addAvailableQuest(quest)
        assertEquals(1, world.availableQuests.size)

        val updated = world.removeAvailableQuest("quest1")

        assertEquals(0, updated.availableQuests.size)
        assertNull(updated.getAvailableQuest("quest1"))
    }

    @Test
    fun `getting available quest by ID works`() {
        val quest1 = Quest(
            id = "quest1",
            title = "Quest 1",
            description = "First quest",
            objectives = emptyList(),
            reward = QuestReward(experiencePoints = 50)
        )

        val quest2 = Quest(
            id = "quest2",
            title = "Quest 2",
            description = "Second quest",
            objectives = emptyList(),
            reward = QuestReward(experiencePoints = 100)
        )

        val world = baseWorldState
            .addAvailableQuest(quest1)
            .addAvailableQuest(quest2)

        assertEquals(quest1, world.getAvailableQuest("quest1"))
        assertEquals(quest2, world.getAvailableQuest("quest2"))
        assertNull(world.getAvailableQuest("quest3"))
    }

    // ========== V3 Component Storage Tests ==========

    @Test
    fun `V3 world state has component storage maps`() {
        val world = WorldState(
            players = mapOf("player1" to player1),
            graphNodes = emptyMap(),
            spaces = emptyMap(),
            chunks = emptyMap(),
            entities = emptyMap()
        )

        assertNotNull(world.graphNodes)
        assertNotNull(world.spaces)
        assertNotNull(world.chunks)
        assertNotNull(world.entities)
    }

    @Test
    fun `V3 default constructor creates empty component maps`() {
        val world = WorldState(players = emptyMap())

        assertTrue(world.graphNodes.isEmpty())
        assertTrue(world.spaces.isEmpty())
        assertTrue(world.chunks.isEmpty())
        assertTrue(world.entities.isEmpty())
    }

    // ========== Immutability Tests ==========

    @Test
    fun `world state operations maintain immutability`() {
        val original = baseWorldState
        val originalPlayers = original.players.size
        val originalTurnCount = original.turnCount
        val originalGameTime = original.gameTime
        val originalQuestCount = original.availableQuests.size

        // Various operations
        val withPlayer = original.addPlayer(player2)
        val withTurn = original.incrementTurn()
        val withTime = original.advanceTime(100L)
        val withQuest = original.addAvailableQuest(
            Quest(
                id = "quest1",
                title = "Test",
                description = "Test",
                objectives = emptyList(),
                reward = QuestReward(experiencePoints = 50)
            )
        )

        // Original unchanged
        assertEquals(originalPlayers, original.players.size)
        assertEquals(originalTurnCount, original.turnCount)
        assertEquals(originalGameTime, original.gameTime)
        assertEquals(originalQuestCount, original.availableQuests.size)

        // New states are different
        assertEquals(2, withPlayer.players.size)
        assertEquals(1, withTurn.turnCount)
        assertEquals(100L, withTime.gameTime)
        assertEquals(1, withQuest.availableQuests.size)
    }

    @Test
    fun `copying world state creates independent copy`() {
        val original = baseWorldState

        val copy = original.copy(turnCount = 5)

        assertEquals(0, original.turnCount)
        assertEquals(5, copy.turnCount)
        assertEquals(original.players, copy.players)
    }

    // ========== Game Properties Tests ==========

    @Test
    fun `world state can store custom game properties`() {
        val world = WorldState(
            players = mapOf("player1" to player1),
            gameProperties = mapOf(
                "difficulty" to "hard",
                "permadeath" to "true"
            )
        )

        assertEquals("hard", world.gameProperties["difficulty"])
        assertEquals("true", world.gameProperties["permadeath"])
    }

    @Test
    fun `world state has default empty game properties`() {
        val world = WorldState(players = emptyMap())

        assertTrue(world.gameProperties.isEmpty())
    }
}
