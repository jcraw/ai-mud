@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.KnowledgeEntry
import com.jcraw.mud.memory.social.KnowledgeRepository
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.sophia.llm.LLMClient

/**
 * Manages NPC knowledge and canon generation
 *
 * Responsibilities:
 * - Query NPC knowledge bases
 * - Generate new canon lore when NPCs don't know the answer
 * - Persist knowledge to database
 * - Maintain knowledge consistency across NPCs
 *
 * Thin facade — bodies in NPCKnowledge* extracts (MUD-034n).
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
    ): KnowledgeResult {
        val trimmedQuestion = question.trim().ifBlank { "Unknown topic" }
        val normalizedTopic = NPCKnowledgeTopics.normalizeTopic(trimmedQuestion)
        val existingKnowledge = knowledgeRepo.findByNpcId(npc.id).getOrElse { emptyList() }

        // Exact topic match first, then fallback to loose keyword match for backward compatibility
        val relevantKnowledge = existingKnowledge.find { entry ->
            NPCKnowledgeTopics.topicsMatch(entry, normalizedTopic) ||
                NPCKnowledgeTopics.containsRelevantKeywords(entry.content, trimmedQuestion)
        }

        if (relevantKnowledge != null) {
            // Return existing knowledge
            val topicId = relevantKnowledge.topic.takeIf { it.isNotBlank() } ?: normalizedTopic
            return KnowledgeResult(
                answer = relevantKnowledge.content,
                npc = npc,
                normalizedTopic = topicId,
                question = trimmedQuestion
            )
        }

        // No existing knowledge - generate new canon if LLM available
        if (llmClient != null) {
            val (answer, updatedNpc) = NPCKnowledgeCanon.generate(
                knowledgeRepo = knowledgeRepo,
                socialRepo = socialRepo,
                llmClient = llmClient,
                npc = npc,
                question = trimmedQuestion,
                normalizedTopic = normalizedTopic,
                worldContext = worldContext,
                existingKnowledge = existingKnowledge
            )
            return KnowledgeResult(
                answer = answer,
                npc = updatedNpc,
                normalizedTopic = normalizedTopic,
                question = trimmedQuestion
            )
        }

        // Fallback if no LLM
        return KnowledgeResult(
            answer = "${npc.name} doesn't know anything about that.",
            npc = npc,
            normalizedTopic = normalizedTopic,
            question = trimmedQuestion
        )
    }

    /**
     * Add predefined knowledge to an NPC
     *
     * Useful for setting up quest-related knowledge or world lore
     */
    fun addPredefinedKnowledge(
        npc: Entity.NPC,
        topic: String,
        content: String,
        question: String = topic,
        category: String = "predefined"
    ): Entity.NPC = NPCKnowledgeStore.addPredefinedKnowledge(
        knowledgeRepo, socialRepo, npc, topic, content, question, category
    )

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

    data class KnowledgeResult(
        val answer: String,
        val npc: Entity.NPC,
        val normalizedTopic: String,
        val question: String
    )
}
