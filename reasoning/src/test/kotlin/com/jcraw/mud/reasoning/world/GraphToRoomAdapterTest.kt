package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Tests for GraphToRoomAdapter - V3 to V2 conversion layer
 * Focus on behavior contracts, edge cases, and data integrity
 */
class GraphToRoomAdapterTest {

    @Test
    fun `toRoom converts basic space to room with name and description`() {
        val space = SpacePropertiesComponent(
            description = "A dark corridor stretches ahead. Torches flicker on the walls.",
            brightness = 30,
            terrainType = TerrainType.NORMAL
        )

        val room = GraphToRoomAdapter.toRoom("room_1", space)

        assertEquals("room_1", room.id)
        assertEquals("A dark corridor stretches ahead", room.name)
        assertTrue(room.traits.contains("A dark corridor stretches ahead. Torches flicker on the walls."))
    }

    @Test
    fun `toRoom handles empty description with fallback name from node type`() {
        val graphNode = GraphNodeComponent(
            id = "node_hub",
            type = NodeType.Hub,
            chunkId = "chunk_1"
        )

        val space = SpacePropertiesComponent(description = "")

        val room = GraphToRoomAdapter.toRoom("node_hub", space, graphNode)

        assertEquals("Central Hub", room.name)
    }

    @Test
    fun `toRoom converts cardinal direction exits to Direction map`() {
        val exits = listOf(
            ExitData("room_2", "north", "passage to the north"),
            ExitData("room_3", "south", "doorway south"),
            ExitData("room_4", "east", "archway east")
        )

        val space = SpacePropertiesComponent(
            description = "Junction",
            exits = exits
        )

        val room = GraphToRoomAdapter.toRoom("room_1", space)

        assertEquals(3, room.exits.size)
        assertEquals("room_2", room.exits[Direction.NORTH])
        assertEquals("room_3", room.exits[Direction.SOUTH])
        assertEquals("room_4", room.exits[Direction.EAST])
    }

    @Test
    fun `toRoom skips non-cardinal direction exits`() {
        val exits = listOf(
            ExitData("room_2", "north", "passage north"),
            ExitData("room_3", "climb ladder", "ladder leading up"), // Non-cardinal
            ExitData("room_4", "through portal", "shimmering portal") // Non-cardinal
        )

        val space = SpacePropertiesComponent(
            description = "Room with mixed exits",
            exits = exits
        )

        val room = GraphToRoomAdapter.toRoom("room_1", space)

        // Only cardinal direction should be converted
        assertEquals(1, room.exits.size)
        assertEquals("room_2", room.exits[Direction.NORTH])

        // Non-cardinal exits should be stored in properties
        assertTrue(room.properties.containsKey("customExits"))
        val customExits = room.properties["customExits"]
        assertTrue(customExits!!.contains("climb ladder:room_3"))
        assertTrue(customExits.contains("through portal:room_4"))
    }

    @Test
    fun `toRoom adds brightness traits based on levels`() {
        val cases = listOf(
            0 to "Dark",
            10 to "Dark",
            20 to "Dimly lit",
            50 to "Well lit",
            80 to "Brightly lit",
            100 to "Brightly lit"
        )

        cases.forEach { (brightness, expectedTrait) ->
            val space = SpacePropertiesComponent(
                description = "Test room",
                brightness = brightness
            )
            val room = GraphToRoomAdapter.toRoom("test", space)
            assertTrue(
                room.traits.any { it.contains(expectedTrait, ignoreCase = true) },
                "Brightness $brightness should have trait '$expectedTrait', got: ${room.traits}"
            )
        }
    }

    @Test
    fun `toRoom adds terrain type traits`() {
        val terrainCases = mapOf(
            TerrainType.NORMAL to null, // No trait
            TerrainType.DIFFICULT to "Difficult terrain",
            TerrainType.IMPASSABLE to "Impassable terrain"
        )

        terrainCases.forEach { (terrain, expectedTrait) ->
            val space = SpacePropertiesComponent(
                description = "Test room",
                terrainType = terrain
            )
            val room = GraphToRoomAdapter.toRoom("test", space)

            if (expectedTrait != null) {
                assertTrue(room.traits.contains(expectedTrait))
            }
        }
    }

    @Test
    fun `toRoom adds node type traits for special nodes`() {
        val nodeCases = mapOf(
            NodeType.Hub to "Central hub area",
            NodeType.Boss to "Boss chamber",
            NodeType.Frontier to "Frontier boundary",
            NodeType.Questable to "Quest location"
        )

        nodeCases.forEach { (nodeType, expectedTrait) ->
            val graphNode = GraphNodeComponent(
                id = "test_node",
                type = nodeType,
                chunkId = "chunk_1"
            )
            val space = SpacePropertiesComponent(description = "Test")
            val room = GraphToRoomAdapter.toRoom("test_node", space, graphNode)

            assertTrue(
                room.traits.contains(expectedTrait),
                "Node type $nodeType should have trait '$expectedTrait'"
            )
        }
    }

