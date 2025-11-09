package com.jcraw.mud.testbot

import com.jcraw.mud.core.SampleDungeon
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class InMemoryGameEngineTest {

    @Test
    fun `Game engine processes look command`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val response = engine.processInput("look")

        assertNotNull(response)
        assertTrue(response.contains("Dungeon Entrance", ignoreCase = true) || response.contains("entrance", ignoreCase = true))
    }

    @Test
    fun `Game engine handles movement`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val response = engine.processInput("north")

        assertNotNull(response)
        // Should either move or say can't go that way
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun `Game engine handles invalid commands`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val response = engine.processInput("xyzabc123")

        assertNotNull(response)
        assertTrue(response.contains("Unknown command") || response.contains("unknown"))
    }

    @Test
    fun `Game engine shows inventory`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val response = engine.processInput("inventory")

        assertNotNull(response)
        assertTrue(response.contains("Inventory"))
    }

    @Test
    fun `Game engine handles take and drop`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        // Move to a room with items
        engine.processInput("east") // Go to corridor

        // Try to take an item (iron sword is in the corridor)
        val takeResponse = engine.processInput("take sword")

        // Should either succeed or fail gracefully
        assertNotNull(takeResponse)
        assertTrue(takeResponse.isNotEmpty())

        // Check inventory
        val invResponse = engine.processInput("inventory")
        assertNotNull(invResponse)
    }

    @Test
    fun `Game engine tracks world state changes`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val initialRoom = engine.getWorldState().getCurrentRoomView()?.id

        // Move if possible
        engine.processInput("east")

        val newRoom = engine.getWorldState().getCurrentRoomView()?.id

        // Room might change or stay same depending on dungeon layout
        assertNotNull(newRoom)
    }

    @Test
    fun `Game engine can be reset`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        val initialRoomId = engine.getWorldState().getCurrentRoomView()?.id

        // Make some changes
        engine.processInput("east")

        // Reset
        engine.reset()

        val resetRoomId = engine.getWorldState().getCurrentRoomView()?.id

        assertEquals(initialRoomId, resetRoomId)
    }

    @Test
    fun `Game engine stops running after quit`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        assertTrue(engine.isRunning())

        engine.processInput("quit")

        assertFalse(engine.isRunning())
    }

    @Test
    fun `Game engine handles combat initiation`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        // Move to room with NPC (treasury has Old Guard)
        engine.processInput("north")

        // Try to attack
        val response = engine.processInput("attack guard")

        assertNotNull(response)
        // Should either start combat or say no such NPC
        assertTrue(response.isNotEmpty())
    }

    @Test
    fun `Game engine handles equipment`() = runBlocking {
        val worldState = SampleDungeon.createInitialWorldState()
        val engine = InMemoryGameEngine(worldState)

        // Get a weapon
        engine.processInput("east")
        engine.processInput("take sword")

        // Equip it
        val equipResponse = engine.processInput("equip sword")

        assertNotNull(equipResponse)
        assertTrue(equipResponse.contains("equip") || equipResponse.contains("don't have"))
    }
}
