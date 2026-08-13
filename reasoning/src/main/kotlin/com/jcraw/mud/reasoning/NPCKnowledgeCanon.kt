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

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.KnowledgeEntry
import com.jcraw.mud.core.KnowledgeSource
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.memory.social.KnowledgeRepository
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.sophia.llm.LLMClient
import java.util.UUID

/**
 * LLM canon generation + persist for [NPCKnowledgeManager] (MUD-034n).
 */
internal object NPCKnowledgeCanon {

    /**
     * Generate new canon lore using LLM
     *
     * Creates consistent world lore that fits the NPC's personality and world context
     */
    suspend fun generate(
        knowledgeRepo: KnowledgeRepository,
        socialRepo: SocialComponentRepository,
        llmClient: LLMClient?,
        npc: Entity.NPC,
        question: String,
        normalizedTopic: String,
        worldContext: String,
        existingKnowledge: List<KnowledgeEntry>
    ): Pair<String, Entity.NPC> {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val systemPrompt = canonSystemPrompt(npc, social, worldContext, existingKnowledge)
        val userPrompt = NPCKnowledgePrompts.buildUserPrompt(question, existingKnowledge)
        val answer = completeCanonChat(llmClient, systemPrompt, userPrompt)
        val updatedNpc = persistCanon(
            knowledgeRepo, socialRepo, npc, social, question, normalizedTopic, answer
        )
        return answer to updatedNpc
    }

    private fun canonSystemPrompt(
        npc: Entity.NPC,
        social: SocialComponent?,
        worldContext: String,
        existingKnowledge: List<KnowledgeEntry>
    ): String {
        return NPCKnowledgePrompts.buildCanonGenerationPrompt(
            npcName = npc.name,
            personality = social?.personality ?: "ordinary",
            traits = social?.traits?.takeIf { it.isNotEmpty() }?.joinToString(", "),
            disposition = social?.getDispositionTier(),
            worldContext = worldContext,
            existingKnowledge = NPCKnowledgeTopics.buildKnowledgeSummary(existingKnowledge)
        )
    }

    private suspend fun completeCanonChat(
        llmClient: LLMClient?,
        systemPrompt: String,
        userPrompt: String
    ): String {
        // Call LLM (using runCatching to handle failures gracefully)
        return runCatching {
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
    }

    private fun persistCanon(
        knowledgeRepo: KnowledgeRepository,
        socialRepo: SocialComponentRepository,
        npc: Entity.NPC,
        social: SocialComponent?,
        question: String,
        normalizedTopic: String,
        answer: String
    ): Entity.NPC {
        val knowledgeId = UUID.randomUUID().toString()
        val saveResult = knowledgeRepo.save(
            canonEntry(knowledgeId, npc.id, normalizedTopic, question, answer)
        )
        if (saveResult.isFailure) {
            println("Warning: Failed to save knowledge entry: ${saveResult.exceptionOrNull()?.message}")
        }
        return attachKnowledge(socialRepo, npc, social, knowledgeId, saveResult.isSuccess)
    }

    private fun canonEntry(
        knowledgeId: String,
        npcId: String,
        normalizedTopic: String,
        question: String,
        answer: String
    ) = KnowledgeEntry(
        id = knowledgeId,
        entityId = npcId,
        topic = normalizedTopic,
        question = question,
        content = answer,
        isCanon = true,
        source = KnowledgeSource.GENERATED,
        timestamp = System.currentTimeMillis(),
        tags = mapOf("category" to "canon", "topic" to normalizedTopic, "question" to question)
    )

    private fun attachKnowledge(
        socialRepo: SocialComponentRepository,
        npc: Entity.NPC,
        social: SocialComponent?,
        knowledgeId: String,
        saved: Boolean
    ): Entity.NPC {
        return if (social != null && saved) {
            val updatedSocial = social.addKnowledge(knowledgeId)
            socialRepo.save(npc.id, updatedSocial)
            npc.withComponent(updatedSocial) as Entity.NPC
        } else {
            npc
        }
    }
}
