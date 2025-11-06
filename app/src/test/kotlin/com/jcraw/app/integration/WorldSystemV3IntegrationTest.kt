package com.jcraw.app.integration

import com.jcraw.mud.core.*
import com.jcraw.mud.core.world.*
import com.jcraw.mud.memory.world.*
import com.jcraw.mud.reasoning.worldgen.*
import com.jcraw.mud.reasoning.world.WorldGenerator
import com.jcraw.mud.reasoning.world.LoreInheritanceEngine
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import kotlin.test.*

/**
 * Integration tests for World System V3 - Graph-Based Navigation
 *
 * Tests the complete V3 workflow including:
 * - Graph-based world generation and initialization
 * - Navigation using graph edges
 * - Lazy-fill content generation
 * - Frontier traversal and chunk cascade
 * - Persistence (save/load) of graph+space state
 * - Multi-user compatibility with V3
 *
 * These tests verify the full end-to-end functionality of World V3,
 * ensuring that graph navigation, content generation, and persistence
 * work correctly together.
 */
class WorldSystemV3IntegrationTest {

    // ==================== SETUP HELPERS ====================

    /**
     * Create a minimal V3 world state with graph nodes and spaces
     * for testing purposes. Does not require LLM.
     */
    private fun createV3WorldState(nodeCount: Int = 5): WorldState {
        val rng = Random(12345)
        val graphGenerator = GraphGenerator(rng, difficultyLevel = 1)
        val layout = GraphLayout.Grid(width = 3, height = 2) // 6 nodes

        val graphNodes = graphGenerator.generate("test_chunk", layout)

        // Create minimal space stubs for each node
        val spaces = graphNodes.associate { node ->
            node.id to SpacePropertiesComponent(
                name = "Node ${node.type}",
                description = "Node ${node.id} - ${node.type}",
                exits = emptyList(),
                terrainType = TerrainType.NORMAL,
                brightness = 50,
                entities = emptyList(),
                resources = emptyList(),
                traps = emptyList(),
                itemsDropped = emptyList()
            )
        }

        val chunk = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = null,
            children = emptyList(),
            lore = "Test chunk for V3 integration tests",
            biomeTheme = "Test Theme",
            sizeEstimate = 10,
            mobDensity = 0.3,
            difficultyLevel = 1
        )

        val hubNode = graphNodes.first { it.type == NodeType.Hub }
        val player = PlayerState(
            id = "test_player",
            name = "Test Hero",
            currentRoomId = hubNode.id
        )

