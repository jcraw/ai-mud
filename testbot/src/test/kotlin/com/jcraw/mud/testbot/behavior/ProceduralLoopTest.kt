package com.jcraw.mud.testbot.behavior

import com.jcraw.mud.core.*
import com.jcraw.mud.testbot.exploration.GraphExplorer
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertEquals
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
    fun `Deterministic explorer finds a loop in the world`() = runTest {
        println("\n" + "=".repeat(60))
        println("TEST: Graph Explorer Finds Loops Using Right-Hand Rule")
        println("=".repeat(60) + "\n")

        // Record starting position
        val startingRoomId = worldState().player.currentRoomId
        val startingSpace = worldState().getCurrentSpace()

        println("📍 Starting Position: ${startingSpace?.name} (ID: $startingRoomId)")
        println("   Description: ${startingSpace?.description?.take(100)}...")
        println()

        // Create deterministic explorer
        val explorer = GraphExplorer.rightHandRule(startingRoomId)

        // Track exploration
        val visitedRoomsList = mutableListOf<SpaceId>()
        visitedRoomsList.add(startingRoomId)

        var loopFound = false
        var finalPath: List<String> = emptyList()

        println("🤖 Right-hand rule explorer begins...")
        println("   Goal: Find a path back to starting room $startingRoomId")
        println("   Strategy: Always prefer rightmost available exit")
        println()

        // Exploration loop
        var moveNum = 0
        while (moveNum < 100) {  // Safety limit
            moveNum++
            println("─".repeat(60))
            println("Move #$moveNum")

            // Get current state
            val currentRoomId = worldState().player.currentRoomId
            val currentSpace = worldState().getCurrentSpace()

            println("📍 Current: ${currentSpace?.name} (ID: $currentRoomId)")

            // Get available exits
            val exits = currentSpace?.exits?.map { it.direction } ?: emptyList()
            if (exits.isEmpty()) {
                println("⚠️  Dead end - no exits available")
                break
            }

            println("   Available exits: ${exits.joinToString(", ")}")

            // Ask explorer for next move
            val step = explorer.nextMove(currentSpace, currentRoomId, exits)

            when (step) {
                is GraphExplorer.ExplorationStep.Move -> {
                    println("   🤖 Explorer chooses: ${step.direction}")

                    // Execute move
                    val output = command("go ${step.direction}")

                    // Track room visits
                    val newRoomId = worldState().player.currentRoomId
                    visitedRoomsList.add(newRoomId)

                    // Log result (abbreviated)
                    val resultPreview = output.lines().firstOrNull() ?: output
                    println("   Result: ${resultPreview.take(80)}${if (resultPreview.length > 80) "..." else ""}")
                    println()

                    // Small delay for readability
                    delay(100)
                }

                is GraphExplorer.ExplorationStep.LoopFound -> {
                    println()
                    println("✅ LOOP FOUND! Returned to starting room after ${step.pathTaken.size} moves!")
                    println("   Path taken: ${step.pathTaken.joinToString(" → ")}")
                    loopFound = true
                    finalPath = step.pathTaken
                    break
                }

                is GraphExplorer.ExplorationStep.Failed -> {
                    println()
                    println("❌ EXPLORATION FAILED: ${step.reason}")
                    break
                }
            }
        }

        // Final results
        println("=".repeat(60))
        println("TEST RESULTS")
        println("=".repeat(60))
        println("Starting room: $startingRoomId")
        println("Final room: ${worldState().player.currentRoomId}")
        println("Moves taken: ${finalPath.size}")
        println("Path: ${finalPath.joinToString(" → ")}")
        println("Rooms explored: ${visitedRoomsList.size}")
        println("Valid loop found: ${if (loopFound) "✅ YES" else "❌ NO"}")
        println("=".repeat(60) + "\n")

        // Assert success - must find loop
        assertTrue(
            loopFound,
            "Expected explorer to find a loop back to starting room $startingRoomId " +
                "but ended at ${worldState().player.currentRoomId} after ${moveNum} moves. " +
                "This suggests the world graph does not contain a loop."
        )

        // Verify the loop actually worked
        assertEquals(
            startingRoomId,
            worldState().player.currentRoomId,
            "Room ID should match starting room after loop"
        )
    }

}
