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

import com.jcraw.mud.core.DispositionTier
import com.jcraw.mud.core.KnowledgeEntry

/**
 * Canon LLM prompts for [NPCKnowledgeManager] (MUD-034n).
 */
internal object NPCKnowledgePrompts {

    /**
     * Build LLM prompt for canon knowledge generation
     */
    fun buildCanonGenerationPrompt(
        npcName: String,
        personality: String,
        traits: String?,
        disposition: DispositionTier?,
        worldContext: String,
        existingKnowledge: String
    ): String {
        return buildString {
            appendIdentity(this, npcName, personality, traits, disposition)
            appendContext(this, worldContext, existingKnowledge)
            appendRules(this)
        }
    }

    private fun appendIdentity(
        sb: StringBuilder,
        npcName: String,
        personality: String,
        traits: String?,
        disposition: DispositionTier?
    ) {
        sb.appendLine("You are generating lore for a fantasy MUD game world.")
        sb.appendLine("You are speaking as the NPC named $npcName.")
        sb.appendLine("NPC personality: $personality")
        if (!traits.isNullOrBlank()) sb.appendLine("NPC traits: $traits")
        if (disposition != null) {
            sb.appendLine("NPC disposition towards the player: ${disposition.name.lowercase()}")
        }
        sb.appendLine()
    }

    private fun appendContext(sb: StringBuilder, worldContext: String, existingKnowledge: String) {
        if (worldContext.isNotEmpty()) {
            sb.appendLine("World context:")
            sb.appendLine(worldContext)
            sb.appendLine()
        }
        if (existingKnowledge.isNotBlank()) {
            sb.appendLine("Existing knowledge you have already confirmed:")
            sb.appendLine(existingKnowledge)
            sb.appendLine()
        }
    }

    private fun appendRules(sb: StringBuilder) {
        sb.appendLine("Rules:")
        sb.appendLine("- Stay in character with the NPC's personality")
        sb.appendLine("- Be concise (1-3 sentences)")
        sb.appendLine("- Generate consistent, believable fantasy lore")
        sb.appendLine("- Don't contradict established world facts")
        sb.appendLine("- If you don't know, say so in character")
    }

    fun buildUserPrompt(question: String, existingKnowledge: List<KnowledgeEntry>): String {
        val relatedExamples = existingKnowledge
            .filter { NPCKnowledgeTopics.containsRelevantKeywords(it.content, question) }
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
}