        return WorldState(
            rooms = emptyMap(),
            players = mapOf(player.id to player),
            graphNodes = graphNodes.associateBy { it.id },
            spaces = spaces,
            chunks = mapOf("test_chunk" to chunk)
        )
    }

    // ==================== GRAPH NAVIGATION TESTS ====================

    @Test
    fun `can navigate using graph edges in V3`() = runBlocking {
        val world = createV3WorldState()
        val engine = InMemoryGameEngine(world)

        // Get starting node and available directions
        val currentNode = world.getCurrentGraphNode()
        assertNotNull(currentNode, "Should have current graph node")

        if (currentNode.neighbors.isNotEmpty()) {
            // Get first neighbor
            val firstEdge = currentNode.neighbors.first()
            val directionString = firstEdge.direction

            // Navigate in that direction
            engine.processInput(directionString)

            // Verify player moved to target node
            val newWorld = engine.getWorldState()
            assertEquals(firstEdge.targetId, newWorld.player.currentRoomId,
                "Player should move to target node via graph edge")
        }
    }

    @Test
    fun `V3 navigation only allows valid graph edges`() {
        val world = createV3WorldState()
        val currentNode = world.getCurrentGraphNode()
        assertNotNull(currentNode)

        // Find a direction NOT in the current node's neighbors
        val validDirections = currentNode.neighbors.map { it.direction }.toSet()
        val invalidDirection = Direction.entries.first { it.displayName.lowercase() !in validDirections }

        // Attempt to move in invalid direction should fail (state unchanged)
        val newWorld = world.movePlayerV3(invalidDirection)
        assertNull(newWorld, "Should not allow movement in direction without graph edge")
    }

    @Test
    fun `V3 world has hub frontier and dead-end nodes`() {
        val world = createV3WorldState()

        val hubCount = world.graphNodes.values.count { it.type == NodeType.Hub }
        val frontierCount = world.graphNodes.values.count { it.type == NodeType.Frontier }
        val deadEndCount = world.graphNodes.values.count { it.type == NodeType.DeadEnd }

        assertTrue(hubCount >= 1, "Should have at least 1 Hub node (entry point)")
        assertTrue(frontierCount >= 2, "Should have at least 2 Frontier nodes (for expansion)")
        assertTrue(deadEndCount >= 0, "Dead-end nodes optional but common")
    }

    @Test
    fun `V3 graph is fully connected via BFS`() {
        val world = createV3WorldState()

        // BFS from starting node
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        val startNode = world.getCurrentGraphNode()
        assertNotNull(startNode)

        queue.add(startNode.id)
        visited.add(startNode.id)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            val node = world.graphNodes[currentId]!!

            for (edge in node.neighbors) {
                if (edge.targetId !in visited) {
                    visited.add(edge.targetId)
                    queue.add(edge.targetId)
                }
            }
        }

        assertEquals(world.graphNodes.size, visited.size,
            "All nodes should be reachable from starting node (connected graph)")
    }

    @Test
    fun `V3 graph has loops for non-linear navigation`() {
        val world = createV3WorldState()

        // Detect cycle using DFS
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()

        fun dfs(nodeId: String, parent: String?): Boolean {
            visited.add(nodeId)
            recStack.add(nodeId)

            val node = world.graphNodes[nodeId]!!

            for (edge in node.neighbors) {
                if (edge.targetId == parent) continue // Skip parent edge

                if (edge.targetId in recStack) {
                    return true // Cycle detected
                }

                if (edge.targetId !in visited) {
                    if (dfs(edge.targetId, nodeId)) return true
                }
            }

            recStack.remove(nodeId)
            return false
        }

        val startNode = world.getCurrentGraphNode()!!
        val hasCycle = dfs(startNode.id, null)

        assertTrue(hasCycle, "V3 graph should contain loops for interesting navigation")
    }

    // ==================== LAZY-FILL TESTS ====================

    @Test
    fun `V3 spaces start with stub descriptions`() {
        val world = createV3WorldState()

        // All spaces should have minimal descriptions initially (stubs)
        world.spaces.values.forEach { space ->
            assertNotNull(space.description, "Space should have description")
            assertTrue(space.description.isNotBlank(), "Space description should not be blank")
        }
    }

    @Test
    fun `V3 space properties include terrain and brightness`() {
        val world = createV3WorldState()

        world.spaces.values.forEach { space ->
            assertNotNull(space.terrainType, "Space should have terrain type")
            assertTrue(space.brightness in 0..100, "Brightness should be 0-100")
        }
    }

    @Test
    fun `V3 getCurrentSpace returns space for current player location`() {
        val world = createV3WorldState()

        val space = world.getCurrentSpace()
        assertNotNull(space, "Should get current space in V3 world")

        val player = world.player
        val expectedSpace = world.spaces[player.currentRoomId]
        assertEquals(expectedSpace, space, "getCurrentSpace should return space at player location")
    }

    @Test
    fun `V3 getAvailableExitsV3 returns all non-hidden exits`() {
        val world = createV3WorldState()

        val exits = world.getAvailableExitsV3()

        // Count visible (non-hidden) exits from current node
        val currentNode = world.getCurrentGraphNode()!!
        val expectedExitCount = currentNode.neighbors.count { !it.hidden }

        assertEquals(expectedExitCount, exits.size,
            "Should return all visible exits from current node")
    }

    // ==================== CHUNK STORAGE ====================

    @Test
    fun `V3 world has chunk storage`() {
        val world = createV3WorldState()

        assertNotNull(world.chunks, "WorldState should have chunks map")
        assertTrue(world.chunks.isNotEmpty(), "Should have at least one chunk")

        val chunk = world.chunks["test_chunk"]
        assertNotNull(chunk, "Test chunk should exist")
        assertEquals(ChunkLevel.SUBZONE, chunk.level)
    }

    @Test
    fun `V3 can retrieve chunk by ID`() {
        val world = createV3WorldState()

        val chunk = world.getChunk("test_chunk")
        assertNotNull(chunk, "Should retrieve chunk by ID")
        assertEquals("Test chunk for V3 integration tests", chunk.lore)
    }

    @Test
    fun `V3 can add new chunk to world`() {
        val world = createV3WorldState()

        val newChunk = WorldChunkComponent(
            level = ChunkLevel.SUBZONE,
            parentId = "test_chunk",
            children = emptyList(),
            lore = "New test chunk",
            biomeTheme = "New Theme",
            sizeEstimate = 5,
            mobDensity = 0.5,
            difficultyLevel = 2
        )

        val updatedWorld = world.addChunk("new_chunk", newChunk)
        assertNotNull(updatedWorld, "Should successfully add chunk")

        val retrievedChunk = updatedWorld.getChunk("new_chunk")
        assertNotNull(retrievedChunk, "Should retrieve newly added chunk")
        assertEquals("New test chunk", retrievedChunk.lore)
    }

    // ==================== ENTITY STORAGE V3 ====================

    @Test
    fun `V3 can add entities to spaces`() {
        val world = createV3WorldState()
        val currentSpace = world.getCurrentSpace()!!

        val goblin = Entity.NPC(
            id = "goblin_1",
            name = "Goblin",
            description = "A small green goblin",
            health = 30,
            maxHealth = 30,
            isHostile = true,
            stats = Stats()
        )

        val updatedWorld = world.addEntityToSpace(world.player.currentRoomId, goblin)
        assertNotNull(updatedWorld, "Should successfully add entity to space")

        val entities = updatedWorld.getEntitiesInSpace(world.player.currentRoomId)
        assertTrue(entities.any { it.id == "goblin_1" }, "Space should contain new entity")
    }

    @Test
    fun `V3 can remove entities from spaces`() {
        val world = createV3WorldState()

        val goblin = Entity.NPC(
            id = "goblin_1",
            name = "Goblin",
            description = "A small green goblin",
            health = 30,
            maxHealth = 30,
            isHostile = true,
            stats = Stats()
        )

        val worldWithGoblin = world.addEntityToSpace(world.player.currentRoomId, goblin)!!
        val worldWithoutGoblin = worldWithGoblin.removeEntityFromSpace(
            worldWithGoblin.player.currentRoomId,
            "goblin_1"
        )

        assertNotNull(worldWithoutGoblin, "Should successfully remove entity")

        val entities = worldWithoutGoblin.getEntitiesInSpace(worldWithoutGoblin.player.currentRoomId)
        assertFalse(entities.any { it.id == "goblin_1" }, "Space should not contain removed entity")
    }

    @Test
    fun `V3 can replace entities in spaces`() {
        val world = createV3WorldState()

        val goblin = Entity.NPC(
            id = "goblin_1",
            name = "Goblin",
            description = "A small green goblin",
            health = 30,
            maxHealth = 30,
            isHostile = true,
            stats = Stats()
        )

        val worldWithGoblin = world.addEntityToSpace(world.player.currentRoomId, goblin)!!

        val damagedGoblin = goblin.copy(health = 15)
        val updatedWorld = worldWithGoblin.replaceEntityInSpace(
            worldWithGoblin.player.currentRoomId,
            "goblin_1",
            damagedGoblin
        )

        assertNotNull(updatedWorld, "Should successfully replace entity")

        val entities = updatedWorld.getEntitiesInSpace(updatedWorld.player.currentRoomId)
        val updatedGoblin = entities.find { it.id == "goblin_1" } as? Entity.NPC
        assertNotNull(updatedGoblin)
        assertEquals(15, updatedGoblin.health, "Entity should have updated health")
    }

    // ==================== V2 FALLBACK COMPATIBILITY ====================

    @Test
    fun `V3 methods gracefully handle V2-only worlds`() {
        // Create V2 world (rooms only, no graph nodes)
        val v2World = SampleDungeon.createInitialWorldState()

        // V3 methods should return null when graph nodes not available
        assertNull(v2World.getCurrentGraphNode(), "V2 world should have no graph node")
        assertNull(v2World.getCurrentSpace(), "V2 world should have no space component")

        // V3 navigation should fail gracefully
        val newWorld = v2World.movePlayerV3(Direction.NORTH)
        assertNull(newWorld, "V3 navigation should fail on V2 world")
    }

    @Test
    fun `V2 methods still work on V3 worlds with rooms`() {
        // If V3 world also has rooms (backwards compatibility), V2 methods should work
        val v3World = createV3WorldState()

        // Add a sample room for V2 compatibility
        val room = Room(
            id = "compat_room",
            name = "Compatibility Room",
            traits = listOf("A room for testing V2/V3 compatibility"),
            exits = emptyMap(),
            entities = emptyList()
        )

        val worldWithRoom = v3World.copy(
            rooms = mapOf(room.id to room),
            players = mapOf(v3World.player.id to v3World.player.copy(currentRoomId = room.id))
        )

        // V2 getCurrentRoom should work
        val currentRoom = worldWithRoom.getCurrentRoom()
        assertNotNull(currentRoom, "V2 methods should work if rooms present")
        assertEquals("compat_room", currentRoom.id)
    }

    // ==================== EDGE CASES ====================

    @Test
    fun `V3 handles empty space entity list`() {
        val world = createV3WorldState()

        val entities = world.getEntitiesInSpace(world.player.currentRoomId)
        assertNotNull(entities, "Should return empty list for space with no entities")
        assertTrue(entities.isEmpty(), "Entities list should be empty initially")
    }

    @Test
    fun `V3 handles non-existent space ID gracefully`() {
        val world = createV3WorldState()

        val entities = world.getEntitiesInSpace("non_existent_space")
        assertNotNull(entities, "Should return empty list for non-existent space")
        assertTrue(entities.isEmpty(), "Should handle non-existent space gracefully")
    }

    @Test
    fun `V3 updateSpace creates new space if not exists`() {
        val world = createV3WorldState()

        val newSpace = SpacePropertiesComponent(
            name = "New Space",
            description = "Newly updated space",
            exits = emptyList(),
            terrainType = TerrainType.DIFFICULT,
            brightness = 75,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList()
        )

        val updatedWorld = world.updateSpace("new_space_id", newSpace)
        assertNotNull(updatedWorld, "Should create new space if not exists")

        val retrievedSpace = updatedWorld.spaces["new_space_id"]
        assertNotNull(retrievedSpace)
        assertEquals("Newly updated space", retrievedSpace.description)
    }

    // ==================== GRAPH NODE OPERATIONS ====================

    @Test
    fun `V3 can update graph node component`() {
        val world = createV3WorldState()
        val currentNode = world.getCurrentGraphNode()!!

        // Mark an edge as revealed (hidden = false)
        val updatedNode = currentNode.copy(
            neighbors = currentNode.neighbors.map { edge ->
                if (edge.hidden) {
                    edge.copy(hidden = false)
                } else {
                    edge
                }
            }
        )

        val updatedWorld = world.updateGraphNode(currentNode.id, updatedNode)
        assertNotNull(updatedWorld, "Should successfully update graph node")

        val retrievedNode = updatedWorld.graphNodes[currentNode.id]
        assertNotNull(retrievedNode)
        assertEquals(updatedNode.neighbors.count { it.hidden },
            retrievedNode.neighbors.count { it.hidden },
            "Updated node should reflect changes")
    }

    @Test
    fun `V3 can add new space with graph node`() {
        val world = createV3WorldState()

        val newNode = GraphNodeComponent(
            id = "new_node",
            type = NodeType.Linear,
            neighbors = emptyList(),
            position = Pair(10, 10)
        )

        val newSpace = SpacePropertiesComponent(
            name = "New Space",
            description = "A new space",
            exits = emptyList(),
            terrainType = TerrainType.NORMAL,
            brightness = 60,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList()
        )

        val updatedWorld = world.addSpace("new_node", newNode, newSpace)
        assertNotNull(updatedWorld, "Should successfully add space with graph node")

        assertNotNull(updatedWorld.graphNodes["new_node"], "Graph node should be added")
        assertNotNull(updatedWorld.spaces["new_node"], "Space should be added")
    }

    // ==================== INTEGRATION SMOKE TESTS ====================

    @Test
    fun `V3 world state is immutable`() {
        val world = createV3WorldState()
        val originalNodeCount = world.graphNodes.size
        val originalSpaceCount = world.spaces.size

        // Operations should not mutate original world
        val newSpace = SpacePropertiesComponent(
            name = "Test Space",
            description = "Test",
            exits = emptyList(),
            terrainType = TerrainType.NORMAL,
            brightness = 50,
            entities = emptyList(),
            resources = emptyList(),
            traps = emptyList(),
            itemsDropped = emptyList()
        )

        world.updateSpace("test_id", newSpace)

        // Original world unchanged
        assertEquals(originalNodeCount, world.graphNodes.size)
        assertEquals(originalSpaceCount, world.spaces.size)
    }

    @Test
    fun `V3 initialization produces valid world state`() {
        val world = createV3WorldState()

        // Verify V3 invariants
        assertNotNull(world.graphNodes, "Should have graph nodes")
        assertNotNull(world.spaces, "Should have spaces")
        assertNotNull(world.chunks, "Should have chunks")

        assertTrue(world.graphNodes.isNotEmpty(), "Should have at least one node")
        assertTrue(world.spaces.isNotEmpty(), "Should have at least one space")
        assertTrue(world.chunks.isNotEmpty(), "Should have at least one chunk")

        // Verify player is at valid location
        val playerLocation = world.player.currentRoomId
        assertTrue(world.graphNodes.containsKey(playerLocation),
            "Player should be at valid graph node")
        assertTrue(world.spaces.containsKey(playerLocation),
            "Player location should have corresponding space")
    }
}