    @Test
    fun `toRoom adds feature traits for traps, resources, safe zones`() {
        val trap = TrapData("trap_1", "spike trap", 15, false, "Spikes!")
        val resource = ResourceNode("res_1", "iron_ore", 3)

        val space = SpacePropertiesComponent(
            description = "Dangerous room",
            traps = listOf(trap),
            resources = listOf(resource),
            isSafeZone = false
        )

        val room = GraphToRoomAdapter.toRoom("test", space)

        assertTrue(room.traits.contains("Traps present"))
        assertTrue(room.traits.contains("Resources available"))
        assertFalse(room.traits.contains("Safe zone - no combat"))
    }

    @Test
    fun `toRoom marks safe zones correctly`() {
        val space = SpacePropertiesComponent(
            description = "Safe haven",
            isSafeZone = true
        )

        val room = GraphToRoomAdapter.toRoom("test", space)

        assertTrue(room.traits.contains("Safe zone - no combat"))
        assertEquals("true", room.properties["isSafeZone"])
    }

    @Test
    fun `toRoom stores space properties in room properties map`() {
        val space = SpacePropertiesComponent(
            description = "Test",
            brightness = 45,
            terrainType = TerrainType.DIFFICULT,
            traps = listOf(TrapData("t1", "trap", 10, false, "desc")),
            resources = listOf(ResourceNode("r1", "ore", 2))
        )

        val room = GraphToRoomAdapter.toRoom("test", space)

        assertEquals("45", room.properties["brightness"])
        assertEquals("DIFFICULT", room.properties["terrainType"])
        assertEquals("1", room.properties["trapCount"])
        assertEquals("1", room.properties["resourceCount"])
    }

    @Test
    fun `toRoom handles all cardinal and intercardinal directions`() {
        val exits = listOf(
            ExitData("n", "north", ""),
            ExitData("s", "south", ""),
            ExitData("e", "east", ""),
            ExitData("w", "west", ""),
            ExitData("ne", "northeast", ""),
            ExitData("nw", "northwest", ""),
            ExitData("se", "southeast", ""),
            ExitData("sw", "southwest", ""),
            ExitData("u", "up", ""),
            ExitData("d", "down", "")
        )

        val space = SpacePropertiesComponent(
            description = "Central junction",
            exits = exits
        )

        val room = GraphToRoomAdapter.toRoom("test", space)

        assertEquals(10, room.exits.size)
        assertEquals("n", room.exits[Direction.NORTH])
        assertEquals("s", room.exits[Direction.SOUTH])
        assertEquals("e", room.exits[Direction.EAST])
        assertEquals("w", room.exits[Direction.WEST])
        assertEquals("ne", room.exits[Direction.NORTHEAST])
        assertEquals("nw", room.exits[Direction.NORTHWEST])
        assertEquals("se", room.exits[Direction.SOUTHEAST])
        assertEquals("sw", room.exits[Direction.SOUTHWEST])
        assertEquals("u", room.exits[Direction.UP])
        assertEquals("d", room.exits[Direction.DOWN])
    }

    @Test
    fun `toRooms batch converts multiple spaces`() {
        val spaces = listOf(
            "room_1" to SpacePropertiesComponent(description = "Room 1"),
            "room_2" to SpacePropertiesComponent(description = "Room 2"),
            "room_3" to SpacePropertiesComponent(description = "Room 3")
        )

        val rooms = GraphToRoomAdapter.toRooms(spaces)

        assertEquals(3, rooms.size)
        assertTrue(rooms.containsKey("room_1"))
        assertTrue(rooms.containsKey("room_2"))
        assertTrue(rooms.containsKey("room_3"))
    }

    @Test
    fun `toRooms uses graph nodes when provided`() {
        val spaces = listOf(
            "hub" to SpacePropertiesComponent(description = "")
        )

        val graphNodes = mapOf(
            "hub" to GraphNodeComponent(
                id = "hub",
                type = NodeType.Hub,
                chunkId = "chunk_1"
            )
        )

        val rooms = GraphToRoomAdapter.toRooms(spaces, graphNodes)

        val hubRoom = rooms["hub"]!!
        assertEquals("Central Hub", hubRoom.name)
        assertTrue(hubRoom.traits.contains("Central hub area"))
    }

    @Test
    fun `toRoom extracts name from long description correctly`() {
        val longDesc = "This is a very long sentence that exceeds forty characters and should be truncated. More text here."

        val space = SpacePropertiesComponent(description = longDesc)
        val room = GraphToRoomAdapter.toRoom("test", space)

        // Name should be truncated to 40 chars from first sentence
        assertTrue(room.name.length <= 40)
        assertTrue(room.name.startsWith("This is a very long sentence"))
    }

    @Test
    fun `toRoom handles space with no exits`() {
        val space = SpacePropertiesComponent(
            description = "Dead end chamber",
            exits = emptyList()
        )

        val room = GraphToRoomAdapter.toRoom("test", space)

        assertTrue(room.exits.isEmpty())
        assertEquals("Dead end chamber", room.name)
    }
}
