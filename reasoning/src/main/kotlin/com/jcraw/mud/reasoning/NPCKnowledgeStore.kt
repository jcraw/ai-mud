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
import java.util.UUID

/**
 * Predefined knowledge writes for [NPCKnowledgeManager] (MUD-034n).
 */
internal object NPCKnowledgeStore {

    /**
     * Add predefined knowledge to an NPC
     *
     * Useful for setting up quest-related knowledge or world lore
     */
    fun addPredefinedKnowledge(
        knowledgeRepo: KnowledgeRepository,
        socialRepo: SocialComponentRepository,
        npc: Entity.NPC,
        topic: String,
        content: String,
        question: String,
        category: String
    ): Entity.NPC {
        val knowledgeId = UUID.randomUUID().toString()
        knowledgeRepo.save(predefinedEntry(knowledgeId, npc.id, topic, question, content, category))
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            ?: SocialComponent(personality = "ordinary", traits = emptyList())
        val updatedSocial = social.addKnowledge(knowledgeId)
        socialRepo.save(npc.id, updatedSocial)
        return npc.withComponent(updatedSocial) as Entity.NPC
    }

    private fun predefinedEntry(
        knowledgeId: String,
        npcId: String,
        topic: String,
        question: String,
        content: String,
        category: String
    ): KnowledgeEntry {
        val normalized = NPCKnowledgeTopics.normalizeTopic(topic)
        return KnowledgeEntry(
            id = knowledgeId,
            entityId = npcId,
            topic = normalized,
            question = question,
            content = content,
            isCanon = true,
            source = KnowledgeSource.PREDEFINED,
            timestamp = System.currentTimeMillis(),
            tags = mapOf("category" to category, "topic" to normalized, "question" to question)
        )
    }
}
