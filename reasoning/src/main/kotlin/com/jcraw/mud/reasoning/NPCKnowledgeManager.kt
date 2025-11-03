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
    ): KnowledgeResult {
        val trimmedQuestion = question.trim().ifBlank { "Unknown topic" }
        val normalizedTopic = normalizeTopic(trimmedQuestion)
        val existingKnowledge = knowledgeRepo.findByNpcId(npc.id).getOrElse { emptyList() }

        // Exact topic match first, then fallback to loose keyword match for backward compatibility
        val relevantKnowledge = existingKnowledge.find { entry ->
            topicsMatch(entry, normalizedTopic) || containsRelevantKeywords(entry.content, trimmedQuestion)
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
            val (answer, updatedNpc) = generateCanonKnowledge(
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
     * Generate new canon lore using LLM
     *
     * Creates consistent world lore that fits the NPC's personality and world context
     */
    private suspend fun generateCanonKnowledge(
        npc: Entity.NPC,
        question: String,
        normalizedTopic: String,
        worldContext: String,
        existingKnowledge: List<KnowledgeEntry>
    ): Pair<String, Entity.NPC> {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val personality = social?.personality ?: "ordinary"
        val traits = social?.traits?.takeIf { it.isNotEmpty() }?.joinToString(", ")
        val disposition = social?.getDispositionTier()
        val knowledgeSummary = buildKnowledgeSummary(existingKnowledge)

        // Build LLM prompt
        val systemPrompt = buildCanonGenerationPrompt(
            npcName = npc.name,
            personality = personality,
            traits = traits,
            disposition = disposition,
            worldContext = worldContext,
            existingKnowledge = knowledgeSummary
        )
        val userPrompt = buildUserPrompt(question, existingKnowledge)

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
            entityId = npc.id,
            topic = normalizedTopic,
            question = question,
            content = answer,
            isCanon = true,
            source = KnowledgeSource.GENERATED,
            timestamp = System.currentTimeMillis(),
            tags = mapOf(
                "category" to "canon",
                "topic" to normalizedTopic,
                "question" to question
            )
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
    private fun buildCanonGenerationPrompt(
        npcName: String,
        personality: String,
        traits: String?,
        disposition: DispositionTier?,
        worldContext: String,
        existingKnowledge: String
    ): String {
        return buildString {
            appendLine("You are generating lore for a fantasy MUD game world.")
            appendLine("You are speaking as the NPC named $npcName.")
            appendLine("NPC personality: $personality")
            if (!traits.isNullOrBlank()) {
                appendLine("NPC traits: $traits")
            }
            if (disposition != null) {
                appendLine("NPC disposition towards the player: ${disposition.name.lowercase()}")
            }
            appendLine()
            if (worldContext.isNotEmpty()) {
                appendLine("World context:")
                appendLine(worldContext)
                appendLine()
            }
            if (existingKnowledge.isNotBlank()) {
                appendLine("Existing knowledge you have already confirmed:")
                appendLine(existingKnowledge)
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

    private fun buildUserPrompt(question: String, existingKnowledge: List<KnowledgeEntry>): String {
        val relatedExamples = existingKnowledge
            .filter { containsRelevantKeywords(it.content, question) }
            .joinToString(separator = "\n") { "- Previously answered (${it.topic}): ${it.content}" }

        return buildString {
            appendLine("Player question: $question")
            if (relatedExamples.isNotBlank()) {
                appendLine()
                appendLine("Similar answers you've given before:")
                appendLine(relatedExamples)
            }
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
        topic: String,
        content: String,
        question: String = topic,
        category: String = "predefined"
    ): Entity.NPC {
        val knowledgeId = UUID.randomUUID().toString()
        val entry = KnowledgeEntry(
            id = knowledgeId,
            entityId = npc.id,
            topic = normalizeTopic(topic),
            question = question,
            content = content,
            isCanon = true,
            source = KnowledgeSource.PREDEFINED,
            timestamp = System.currentTimeMillis(),
            tags = mapOf(
                "category" to category,
                "topic" to normalizeTopic(topic),
                "question" to question
            )
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

    private fun normalizeTopic(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun topicsMatch(entry: KnowledgeEntry, normalizedTopic: String): Boolean {
        val entryTopic = entry.topic.ifBlank { entry.question }
        return normalizeTopic(entryTopic) == normalizedTopic
    }

    private fun buildKnowledgeSummary(entries: List<KnowledgeEntry>): String {
        if (entries.isEmpty()) return ""
        return entries
            .sortedByDescending { it.timestamp }
            .take(5)
            .joinToString(separator = "\n") { entry ->
                val topic = entry.topic.ifBlank { entry.question }
                "- Topic: $topic | Answer: ${entry.content}"
            }
    }

    data class KnowledgeResult(
        val answer: String,
        val npc: Entity.NPC,
        val normalizedTopic: String,
        val question: String
    )
}
