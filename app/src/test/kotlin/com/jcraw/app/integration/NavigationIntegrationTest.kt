package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.procedural.DungeonConfig
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Integration tests for navigation system
 *
 * Tests the full navigation workflow including:
 * - Directional navigation (n/s/e/w and cardinal directions)
 * - Natural language navigation ("go to <room name>")
 * - Invalid direction handling
 * - Exit validation and room connectivity
 */
class NavigationIntegrationTest {

    // ========== Directional Navigation Tests ==========

    @Test
    fun `can navigate using cardinal direction abbreviations`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Player starts at entrance
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate north to corridor
            engine.processInput("n")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate east to treasury
            engine.processInput("e")
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate west back to corridor
            engine.processInput("w")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate south to entrance
            engine.processInput("s")
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `can navigate using full cardinal direction names`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate using full names
            engine.processInput("north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("west")
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("east")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("south")
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `can navigate using go direction syntax`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate using "go <direction>"
            engine.processInput("go north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("go east")
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `can navigate through multiple rooms in sequence`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate from entrance to secret chamber (multi-step journey)
            engine.processInput("north")  // entrance -> corridor
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("north")  // corridor -> throne room
            assertEquals(SampleDungeon.THRONE_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("north")  // throne room -> secret chamber
            assertEquals(SampleDungeon.SECRET_CHAMBER_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate back
            engine.processInput("south")  // secret chamber -> throne room
            assertEquals(SampleDungeon.THRONE_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("south")  // throne room -> corridor
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("south")  // corridor -> entrance
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

    // ========== Natural Language Navigation Tests ==========

    @Test
    fun `can navigate using room names`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate to corridor by name
            val response1 = engine.processInput("go to Dark Corridor")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Navigate to treasury by name
            val response2 = engine.processInput("go to Ancient Treasury")
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `can navigate using partial room names`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Navigate using partial names
            engine.processInput("go to corridor")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("go to throne room")
            assertEquals(SampleDungeon.THRONE_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("go to secret chamber")
            assertEquals(SampleDungeon.SECRET_CHAMBER_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `natural language navigation only works for adjacent rooms`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Player starts at entrance - throne room is not adjacent (corridor is in between)
            val initialRoom = engine.getWorldState().player.currentRoomId
            assertEquals(SampleDungeon.STARTING_ROOM_ID, initialRoom)

            // Try to go directly to throne room (should fail or stay in place)
            engine.processInput("go to throne room")
            val afterAttempt = engine.getWorldState().player.currentRoomId

            // Should either stay at entrance or only move to an adjacent room
            assertNotEquals(SampleDungeon.THRONE_ROOM_ID, afterAttempt,
                "Cannot navigate directly to non-adjacent room")
        }

        @Test
        fun `natural language navigation works from any room`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // Get to corridor first
            engine.processInput("north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // From corridor, can go to armory by name
            engine.processInput("go to armory")
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

    // ========== Invalid Direction Handling Tests ==========

    @Test
    fun `invalid direction does not move player`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            val startingRoom = engine.getWorldState().player.currentRoomId
            assertEquals(SampleDungeon.STARTING_ROOM_ID, startingRoom)

            // Try to go in a direction that doesn't exist from entrance
            engine.processInput("east")  // No east exit from entrance
            val afterInvalid = engine.getWorldState().player.currentRoomId

            assertEquals(startingRoom, afterInvalid, "Player should not move on invalid direction")
        }

        @Test
        fun `multiple invalid directions do not move player`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            // From entrance, only north is valid
            engine.processInput("east")
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("west")
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)

            engine.processInput("south")
            assertEquals(SampleDungeon.STARTING_ROOM_ID, engine.getWorldState().player.currentRoomId)

            // Valid direction should still work after invalid attempts
            engine.processInput("north")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, engine.getWorldState().player.currentRoomId)
        }

        @Test
        fun `non-existent room name does not move player`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            val startingRoom = engine.getWorldState().player.currentRoomId

            // Try to go to a room that doesn't exist
            engine.processInput("go to Imaginary Palace")
            val afterAttempt = engine.getWorldState().player.currentRoomId

            assertEquals(startingRoom, afterAttempt,
                "Player should not move when targeting non-existent room")
        }

        @Test
        fun `gibberish navigation command does not move player`() = runBlocking {
            val world = SampleDungeon.createInitialWorldState()
            val engine = InMemoryGameEngine(world)

            val startingRoom = engine.getWorldState().player.currentRoomId

            // Try nonsense navigation
            engine.processInput("go blarghlfargh")
            val afterAttempt = engine.getWorldState().player.currentRoomId

            assertEquals(startingRoom, afterAttempt,
                "Player should not move on gibberish navigation command")
        }

    // ========== Exit Validation Tests ==========

    @Test
    fun `all room exits point to valid rooms`() {
            val world = SampleDungeon.createInitialWorldState()

            // Verify every exit in every room points to a valid room
            world.rooms.values.forEach { room ->
                room.exits.forEach { (direction, targetRoomId) ->
                    assertTrue(world.rooms.containsKey(targetRoomId),
                        "Exit ${direction.displayName} from ${room.name} should point to valid room $targetRoomId")
                }
            }
        }

        @Test
        fun `exits are bidirectional in sample dungeon`() {
            val world = SampleDungeon.createInitialWorldState()

            // Check bidirectionality
            world.rooms.values.forEach { room ->
                room.exits.forEach { (direction, targetRoomId) ->
                    val targetRoom = world.rooms[targetRoomId]
                    assertNotNull(targetRoom)

                    // Target room should have some path back (not necessarily opposite direction)
                    val hasPathBack = targetRoom.exits.values.contains(room.id)
                    assertTrue(hasPathBack,
                        "Room ${targetRoom.name} should have exit back to ${room.name}")
                }
            }
        }

        @Test
        fun `entrance has correct exits`() {
            val world = SampleDungeon.createInitialWorldState()
            val entrance = world.rooms[SampleDungeon.STARTING_ROOM_ID]

            assertNotNull(entrance)
            assertEquals(1, entrance.exits.size, "Entrance should have 1 exit")
            assertTrue(entrance.exits.containsKey(Direction.NORTH), "Entrance should have north exit")
            assertEquals(SampleDungeon.CORRIDOR_ROOM_ID, entrance.exits[Direction.NORTH])
        }

        @Test
        fun `corridor has correct exits as hub room`() {
            val world = SampleDungeon.createInitialWorldState()
            val corridor = world.rooms[SampleDungeon.CORRIDOR_ROOM_ID]

            assertNotNull(corridor)
            assertEquals(4, corridor.exits.size, "Corridor should have 4 exits (hub room)")

            // Verify all connections
            assertEquals(SampleDungeon.STARTING_ROOM_ID, corridor.exits[Direction.SOUTH])
            assertEquals(SampleDungeon.TREASURY_ROOM_ID, corridor.exits[Direction.EAST])
            assertEquals(SampleDungeon.ARMORY_ROOM_ID, corridor.exits[Direction.WEST])
            assertEquals(SampleDungeon.THRONE_ROOM_ID, corridor.exits[Direction.NORTH])
        }

        @Test
        fun `all rooms are reachable from entrance`() {
            val world = SampleDungeon.createInitialWorldState()

            // BFS to verify all rooms are reachable
            val visited = mutableSetOf<String>()
            val queue = mutableListOf(SampleDungeon.STARTING_ROOM_ID)

            while (queue.isNotEmpty()) {
                val currentId = queue.removeAt(0)
                if (currentId in visited) continue

                visited.add(currentId)
                val room = world.rooms[currentId]!!
                queue.addAll(room.exits.values)
            }

            assertEquals(world.rooms.size, visited.size,
                "All rooms should be reachable from entrance")
        }

        @Test
        fun `dead end rooms have at least one exit`() {
            val world = SampleDungeon.createInitialWorldState()

            // Treasury, Armory, and Secret Chamber are "dead end" rooms (only one exit)
            val deadEndRooms = listOf(
                SampleDungeon.TREASURY_ROOM_ID,
                SampleDungeon.ARMORY_ROOM_ID,
                SampleDungeon.SECRET_CHAMBER_ROOM_ID
            )

            deadEndRooms.forEach { roomId ->
                val room = world.rooms[roomId]
                assertNotNull(room)
                assertTrue(room.exits.isNotEmpty(),
                    "Dead end room ${room.name} should have at least one exit (to escape)")
                assertEquals(1, room.exits.size,
                    "Dead end room ${room.name} should have exactly one exit")
            }
        }

    // ========== Procedural Dungeon Navigation Tests ==========

    @Test
    fun `can navigate in procedurally generated dungeon`() = runBlocking {
            val config = DungeonConfig(
                theme = DungeonTheme.CRYPT,
                roomCount = 8,
                seed = 5555L
            )
            val world = com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder(config).generateDungeon()
            val engine = InMemoryGameEngine(world)

            val startingRoom = engine.getWorldState().player.currentRoomId

            // Try to navigate using available exits
            val currentRoom = world.getCurrentRoom()
            assertNotNull(currentRoom)

            if (currentRoom.exits.isNotEmpty()) {
                val (direction, targetId) = currentRoom.exits.entries.first()

                engine.processInput(direction.name.lowercase())
                val newRoom = engine.getWorldState().player.currentRoomId

                assertEquals(targetId, newRoom,
                    "Should navigate to correct room in procedural dungeon")
            }
        }

        @Test
        fun `all procedural rooms have valid exits`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CASTLE,
                roomCount = 10,
                seed = 6666L
            )
            val world = com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder(config).generateDungeon()

            // Verify all exits are valid
            world.rooms.values.forEach { room ->
                room.exits.forEach { (direction, targetId) ->
                    assertTrue(world.rooms.containsKey(targetId),
                        "Exit ${direction.displayName} from ${room.name} should point to valid room")
                }
            }
        }

        @Test
        fun `procedural dungeon is fully connected`() {
            val config = DungeonConfig(
                theme = DungeonTheme.CAVE,
                roomCount = 12,
                seed = 7777L
            )
            val world = com.jcraw.mud.reasoning.procedural.ProceduralDungeonBuilder(config).generateDungeon()

            // BFS from starting room
            val visited = mutableSetOf<String>()
            val queue = mutableListOf(world.player.currentRoomId)

            while (queue.isNotEmpty()) {
                val currentId = queue.removeAt(0)
                if (currentId in visited) continue

                visited.add(currentId)
                val room = world.rooms[currentId]!!
                queue.addAll(room.exits.values)
            }

            assertEquals(world.rooms.size, visited.size,
                "All rooms in procedural dungeon should be reachable")
        }
}
