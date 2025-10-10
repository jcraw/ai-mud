package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates vivid, atmospheric combat narratives using LLM with combat history.
 * Transforms simple combat mechanics into engaging descriptive text.
 */
class CombatNarrator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null
) {

    /**
     * Narrates a combat round with player attack and enemy counter-attack.
     */
    suspend fun narrateCombatRound(
        worldState: WorldState,
        npc: Entity.NPC,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean,
        playerDied: Boolean
    ): String {
        val room = worldState.getCurrentRoom() ?: return "You fight in darkness..."

        // Retrieve past combat encounters for context
        val memories = memoryManager?.recall("combat with ${npc.name}", k = 2) ?: emptyList()

        val systemPrompt = """
            You are a dungeon master narrating a turn-based combat encounter.
            Create vivid, atmospheric combat descriptions that bring the action to life.
            Keep descriptions concise (2-3 sentences) but evocative.
            Focus on the visceral details of combat - the clash of steel, the grunt of effort, the spray of blood.
            If past combat history is provided, build on it to show progression of the fight.
        """.trimIndent()

        val userContext = buildString {
            appendLine("Combat Round:")
            appendLine("Location: ${room.name}")
            appendLine("Room atmosphere: ${room.traits.joinToString(", ")}")
            appendLine()
            appendLine("Combatants:")
            appendLine("- Player (${worldState.player.name}): Health ${worldState.player.health}/${worldState.player.maxHealth}")
            appendLine("- Enemy (${npc.name}): ${npc.description}")
            appendLine()
            if (memories.isNotEmpty()) {
                appendLine("Previous combat rounds:")
                memories.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("Actions this round:")
            appendLine("1. Player attacks ${npc.name} for $playerDamage damage")
            if (!npcDied) {
                appendLine("2. ${npc.name} counter-attacks for $npcDamage damage")
            }
            appendLine()
            if (npcDied) {
                appendLine("Result: ${npc.name} is defeated!")
            } else if (playerDied) {
                appendLine("Result: ${worldState.player.name} is defeated!")
            } else {
                appendLine("Result: Combat continues")
            }
            appendLine()
            appendLine("Narrate this combat round in 2-3 vivid sentences. Do not include damage numbers or game mechanics in the narrative.")
        }

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 150,
                temperature = 0.8
            )
            val narrative = response.choices.firstOrNull()?.message?.content?.trim()
                ?: buildFallbackNarrative(npc.name, playerDamage, npcDamage, npcDied, playerDied)

            // Store this combat round in memory
            memoryManager?.remember(
                "Combat with ${npc.name}: $narrative",
                mapOf("type" to "combat", "npc" to npc.name)
            )

            narrative
        } catch (e: Exception) {
            // Fallback to simple narrative
            buildFallbackNarrative(npc.name, playerDamage, npcDamage, npcDied, playerDied)
        }
    }

    /**
     * Narrates the initiation of combat.
     */
    suspend fun narrateCombatStart(worldState: WorldState, npc: Entity.NPC): String {
        val room = worldState.getCurrentRoom() ?: return "Combat begins..."

        val systemPrompt = """
            You are a dungeon master narrating the start of a combat encounter.
            Create a tense, atmospheric description as combat begins.
            Keep it brief (1-2 sentences) but set the mood.
        """.trimIndent()

        val userContext = buildString {
            appendLine("Combat Starting:")
            appendLine("Location: ${room.name}")
            appendLine("Atmosphere: ${room.traits.joinToString(", ")}")
            appendLine("Enemy: ${npc.name} - ${npc.description}")
            appendLine("Enemy disposition: ${if (npc.isHostile) "Hostile" else "Provoked"}")
            appendLine()
            appendLine("Narrate the moment combat begins in 1-2 sentences.")
        }

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 100,
                temperature = 0.8
            )
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: (if (npc.isHostile) {
                    "${npc.name} attacks! Steel rings as you draw your weapon!"
                } else {
                    "You engage ${npc.name} in combat!"
                })
        } catch (e: Exception) {
            if (npc.isHostile) {
                "${npc.name} attacks! Steel rings as you draw your weapon!"
            } else {
                "You engage ${npc.name} in combat!"
            }
        }
    }

    /**
     * Simple fallback narrative without LLM.
     */
    private fun buildFallbackNarrative(
        npcName: String,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean,
        playerDied: Boolean
    ): String = buildString {
        append("You strike $npcName for $playerDamage damage! ")
        when {
            npcDied -> append("$npcName falls defeated!")
            playerDied -> append("$npcName's counter-attack for $npcDamage damage strikes you down!")
            else -> append("$npcName retaliates for $npcDamage damage!")
        }
    }
}
