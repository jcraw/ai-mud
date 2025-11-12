package com.jcraw.mud.testbot.behavior

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.InMemoryGameEngine
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Test that V3 worlds contain navigable loops.
 *
 * An LLM-controlled player explores the world trying to find a path
 * that returns to the starting room. This validates:
 * - Graph topology has loops
 * - Navigation system works correctly
 * - Room IDs remain consistent through navigation
 * - World is fully connected
 *
 * Success condition: Player finds ANY path that returns to starting room ID.
 * Example: go north, east, south, west -> back at start (square loop)
 *
 * Note: Uses SampleDungeon which has V3 structure. For procedural worlds,
 * world generation API needs to be integrated separately.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProceduralLoopTest : BehaviorTestBase() {

    // This test REQUIRES LLM for decision making
    override val requiresLLM: Boolean = true

    /**
     * Use default SampleDungeon (has V3 structure with multiple connected rooms).
     */
    override fun createInitialWorldState(): WorldState {
        println("\n=== Using Sample Dungeon (V3 Structure) ===")
        val worldState = SampleDungeon.createInitialWorldState()

        val startSpace = worldState.getCurrentSpace()
        println("Player starting at: ${startSpace?.name} (${worldState.player.currentRoomId})")
        println("Sample dungeon has multiple rooms to explore")
        println("=== Setup Complete ===\n")

        return worldState
    }

    @Test
    fun `LLM player explores world and finds a loop`() = runTest {
        println("\n" + "=".repeat(60))
        println("TEST: LLM Player Explores World for Loops")
        println("=".repeat(60) + "\n")

        // Record starting position
        val startingRoomId = worldState().player.currentRoomId
        val startingSpace = worldState().getCurrentSpace()

        println("📍 Starting Position: ${startingSpace?.name} (ID: $startingRoomId)")
        println("   Description: ${startingSpace?.description?.take(100)}...")
        println()

        // Track exploration
        val visitedRooms = mutableSetOf<SpaceId>()  // Set to detect revisits
        val visitedRoomsList = mutableListOf<SpaceId>()  // List to track order
        val pathTaken = mutableListOf<String>()
        visitedRooms.add(startingRoomId)
        visitedRoomsList.add(startingRoomId)

        var loopFound = false
        var backtracked = false
        val maxMoves = 20  // Limit exploration to 20 moves

        println("🤖 LLM Agent begins exploration...")
        println("   Goal: Find a path back to starting room $startingRoomId")
        println("   Rule: No backtracking - must visit unique rooms only")
        println()

        // Exploration loop
        for (moveNum in 1..maxMoves) {
            println("─".repeat(60))
            println("Move #$moveNum")

            // Get current state
            val currentRoomId = worldState().player.currentRoomId
            val currentSpace = worldState().getCurrentSpace()

            println("📍 Current: ${currentSpace?.name} (ID: $currentRoomId)")

            // Check if we've looped back to start
            if (moveNum > 1 && currentRoomId == startingRoomId) {
                println()

                // Check if this is just an immediate reversal (2 moves)
                if (pathTaken.size == 2) {
                    val first = pathTaken[0]
                    val second = pathTaken[1]
                    if (second == getOppositeDirection(first)) {
                        println("❌ BACKTRACKING DETECTED! Path ${first} → ${second} is just reversing direction.")
                        println("   This is not a valid loop - just went out and back.")
                        backtracked = true
                        break
                    }
                }

                // Check for any direction reversals in the path
                var hasReversal = false
                for (i in 0 until pathTaken.size - 1) {
                    if (pathTaken[i + 1] == getOppositeDirection(pathTaken[i])) {
                        hasReversal = true
                        println("❌ BACKTRACKING DETECTED! Path contains reversal: ${pathTaken[i]} → ${pathTaken[i + 1]}")
                        println("   Full path: ${pathTaken.joinToString(" → ")}")
                        backtracked = true
                        break
                    }
                }

                if (backtracked) {
                    break
                }

                println("✅ LOOP FOUND! Returned to starting room after ${moveNum - 1} moves!")
                println("   Path taken: ${pathTaken.joinToString(" → ")}")
                println("   Unique rooms in loop: ${visitedRooms.size}")
                loopFound = true
                break
            }

            // Check for backtracking (visiting a room we've already been to, excluding start)
            if (moveNum > 1 && visitedRooms.contains(currentRoomId)) {
                println()
                println("❌ BACKTRACKING DETECTED! Visited room $currentRoomId again.")
                println("   This is not a proper loop - just retracing steps.")
                println("   Path taken: ${pathTaken.joinToString(" → ")}")
                println("   Rooms visited: ${visitedRoomsList.joinToString(" → ")}")
                backtracked = true
                break
            }

            visitedRooms.add(currentRoomId)
            visitedRoomsList.add(currentRoomId)

            // Get available exits
            val exits = currentSpace?.exits ?: emptyList()
            if (exits.isEmpty()) {
                println("⚠️  Dead end - no exits available")
                break
            }

            println("   Available exits: ${exits.map { it.direction }.joinToString(", ")}")

            // Ask LLM to choose next direction
            val direction = chooseMoveWithLLM(
                currentSpace = currentSpace,
                visitedRooms = visitedRooms.toList(),
                pathTaken = pathTaken,
                startingRoomId = startingRoomId,
                moveNum = moveNum
            )

            println("   🤖 LLM chooses: $direction")

            // Check for immediate direction reversal (backtracking)
            if (pathTaken.isNotEmpty()) {
                val lastDirection = pathTaken.last()
                val oppositeDirection = getOppositeDirection(lastDirection)
                if (direction == oppositeDirection) {
                    println("   ⚠️  Warning: Reversing direction ($lastDirection → $direction)")
                }
            }

            // Execute move
            val output = command("go $direction")
            pathTaken.add(direction)

            // Log result (abbreviated)
            val resultPreview = output.lines().firstOrNull() ?: output
            println("   Result: ${resultPreview.take(80)}${if (resultPreview.length > 80) "..." else ""}")
            println()

            // Small delay for readability
            delay(100)
        }

        // Final results
        println("=".repeat(60))
        println("TEST RESULTS")
        println("=".repeat(60))
        println("Starting room: $startingRoomId")
        println("Final room: ${worldState().player.currentRoomId}")
        println("Moves taken: ${pathTaken.size}")
        println("Path: ${pathTaken.joinToString(" → ")}")
        println("Unique rooms visited: ${visitedRooms.size}")
        println("Backtracking occurred: ${if (backtracked) "❌ YES" else "✅ NO"}")
        println("Valid loop found: ${if (loopFound && !backtracked) "✅ YES" else "❌ NO"}")
        println("=".repeat(60) + "\n")

        // Assert success - must find loop AND not backtrack
        assertFalse(
            backtracked,
            "Player backtracked by revisiting a room. This is not a valid loop. " +
                "Path: ${pathTaken.joinToString(" → ")} " +
                "Rooms: ${visitedRoomsList.joinToString(" → ")}"
        )

        assertTrue(
            loopFound,
            "Expected LLM player to find a loop back to starting room $startingRoomId " +
                "but ended at ${worldState().player.currentRoomId} after ${pathTaken.size} moves. " +
                "Path: ${pathTaken.joinToString(" → ")}"
        )

        // Verify the loop actually worked
        assertEquals(
            startingRoomId,
            worldState().player.currentRoomId,
            "Room ID should match starting room after loop"
        )
    }

    /**
     * Use LLM to choose next movement direction.
     * Strategy: Try to form a loop by tracking visited rooms and attempting to return home.
     */
    private suspend fun chooseMoveWithLLM(
        currentSpace: SpacePropertiesComponent?,
        visitedRooms: List<SpaceId>,
        pathTaken: List<String>,
        startingRoomId: SpaceId,
        moveNum: Int
    ): String {
        val exits = currentSpace?.exits?.map { it.direction } ?: emptyList()

        if (exits.isEmpty()) {
            return "north"  // Fallback (should not happen)
        }

        // Build prompt for LLM
        val prompt = buildString {
            appendLine("You are an explorer in a procedurally generated dungeon.")
            appendLine("Your GOAL: Find a path that loops back to the starting room (ID: $startingRoomId).")
            appendLine()
            appendLine("Current status:")
            appendLine("- Current room: ${currentSpace?.name} (ID: ${worldState().player.currentRoomId})")
            appendLine("- Move number: $moveNum")
            appendLine("- Path so far: ${if (pathTaken.isEmpty()) "(start)" else pathTaken.joinToString(" → ")}")
            appendLine("- Unique rooms visited: ${visitedRooms.size} locations")
            appendLine()
            appendLine("Available exits from current room: ${exits.joinToString(", ")}")
            appendLine()
            appendLine("Strategy hints:")
            appendLine("- Try to form loops (e.g., if you went north, try south later)")
            appendLine("- After exploring ~4-6 moves, try to reverse your path")
            appendLine("- Look for opposite directions to return")
            appendLine()
            appendLine("Choose ONE direction from: ${exits.joinToString(", ")}")
            appendLine("Respond with ONLY the direction word (e.g., 'north'), no explanation.")
        }

        // Call LLM using actual API
        val response = llmClient?.chatCompletion(
            modelId = "gpt-4o-mini",
            systemPrompt = "You are a dungeon explorer trying to find loops in a world. " +
                "Make strategic navigation choices to return to your starting point. " +
                "Respond with only a single direction word.",
            userContext = prompt,
            maxTokens = 10,
            temperature = 0.7  // Some randomness for exploration
        )?.choices?.firstOrNull()?.message?.content ?: return exits.first()

        // Parse LLM response (extract direction word)
        val chosenDirection = response.lowercase().trim()
            .split(Regex("\\s+"))
            .firstOrNull { it in exits.map { e -> e.lowercase() } }
            ?: exits.first()  // Fallback to first exit if LLM gives invalid response

        return chosenDirection
    }

    /**
     * Get the opposite direction for detecting backtracking.
     */
    private fun getOppositeDirection(direction: String): String {
        return when (direction.lowercase()) {
            "north", "n" -> "south"
            "south", "s" -> "north"
            "east", "e" -> "west"
            "west", "w" -> "east"
            "northeast", "ne" -> "southwest"
            "southwest", "sw" -> "northeast"
            "northwest", "nw" -> "southeast"
            "southeast", "se" -> "northwest"
            "up", "u" -> "down"
            "down", "d" -> "up"
            else -> ""
        }
    }
}
