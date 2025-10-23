package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.PersistenceManager
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.*

/**
 * Integration tests for save/load persistence system
 *
 * Tests the full persistence workflow including:
 * - Saving game state to disk
 * - Loading game state from disk
 * - Persistence roundtrip (save → load → verify)
 * - Save file format validation
 */
class SaveLoadIntegrationTest {

    private val testSaveDir = "test_saves"
    private lateinit var persistenceManager: PersistenceManager

    @BeforeTest
    fun setup() {
        // Use a test-specific save directory
        persistenceManager = PersistenceManager(testSaveDir)

        // Clean up any existing test saves
        File(testSaveDir).deleteRecursively()
    }

    @AfterTest
    fun cleanup() {
        // Clean up test saves after each test
        File(testSaveDir).deleteRecursively()
    }

    @Test
    fun `can save game state to disk`() = runBlocking {
        val world = createTestWorld()

        // Save the game
        val result = persistenceManager.saveGame(world, "test_save")

        assertTrue(result.isSuccess, "Save operation should succeed")

        // Verify save file exists
        val saveFile = File(testSaveDir, "test_save.json")
        assertTrue(saveFile.exists(), "Save file should exist on disk")
        assertTrue(saveFile.length() > 0, "Save file should not be empty")
    }

    @Test
    fun `can load game state from disk`() = runBlocking {
        val world = createTestWorld()

        // Save first
        persistenceManager.saveGame(world, "test_load")

        // Load the saved game
        val result = persistenceManager.loadGame("test_load")

        assertTrue(result.isSuccess, "Load operation should succeed")

        val loadedWorld = result.getOrNull()
        assertNotNull(loadedWorld, "Loaded world state should not be null")
        assertEquals(world.player.id, loadedWorld.player.id, "Player ID should match")
        assertEquals(world.player.name, loadedWorld.player.name, "Player name should match")
        assertEquals(world.rooms.size, loadedWorld.rooms.size, "Room count should match")
    }

    @Test
    fun `persistence roundtrip preserves game state`() = runBlocking {
        // Create a world with specific state
        val sword = Entity.Item(
            id = "iron_sword",
            name = "Iron Sword",
            description = "A sturdy blade",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 5
        )

        val player = PlayerState(
            id = "player1",
            name = "TestHero",
            currentRoomId = "room1",
            health = 75,
            maxHealth = 100,
            gold = 150,
            experiencePoints = 500,
            inventory = listOf(sword),
            equippedWeapon = sword
        )

        val room = Room(
            id = "room1",
            name = "Test Chamber",
            traits = listOf("dark", "musty"),
            entities = emptyList()
        )

        val world = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf(player.id to player)
        )

        // Save
        val saveResult = persistenceManager.saveGame(world, "roundtrip_test")
        assertTrue(saveResult.isSuccess, "Save should succeed")

        // Load
        val loadResult = persistenceManager.loadGame("roundtrip_test")
        assertTrue(loadResult.isSuccess, "Load should succeed")

        val loadedWorld = loadResult.getOrThrow()

        // Verify all state preserved
        assertEquals(player.id, loadedWorld.player.id)
        assertEquals(player.name, loadedWorld.player.name)
        assertEquals(player.health, loadedWorld.player.health)
        assertEquals(player.maxHealth, loadedWorld.player.maxHealth)
        assertEquals(player.gold, loadedWorld.player.gold)
        assertEquals(player.experiencePoints, loadedWorld.player.experiencePoints)
        assertEquals(player.currentRoomId, loadedWorld.player.currentRoomId)

        // Verify inventory
        assertEquals(1, loadedWorld.player.inventory.size)
        assertEquals(sword.id, loadedWorld.player.inventory[0].id)
        assertEquals(sword.name, loadedWorld.player.inventory[0].name)
        assertEquals(sword.damageBonus, loadedWorld.player.inventory[0].damageBonus)

        // Verify equipped weapon
        assertNotNull(loadedWorld.player.equippedWeapon)
        assertEquals(sword.id, loadedWorld.player.equippedWeapon?.id)

