package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.social.*
import com.jcraw.sophia.llm.LLMClient
import java.util.UUID

/**
 * Manages NPC knowledge and canon generation
 *
 * Responsibilities:
 * - Query NPC knowledge bases
 * - Generate new canon lore when NPCs don't know the answer
 * - Persist knowledge to database
 * - Maintain knowledge consistency across NPCs
 */
class NPCKnowledgeManager(
    private val knowledgeRepo: KnowledgeRepository,
    private val socialRepo: SocialComponentRepository,
    private val llmClient: LLMClient?
) {

    /**
     * Query an NPC's knowledge about a topic
     *
     * Flow:
     * 1. Search existing knowledge for the NPC
     * 2. If found, return it
     * 3. If not found and LLM available, generate new canon knowledge
     * 4. If LLM unavailable, return fallback response
     *
     * @param npc The NPC being asked
     * @param question The player's question
     * @param worldContext Optional context about the game world
     * @return Pair of (answer text, updated NPC with new knowledge reference)
     */
    suspend fun queryKnowledge(
        npc: Entity.NPC,
        question: String,
        worldContext: String = ""
    ): Pair<String, Entity.NPC> {
        // Search existing knowledge
        val existingKnowledge = knowledgeRepo.findByNpcId(npc.id).getOrElse { emptyList() }

        // Simple keyword matching for existing knowledge
        val relevantKnowledge = existingKnowledge.find { entry ->
            containsRelevantKeywords(entry.content, question)
        }

        if (relevantKnowledge != null) {
            // Return existing knowledge
            return relevantKnowledge.content to npc
        }

        // No existing knowledge - generate new canon if LLM available
        if (llmClient != null) {
            val (answer, updatedNpc) = generateCanonKnowledge(npc, question, worldContext)
            return answer to updatedNpc
        }

        // Fallback if no LLM
        return "${npc.name} doesn't know anything about that." to npc
    }

    /**
     * Generate new canon lore using LLM
     *
     * Creates consistent world lore that fits the NPC's personality and world context
     */
    private suspend fun generateCanonKnowledge(
        npc: Entity.NPC,
        question: String,
        worldContext: String
    ): Pair<String, Entity.NPC> {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val personality = social?.personality ?: "ordinary"

        // Build LLM prompt
        val systemPrompt = buildCanonGenerationPrompt(personality, worldContext)
        val userPrompt = "Question: $question"

        // Call LLM (using runCatching to handle failures gracefully)
        val answer = runCatching {
            if (llmClient != null) {
                val response = llmClient.chatCompletion(
                    modelId = "gpt-4o-mini",
                    systemPrompt = systemPrompt,
                    userContext = userPrompt,
                    maxTokens = 200,
                    temperature = 0.9
                )
                response.choices.firstOrNull()?.message?.content ?: "I don't know."
            } else {
                "I don't know."
            }
        }.getOrElse { exception ->
            println("Warning: LLM call failed: ${exception.message}")
            "I don't know."
        }

        // Save to knowledge base
        val knowledgeId = UUID.randomUUID().toString()
        val entry = KnowledgeEntry(
            id = knowledgeId,
            npcId = npc.id,
            content = answer,
            category = "canon",
            timestamp = System.currentTimeMillis(),
            source = "generated"
        )

        val saveResult = knowledgeRepo.save(entry)
        if (saveResult.isFailure) {
            println("Warning: Failed to save knowledge entry: ${saveResult.exceptionOrNull()?.message}")
        }

        // Update NPC's social component with knowledge reference
        val updatedNpc = if (social != null && saveResult.isSuccess) {
            val updatedSocial = social.addKnowledge(knowledgeId)
            socialRepo.save(npc.id, updatedSocial)
            npc.withComponent(updatedSocial) as Entity.NPC
        } else {
            npc
        }

        return answer to updatedNpc
    }

    /**
     * Build LLM prompt for canon knowledge generation
     */
    private fun buildCanonGenerationPrompt(personality: String, worldContext: String): String {
        return buildString {
            appendLine("You are generating lore for a fantasy MUD game world.")
            appendLine("You are speaking as an NPC with personality: $personality")
            appendLine()
            if (worldContext.isNotEmpty()) {
                appendLine("World context:")
                appendLine(worldContext)
                appendLine()
            }
            appendLine("Rules:")
            appendLine("- Stay in character with the NPC's personality")
            appendLine("- Be concise (1-3 sentences)")
            appendLine("- Generate consistent, believable fantasy lore")
            appendLine("- Don't contradict established world facts")
            appendLine("- If you don't know, say so in character")
        }
    }

    /**
     * Simple keyword matching to find relevant knowledge
     *
     * This is a basic implementation. Could be enhanced with:
     * - Vector embeddings for semantic search
     * - TF-IDF scoring
     * - N-gram matching
     */
    private fun containsRelevantKeywords(content: String, question: String): Boolean {
        val contentWords = content.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
        val questionWords = question.lowercase().split(Regex("\\W+")).filter { it.length > 3 }

        // Check if at least 2 keywords match (or 1 for short questions)
        val threshold = if (questionWords.size <= 3) 1 else 2
        val matches = questionWords.count { qWord -> contentWords.any { it.contains(qWord) || qWord.contains(it) } }

        return matches >= threshold
    }

    /**
     * Add predefined knowledge to an NPC
     *
     * Useful for setting up quest-related knowledge or world lore
     */
    fun addPredefinedKnowledge(
        npc: Entity.NPC,
        content: String,
        category: String = "predefined"
    ): Entity.NPC {
        val knowledgeId = UUID.randomUUID().toString()
        val entry = KnowledgeEntry(
            id = knowledgeId,
            npcId = npc.id,
            content = content,
            category = category,
            timestamp = System.currentTimeMillis(),
            source = "predefined"
        )

        // Save to repository
        knowledgeRepo.save(entry)

        // Update NPC's social component
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL) ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )

        val updatedSocial = social.addKnowledge(knowledgeId)
        socialRepo.save(npc.id, updatedSocial)

        return npc.withComponent(updatedSocial) as Entity.NPC
    }

    /**
     * Get all knowledge for an NPC
     *
     * Useful for debugging or displaying NPC's full knowledge base
     */
    fun getAllKnowledge(npcId: String): List<KnowledgeEntry> {
        return knowledgeRepo.findByNpcId(npcId).getOrElse { emptyList() }
    }

    /**
     * Get knowledge by category for an NPC
     *
     * Categories: "quest", "rumor", "secret", "canon", "predefined", etc.
     */
    fun getKnowledgeByCategory(npcId: String, category: String): List<KnowledgeEntry> {
        return knowledgeRepo.findByCategory(npcId, category).getOrElse { emptyList() }
    }
}
