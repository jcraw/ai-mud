package com.jcraw.app

import com.jcraw.mud.core.GameEngineInterface
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.perception.Intent
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * Adapter that wraps the real MudGame engine for use in tests.
 * Captures console output and exposes it as return values.
 *
 * This eliminates the need for InMemoryGameEngine - the testbot now uses
 * the same production code path as real players, preventing architectural drift.
 */
class RealGameEngineAdapter(
    private val mudGame: MudGame
) : GameEngineInterface {

    private var running = true

    override suspend fun processInput(input: String): String {
        if (!running) return "Game is not running."

        // Capture stdout while processing the input
        return captureOutput {
            // Parse intent (same as real game)
            val space = mudGame.worldState.getCurrentSpace()
            val spaceContext = space?.let {
                val desc = if (it.description.isNotBlank()) it.description else "Unexplored area"
                "${it.name}: $desc"
            }
            val exitsWithNames = mudGame.worldState.getCurrentGraphNode()?.let {
                mudGame.buildExitsWithNames(it)
            }

            val intent = mudGame.intentRecognizer.parseIntent(input, spaceContext, exitsWithNames)

            // Process intent (this calls handlers which print to stdout)
            mudGame.processIntent(intent)

            // Don't process time/NPCs for Quit intent
            if (intent is Intent.Quit) {
                running = false
                return@captureOutput
            }

            // Advance game time (Combat V2)
            val speedLevel = mudGame.skillManager.getSkillComponent(mudGame.worldState.player.id)
                ?.getEffectiveLevel("Speed") ?: 0
            val baseCost = mudGame.getBaseCostForIntent(intent)
            val actionCost = com.jcraw.mud.reasoning.combat.ActionCosts.calculateCost(baseCost, speedLevel)
            mudGame.worldState = mudGame.worldState.advanceTime(actionCost)

            // Process NPC turns (Combat V2)
            mudGame.processNPCTurns()
        }
    }

    override fun getWorldState(): WorldState {
        return mudGame.worldState
    }

    override fun reset() {
        mudGame.worldState = mudGame.initialWorldState
        running = true
    }

    override fun isRunning(): Boolean {
        // Check adapter's running flag, mudGame's running flag, and player health
        if (!running) return false
        if (!mudGame.running) {
            running = false
            return false
        }
        if (mudGame.worldState.player.health <= 0) {
            running = false
            return false
        }
        return true
    }

    /**
     * Get the skill manager for testing/validation purposes.
     */
    fun getSkillManager(): com.jcraw.mud.reasoning.skill.SkillManager {
        return mudGame.skillManager
    }

    /**
     * Capture stdout during execution of a block.
     * Returns the captured output as a string.
     */
    private fun captureOutput(block: suspend () -> Unit): String {
        val originalOut = System.out
        val baos = ByteArrayOutputStream()
        val captureStream = PrintStream(baos, true, "UTF-8")

        return try {
            System.setOut(captureStream)
            runBlocking { block() }
            baos.toString("UTF-8").trim()
        } finally {
            System.setOut(originalOut)
            captureStream.close()
        }
    }
}