        // Verify room state
        val loadedRoom = loadedWorld.rooms["room1"]
        assertNotNull(loadedRoom)
        assertEquals(room.name, loadedRoom.name)
        assertEquals(room.traits, loadedRoom.traits)
    }

    @Test
    fun `save file is valid JSON format`() = runBlocking {
        val world = createTestWorld()

        // Save the game
        persistenceManager.saveGame(world, "format_test")

        // Read the file contents
        val saveFile = File(testSaveDir, "format_test.json")
        val contents = saveFile.readText()

        // Verify it looks like JSON
        assertTrue(contents.trim().startsWith("{"), "Save file should start with {")
        assertTrue(contents.trim().endsWith("}"), "Save file should end with }")
        assertTrue(contents.contains("\"rooms\""), "Save file should contain rooms")
        assertTrue(contents.contains("\"players\""), "Save file should contain players")
    }

    @Test
    fun `can load after game modifications`() = runBlocking {
        val world = createTestWorld()
        val engine = InMemoryGameEngine(world)

        // Move and pick up item
        val sword = Entity.Item(
            id = "sword",
            name = "Short Sword",
            description = "A blade",
            isPickupable = true,
            itemType = ItemType.WEAPON,
            damageBonus = 3
        )

        val roomWithSword = world.getCurrentRoom()!!.copy(entities = listOf(sword))
        val worldWithSword = world.updateRoom(roomWithSword)
        val engineWithSword = InMemoryGameEngine(worldWithSword)

        engineWithSword.processInput("take sword")

        // Save current state
        val beforeSave = engineWithSword.getWorldState()
        persistenceManager.saveGame(beforeSave, "modified_test")

        // Make more changes
        engineWithSword.processInput("equip sword")
        val afterChanges = engineWithSword.getWorldState()

        // Verify state changed
        assertNotNull(afterChanges.player.equippedWeapon, "Sword should be equipped")

        // Load the saved state
        val loadResult = persistenceManager.loadGame("modified_test")
        assertTrue(loadResult.isSuccess, "Load should succeed")

        val loadedWorld = loadResult.getOrThrow()

        // Should match the state at save time (sword in inventory but NOT equipped)
        assertEquals(1, loadedWorld.player.inventory.size)
        assertTrue(loadedWorld.player.inventory.any { it.id == "sword" })
        assertNull(loadedWorld.player.equippedWeapon, "Sword should not be equipped in loaded state")
    }

    @Test
    fun `load fails for non-existent save file`() = runBlocking {
        val result = persistenceManager.loadGame("does_not_exist")

        assertTrue(result.isFailure, "Load should fail for non-existent file")

        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(exception is IllegalStateException, "Should throw IllegalStateException")
        assertTrue(exception.message?.contains("not found") == true, "Error message should mention file not found")
    }

    @Test
    fun `can save and load with custom save names`() = runBlocking {
        val world1 = createTestWorld(playerName = "Hero1")
        val world2 = createTestWorld(playerName = "Hero2")

        // Save both with different names
        persistenceManager.saveGame(world1, "save_slot_1")
        persistenceManager.saveGame(world2, "save_slot_2")

        // Load them back
        val loaded1 = persistenceManager.loadGame("save_slot_1").getOrThrow()
        val loaded2 = persistenceManager.loadGame("save_slot_2").getOrThrow()

        // Verify they're distinct
        assertEquals("Hero1", loaded1.player.name)
        assertEquals("Hero2", loaded2.player.name)
    }

    @Test
    fun `list saves returns all save files`() = runBlocking {
        val world = createTestWorld()

        // Create multiple saves
        persistenceManager.saveGame(world, "save1")
        persistenceManager.saveGame(world, "save2")
        persistenceManager.saveGame(world, "save3")

        // List saves
        val saves = persistenceManager.listSaves()

        assertEquals(3, saves.size, "Should find 3 save files")
        assertTrue(saves.contains("save1"))
        assertTrue(saves.contains("save2"))
        assertTrue(saves.contains("save3"))
    }

    @Test
    fun `delete save removes file from disk`() = runBlocking {
        val world = createTestWorld()

        // Create a save
        persistenceManager.saveGame(world, "to_delete")

        // Verify it exists
        val saveFile = File(testSaveDir, "to_delete.json")
        assertTrue(saveFile.exists(), "Save file should exist before deletion")

        // Delete it
        val deleteResult = persistenceManager.deleteSave("to_delete")
        assertTrue(deleteResult.isSuccess, "Delete should succeed")

        // Verify it's gone
        assertFalse(saveFile.exists(), "Save file should not exist after deletion")
    }

    @Test
    fun `save preserves combat state`() = runBlocking {
        val npc = Entity.NPC(
            id = "goblin",
            name = "Goblin",
            description = "A hostile creature",
            isHostile = true,
            health = 30,
            maxHealth = 30
        )

        val combatState = CombatState(
            combatantNpcId = "goblin",
            playerHealth = 85,
            npcHealth = 25,
            turnCount = 3
        )

        val player = PlayerState(
            id = "player1",
            name = "Warrior",
            currentRoomId = "room1",
            health = 85,
            maxHealth = 100,
            activeCombat = combatState
        )

        val room = Room(
            id = "room1",
            name = "Battle Room",
            traits = listOf("dangerous"),
            entities = listOf(npc)
        )

        val world = WorldState(
            rooms = mapOf("room1" to room),
            players = mapOf(player.id to player)
        )

        // Save
        persistenceManager.saveGame(world, "combat_test")

        // Load
        val loadedWorld = persistenceManager.loadGame("combat_test").getOrThrow()

        // Verify combat state preserved
        assertNotNull(loadedWorld.player.activeCombat)
        assertEquals(combatState.combatantNpcId, loadedWorld.player.activeCombat?.combatantNpcId)
        assertEquals(combatState.npcHealth, loadedWorld.player.activeCombat?.npcHealth)
        assertEquals(combatState.turnCount, loadedWorld.player.activeCombat?.turnCount)
        assertTrue(loadedWorld.player.isInCombat())
    }

    @Test
    fun `save preserves room connections`() = runBlocking {
        val room1 = Room(
            id = "room1",
            name = "First Room",
            traits = listOf("entrance"),
            entities = emptyList(),
            exits = mapOf(Direction.NORTH to "room2", Direction.EAST to "room3")
        )

        val room2 = Room(
            id = "room2",
            name = "Second Room",
            traits = listOf("corridor"),
            entities = emptyList(),
            exits = mapOf(Direction.SOUTH to "room1")
        )

        val room3 = Room(
            id = "room3",
            name = "Third Room",
            traits = listOf("chamber"),
            entities = emptyList(),
            exits = mapOf(Direction.WEST to "room1")
        )

        val player = PlayerState(
            id = "player1",
            name = "Explorer",
            currentRoomId = "room1"
        )

        val world = WorldState(
            rooms = mapOf(
                "room1" to room1,
                "room2" to room2,
                "room3" to room3
            ),
            players = mapOf(player.id to player)
        )

        // Save
        persistenceManager.saveGame(world, "rooms_test")

        // Load
        val loadedWorld = persistenceManager.loadGame("rooms_test").getOrThrow()

        // Verify all rooms and their connections
        assertEquals(3, loadedWorld.rooms.size)

        val loadedRoom1 = loadedWorld.rooms["room1"]
        assertNotNull(loadedRoom1)
        assertEquals(2, loadedRoom1.exits.size)
        assertEquals("room2", loadedRoom1.exits[Direction.NORTH])
        assertEquals("room3", loadedRoom1.exits[Direction.EAST])

        val loadedRoom2 = loadedWorld.rooms["room2"]
        assertNotNull(loadedRoom2)
        assertEquals(1, loadedRoom2.exits.size)
        assertEquals("room1", loadedRoom2.exits[Direction.SOUTH])
    }

    // ========== Helper Functions ==========

    private fun createTestWorld(
        playerName: String = "TestPlayer",
        playerHealth: Int = 100
    ): WorldState {
        val player = PlayerState(
            id = "player1",
            name = playerName,
            currentRoomId = "test_room",
            health = playerHealth,
            maxHealth = 100
        )

        val room = Room(
            id = "test_room",
            name = "Test Room",
            traits = listOf("quiet", "empty"),
            entities = emptyList()
        )

        return WorldState(
            rooms = mapOf("test_room" to room),
            players = mapOf(player.id to player)
        )
    }
}
