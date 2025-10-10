package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates NPC dialogue and interactions using LLM with conversation history
 */
class NPCInteractionGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null
) {

    /**
     * Generate dialogue response from an NPC with conversation history
     */
    suspend fun generateDialogue(npc: Entity.NPC, player: PlayerState): String {
        // Retrieve past interactions with this NPC
        val memories = memoryManager?.recall("conversation with ${npc.name}", k = 3) ?: emptyList()

        val systemPrompt = buildSystemPrompt()
        val userContext = buildUserContext(npc, player, memories)

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",  // Cost-effective model
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 150,
                temperature = 0.9  // Higher temperature for varied, creative dialogue
            )

            val dialogue = response.choices.firstOrNull()?.message?.content?.trim()
                ?: fallbackDialogue(npc)

            // Store this conversation in memory
            memoryManager?.remember(
                "Conversation with ${npc.name}: ${npc.name} said: \"$dialogue\"",
                mapOf("type" to "conversation", "npc" to npc.name)
            )

            dialogue
        } catch (e: Exception) {
            println("⚠️ LLM dialogue generation failed: ${e.message}")
            fallbackDialogue(npc)
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a dialogue generator for NPCs in a text-based adventure game (MUD).

        Your task is to create engaging, in-character dialogue for NPCs.

        Guidelines:
        - Write in the NPC's voice, staying true to their nature
        - Keep responses to 1-3 sentences
        - Be creative and atmospheric
        - If hostile, the NPC should be menacing or dismissive
        - If friendly, the NPC should be welcoming or helpful
        - Use the NPC's description and properties to inform personality
        - Do NOT break character or provide meta-information
        - If conversation history is provided, maintain continuity
        - Avoid repeating exact phrases from past conversations

        Example hostile NPC: "Begone, mortal! Your presence defiles this sacred throne."
        Example friendly NPC: "Welcome, traveler. You look weary from your journey."
    """.trimIndent()

    private fun buildUserContext(npc: Entity.NPC, player: PlayerState, memories: List<String>): String {
        val hostilityNote = if (npc.isHostile) "HOSTILE" else "FRIENDLY"
        val healthStatus = when {
            npc.health < npc.maxHealth * 0.3 -> "severely wounded"
            npc.health < npc.maxHealth * 0.7 -> "injured"
            else -> "healthy"
        }

        val historySection = if (memories.isNotEmpty()) {
            "\n\nPast conversations:\n${memories.joinToString("\n") { "- $it" }}"
        } else {
            ""
        }

        return """
            NPC: ${npc.name}
            Description: ${npc.description}
            Disposition: $hostilityNote
            Condition: $healthStatus (${npc.health}/${npc.maxHealth} HP)

            Player: ${player.name}
            Player Health: ${player.health}/${player.maxHealth} HP$historySection

            The player approaches and tries to talk to the NPC.
            Generate the NPC's response.
        """.trimIndent()
    }

    /**
     * Fallback to simple description if LLM fails
     */
    private fun fallbackDialogue(npc: Entity.NPC): String {
        return if (npc.isHostile) {
            "${npc.name} glares at you menacingly and says nothing."
        } else {
            "${npc.name} nods at you in acknowledgment."
        }
    }
}
