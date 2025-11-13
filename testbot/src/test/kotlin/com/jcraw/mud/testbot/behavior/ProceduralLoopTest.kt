package com.jcraw.mud.testbot.behavior

import com.jcraw.mud.core.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertTrue

/**
 * Test that V3 worlds contain navigable loops.
 *
 * A deterministic explorer traverses the world trying to find a path
 * that returns to the starting room. This validates:
 * - Graph topology has loops
 * - Navigation system works correctly
 * - Room IDs remain consistent through navigation
 * - World is fully connected
 *
 * Success condition: Player finds ANY path that returns to starting room ID.
 * Example: go north, east, south, west -> back at start (square loop)
 *
 * Uses right-hand rule algorithm for deterministic exploration without LLM.
 *
 * Note: Uses SampleDungeon which has V3 structure. For procedural worlds,
 * world generation API needs to be integrated separately.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProceduralLoopTest : BehaviorTestBase() {

    // This test uses deterministic exploration - no LLM needed
    override val requiresLLM: Boolean = false

    // Store seed for current test iteration
    private var currentSeed: Int = 0

    /**
     * Generate a procedural V3 world with high loop frequency.
     * This ensures the test has actual graph cycles to find.
     */
    override fun createInitialWorldState(): WorldState {
        println("\n=== Generating Procedural World with Loops ===")

        // Use the current seed (set by test method)
        val rng = kotlin.random.Random(currentSeed)
        println("Using seed: $currentSeed")

        val graphGenerator = com.jcraw.mud.reasoning.worldgen.GraphGenerator(rng, difficultyLevel = 1)

        // Create layout with MAX loop frequency to guarantee loops
        // Use smaller grid for faster loop finding
        val layout = com.jcraw.mud.reasoning.worldgen.GraphLayout.Grid(
            width = 3,
            height = 3,
            loopFrequency = 1.0  // Maximum loops for reliable testing
        )

        println("Layout: ${layout.width}x${layout.height} grid, loopFrequency=1.0")

        // Generate graph topology
        val graphNodes = graphGenerator.generate("test_chunk", layout)
        println("Generated ${graphNodes.size} nodes")

        // Validate graph has loops
        val validator = com.jcraw.mud.reasoning.worldgen.GraphValidator()
        val validation = validator.validate(graphNodes)
        if (validation !is com.jcraw.mud.reasoning.worldgen.ValidationResult.Success) {
            throw IllegalStateException("Generated world failed validation: $validation")
        }

        // Check for cycles using DFS
        val hasCycles = detectCycle(graphNodes)
        println("Graph has cycles: ${if (hasCycles) "✅ YES" else "❌ NO"}")

        if (!hasCycles) {
            throw IllegalStateException("Generated world does not have cycles - cannot test loop finding")
        }

        // Create minimal space stubs for each node
        // Only include non-hidden edges in exits (hidden edges need to be discovered first)
        val spaces = graphNodes.associate { node ->
            node.id to SpacePropertiesComponent(
                name = "Node ${node.type}",
                description = "Test node ${node.id} of type ${node.type}. Part of a ${layout.width}x${layout.height} grid.",
                exits = node.neighbors
                    .filter { edge -> !edge.hidden }  // Skip hidden edges for test simplicity
                    .map { edge ->
                        com.jcraw.mud.core.world.ExitData(
                            targetId = edge.targetId,
                            direction = edge.direction,
                            description = "A passage ${edge.direction}"
                        )
                    },
                terrainType = com.jcraw.mud.core.world.TerrainType.NORMAL,
                brightness = 50,
                entities = emptyList(),
                resources = emptyList(),
                traps = emptyList(),
                itemsDropped = emptyList()
            )
        }

        // Create chunk component
        val chunk = WorldChunkComponent(
            level = com.jcraw.mud.core.world.ChunkLevel.SUBZONE,
            parentId = null,
            children = emptyList(),
            lore = "Test chunk for loop finding",
            biomeTheme = "Test Dungeon",
            sizeEstimate = graphNodes.size,
            mobDensity = 0.0,
            difficultyLevel = 1
        )

        // Find Hub node and place player there
        val hubNode = graphNodes.first { it.type == com.jcraw.mud.core.world.NodeType.Hub }
        val player = PlayerState(
            id = "test_player",
            name = "Loop Explorer",
            currentRoomId = hubNode.id
        )

        var worldState = WorldState(
            graphNodes = graphNodes.associateBy { it.id },
            spaces = spaces,
            chunks = mapOf("test_chunk" to chunk),
            entities = emptyMap(),
            players = mapOf(player.id to player)
        )

        // Set starting space in game properties
        worldState = worldState.copy(
            gameProperties = worldState.gameProperties + ("starting_space" to player.currentRoomId)
        )

        val startSpace = worldState.getCurrentSpace()
        println("Player starting at: ${startSpace?.name} (${player.currentRoomId})")
        println("Graph topology validated with loops present")
        println("=== Setup Complete ===\n")

        return worldState
    }

    /**
     * Detect if graph contains cycles using DFS.
     * Returns true if at least one cycle exists.
     */
    private fun detectCycle(graphNodes: List<GraphNodeComponent>): Boolean {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()

        fun dfs(nodeId: String): Boolean {
            if (recursionStack.contains(nodeId)) {
                return true  // Cycle detected
            }
            if (visited.contains(nodeId)) {
                return false  // Already visited
            }

            visited.add(nodeId)
            recursionStack.add(nodeId)

            val node = graphNodes.find { it.id == nodeId }
            if (node != null) {
                for (edge in node.neighbors) {
                    if (dfs(edge.targetId)) {
                        return true
                    }
                }
            }

            recursionStack.remove(nodeId)
            return false
        }

        for (node in graphNodes) {
            if (!visited.contains(node.id)) {
                if (dfs(node.id)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Find a loop path from start position back to itself using BFS.
     * Returns list of directions to navigate the loop, or null if no loop found.
     * Excludes trivial back-and-forth loops (e.g., east→west).
     */
    private fun findLoopPath(startingRoomId: String, minLoopSize: Int = 4, maxDepth: Int = 10): List<String>? {
        data class SearchNode(
            val roomId: String,
            val path: List<String>,
            val visitedRooms: Set<String>
        )

        val queue = ArrayDeque<SearchNode>()

        // Get starting exits
        val startSpace = worldState().spaces[startingRoomId]
        val startExits = startSpace?.exits ?: return null

        // Try each initial direction
        for (exit in startExits) {
            queue.add(SearchNode(
                roomId = exit.targetId,
                path = listOf(exit.direction),
                visitedRooms = setOf(startingRoomId, exit.targetId)
            ))
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            // Found a loop! Must be at least minLoopSize to avoid trivial backtracking
            if (current.roomId == startingRoomId && current.path.size >= minLoopSize) {
                return current.path
            }

            // Don't explore too deep
            if (current.path.size >= maxDepth) {
                continue
            }

            // Explore neighbors
            val space = worldState().spaces[current.roomId]
            val exits = space?.exits ?: continue

            for (exit in exits) {
                val nextRoomId = exit.targetId

                // Allow returning to start only if we have enough moves
                if (nextRoomId == startingRoomId) {
                    if (current.path.size >= minLoopSize - 1) {
                        queue.add(SearchNode(
                            roomId = nextRoomId,
                            path = current.path + exit.direction,
                            visitedRooms = current.visitedRooms + nextRoomId
                        ))
                    }
                } else if (nextRoomId !in current.visitedRooms) {
                    // Only visit new rooms (except for returning to start)
                    queue.add(SearchNode(
                        roomId = nextRoomId,
                        path = current.path + exit.direction,
                        visitedRooms = current.visitedRooms + nextRoomId
                    ))
                }
            }
        }

        return null
    }

    @Test
    fun `Generated world with high loop frequency has navigable cycles`() = runTest {
        // Test with multiple random seeds to ensure robustness
        val seeds = listOf(12345, 54321, 99999, 11111, 42424)
        val successfulTests = mutableListOf<String>()

        println("\n" + "=".repeat(60))
        println("TEST: Verify Spatial Consistency Across Multiple Graphs")
        println("Testing with ${seeds.size} different random seeds")
        println("=".repeat(60))

        for ((iteration, seed) in seeds.withIndex()) {
            println("\n" + "-".repeat(60))
            println("ITERATION ${iteration + 1}/${seeds.size} - Seed: $seed")
            println("-".repeat(60))

            // Set seed and recreate engine with new world
            currentSeed = seed
            engine = createEngine()

            // Record starting position
            val startingRoomId = worldState().player.currentRoomId
            val startingSpace = worldState().getCurrentSpace()

            println("📍 Starting Position: ${startingSpace?.name} (ID: $startingRoomId)")

            // Test 1: Verify graph has cycles
            val graphNodes = worldState().graphNodes.values.toList()
            val hasCycles = detectCycle(graphNodes)
            assertTrue(hasCycles, "Graph should contain cycles with loopFrequency=1.0")

            // Test 2: Find and navigate any loop path
            println("🤖 Searching for a navigable loop using BFS...")

            val loopPath = findLoopPath(startingRoomId)

            if (loopPath == null) {
                println("❌ Could not find a loop path within search depth")
                throw AssertionError("Graph has cycles but starting node is not in a cycle (seed: $seed)")
            }

            println("✅ Found loop path with ${loopPath.size} moves: ${loopPath.joinToString(" → ")}")

            // Test 3: Execute the loop and verify we return to start
            val movements = mutableListOf<String>()
            for ((index, direction) in loopPath.withIndex()) {
                val oldRoom = worldState().player.currentRoomId
                val output = command("go $direction")
                val newRoom = worldState().player.currentRoomId

                movements.add("$oldRoom → $direction → $newRoom")
                assertTrue(output.isNotEmpty(), "Move ${index + 1} should produce output")
            }

            val finalRoomId = worldState().player.currentRoomId

            // Verify spatial consistency
            if (finalRoomId == startingRoomId) {
                println("✅ Loop completed! Returned to starting position")
                successfulTests.add("Seed $seed: ${loopPath.size}-move loop")
            } else {
                println("❌ Spatial inconsistency detected!")
                throw AssertionError("Loop path did not return to start (seed: $seed). Expected: $startingRoomId, Got: $finalRoomId")
            }
        }

        // Final summary
        println("\n" + "=".repeat(60))
        println("FINAL TEST RESULTS")
        println("=".repeat(60))
        println("✅ All ${seeds.size} iterations passed!")
        println("\nSuccessful loop tests:")
        successfulTests.forEach { println("  • $it") }
        println("\n✅ Spatial consistency verified across multiple graph topologies")
        println("✅ Navigation system is spatially coherent")
        println("=".repeat(60) + "\n")

        println("All assertions passed!")
    }

}
