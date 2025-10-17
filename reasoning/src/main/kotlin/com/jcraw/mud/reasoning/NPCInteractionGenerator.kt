package com.jcraw.mud.reasoning

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates NPC dialogue and interactions using LLM with conversation history and disposition awareness
 */
class NPCInteractionGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null,
    private val dispositionManager: DispositionManager? = null
) {

    /**
     * Generate dialogue response from an NPC with conversation history and disposition awareness
     */
    suspend fun generateDialogue(npc: Entity.NPC, player: PlayerState): String {
        // Retrieve past interactions with this NPC
        val memories = memoryManager?.recall("conversation with ${npc.name}", k = 3) ?: emptyList()

        // Get disposition tone if available
        val dispositionTone = dispositionManager?.getDialogueTone(npc)

        val systemPrompt = buildSystemPrompt(dispositionTone)
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

    private fun buildSystemPrompt(dispositionTone: String? = null): String = buildString {
        appendLine("You are a dialogue generator for NPCs in a text-based adventure game (MUD).")
        appendLine()
        appendLine("Your task is to create engaging, in-character dialogue for NPCs.")
        appendLine()
        appendLine("Guidelines:")
        appendLine("- Write in the NPC's voice, staying true to their nature")
        appendLine("- Keep responses to 1-3 sentences")
        appendLine("- Be creative and atmospheric")

        if (dispositionTone != null) {
            appendLine("- DISPOSITION TONE: Be $dispositionTone")
        } else {
            appendLine("- If hostile, the NPC should be menacing or dismissive")
            appendLine("- If friendly, the NPC should be welcoming or helpful")
        }

        appendLine("- Use the NPC's description and properties to inform personality")
        appendLine("- Do NOT break character or provide meta-information")
        appendLine("- If conversation history is provided, maintain continuity")
        appendLine("- Avoid repeating exact phrases from past conversations")
        appendLine()
        appendLine("Example hostile NPC: \"Begone, mortal! Your presence defiles this sacred throne.\"")
        appendLine("Example friendly NPC: \"Welcome, traveler. You look weary from your journey.\"")
    }.trimEnd()

    private fun buildUserContext(npc: Entity.NPC, player: PlayerState, memories: List<String>): String {
        // Get social component and disposition info
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val dispositionInfo = if (social != null) {
            val tier = social.getDispositionTier()
            val value = social.disposition
            "$tier (${if (value >= 0) "+" else ""}$value)"
        } else {
            // Fallback to legacy hostile flag
            if (npc.isHostile) "HOSTILE" else "FRIENDLY"
        }

        // Get personality info
        val personalityNote = social?.personality?.let { "\nPersonality: $it" } ?: ""
        val traitsNote = social?.traits?.takeIf { it.isNotEmpty() }?.let {
            "\nTraits: ${it.joinToString(", ")}"
        } ?: ""

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

        return buildString {
            appendLine("NPC: ${npc.name}")
            appendLine("Description: ${npc.description}")
            append("Disposition: $dispositionInfo")
            append(personalityNote)
            append(traitsNote)
            appendLine()
            appendLine("Condition: $healthStatus (${npc.health}/${npc.maxHealth} HP)")
            appendLine()
            appendLine("Player: ${player.name}")
            appendLine("Player Health: ${player.health}/${player.maxHealth} HP")
            append(historySection)
            appendLine()
            appendLine()
            appendLine("The player approaches and tries to talk to the NPC.")
            append("Generate the NPC's response.")
        }.trim()
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
