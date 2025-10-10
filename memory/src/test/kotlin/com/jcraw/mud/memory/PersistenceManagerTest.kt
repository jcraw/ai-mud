package com.jcraw.mud.memory

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

class PersistenceManagerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `saveGame should create save file`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val worldState = createTestWorldState()
        val result = manager.saveGame(worldState, "test")

        assertTrue(result.isSuccess)
        val saveFile = File(saveDir, "test.json")
        assertTrue(saveFile.exists())
        assertTrue(saveFile.readText().contains("TestPlayer"))
    }

    @Test
    fun `loadGame should restore world state`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val originalState = createTestWorldState()
        manager.saveGame(originalState, "test")

        val result = manager.loadGame("test")

        assertTrue(result.isSuccess)
        val loadedState = result.getOrThrow()

        assertEquals(originalState.player.name, loadedState.player.name)
        assertEquals(originalState.player.health, loadedState.player.health)
        assertEquals(originalState.player.currentRoomId, loadedState.player.currentRoomId)
        assertEquals(originalState.rooms.size, loadedState.rooms.size)
    }

    @Test
    fun `loadGame should fail for non-existent save`() {
        val manager = PersistenceManager(tempDir.path)

        val result = manager.loadGame("nonexistent")

        assertTrue(result.isFailure)
        assertIs<IllegalStateException>(result.exceptionOrNull())
    }

    @Test
    fun `listSaves should return all save files`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val worldState = createTestWorldState()
        manager.saveGame(worldState, "save1")
        manager.saveGame(worldState, "save2")
        manager.saveGame(worldState, "save3")

        val saves = manager.listSaves()

        assertEquals(3, saves.size)
        assertTrue(saves.contains("save1"))
        assertTrue(saves.contains("save2"))
        assertTrue(saves.contains("save3"))
    }

    @Test
    fun `listSaves should return empty list for non-existent directory`() {
        val manager = PersistenceManager(File(tempDir, "nonexistent").path)

        val saves = manager.listSaves()

        assertTrue(saves.isEmpty())
    }

    @Test
    fun `deleteSave should remove save file`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val worldState = createTestWorldState()
        manager.saveGame(worldState, "test")

        val result = manager.deleteSave("test")

        assertTrue(result.isSuccess)
        assertFalse(File(saveDir, "test.json").exists())
    }

    @Test
    fun `deleteSave should fail for non-existent save`() {
        val manager = PersistenceManager(tempDir.path)

        val result = manager.deleteSave("nonexistent")

        assertTrue(result.isFailure)
    }

    @Test
    fun `saved game should preserve player inventory`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val item = Entity.Item(
            id = "sword",
            name = "Iron Sword",
            description = "A sturdy blade",
            itemType = ItemType.WEAPON,
            damageBonus = 5
        )

        val player = PlayerState(
            name = "TestPlayer",
            currentRoomId = "room1",
            inventory = listOf(item)
        )

        val worldState = WorldState(
            rooms = mapOf("room1" to Room("room1", "Test Room", emptyList())),
            player = player
        )

        manager.saveGame(worldState, "inventory_test")
        val loadedState = manager.loadGame("inventory_test").getOrThrow()

        assertEquals(1, loadedState.player.inventory.size)
        assertEquals("Iron Sword", loadedState.player.inventory[0].name)
        assertEquals(5, loadedState.player.inventory[0].damageBonus)
    }

    @Test
    fun `saved game should preserve combat state`() {
        val saveDir = File(tempDir, "saves")
        val manager = PersistenceManager(saveDir.path)

        val worldState = createTestWorldState().copy(
            activeCombat = CombatState(
                combatantNpcId = "skeleton",
                playerHealth = 80,
                npcHealth = 50,
                isPlayerTurn = true,
                turnCount = 3
            )
        )

        manager.saveGame(worldState, "combat_test")
        val loadedState = manager.loadGame("combat_test").getOrThrow()

        assertNotNull(loadedState.activeCombat)
        assertEquals("skeleton", loadedState.activeCombat?.combatantNpcId)
        assertEquals(80, loadedState.activeCombat?.playerHealth)
        assertEquals(50, loadedState.activeCombat?.npcHealth)
        assertTrue(loadedState.activeCombat?.isPlayerTurn == true)
        assertEquals(3, loadedState.activeCombat?.turnCount)
    }

    private fun createTestWorldState(): WorldState {
        val room = Room(
            id = "room1",
            name = "Test Room",
            traits = listOf("dark", "damp")
        )

        val player = PlayerState(
            name = "TestPlayer",
            currentRoomId = "room1",
            health = 100
        )

        return WorldState(
            rooms = mapOf(room.id to room),
            player = player
        )
    }
}
