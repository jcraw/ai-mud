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

import com.jcraw.mud.core.KnowledgeEntry

/**
 * Topic normalize / match / keywords / summary for [NPCKnowledgeManager] (MUD-034n).
 */
internal object NPCKnowledgeTopics {

    fun normalizeTopic(input: String): String {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun topicsMatch(entry: KnowledgeEntry, normalizedTopic: String): Boolean {
        val entryTopic = entry.topic.ifBlank { entry.question }
        return normalizeTopic(entryTopic) == normalizedTopic
    }

    /**
     * Simple keyword matching to find relevant knowledge
     *
     * This is a basic implementation. Could be enhanced with:
     * - Vector embeddings for semantic search
     * - TF-IDF scoring
     * - N-gram matching
     */
    fun containsRelevantKeywords(content: String, question: String): Boolean {
        val contentWords = content.lowercase().split(Regex("\\W+")).filter { it.length > 3 }
        val questionWords = question.lowercase().split(Regex("\\W+")).filter { it.length > 3 }

        // Check if at least 2 keywords match (or 1 for short questions)
        val threshold = if (questionWords.size <= 3) 1 else 2
        val matches = questionWords.count { qWord -> contentWords.any { it.contains(qWord) || qWord.contains(it) } }

        return matches >= threshold
    }

    fun buildKnowledgeSummary(entries: List<KnowledgeEntry>): String {
        if (entries.isEmpty()) return ""
        return entries
            .sortedByDescending { it.timestamp }
            .take(5)
            .joinToString(separator = "\n") { entry ->
                val topic = entry.topic.ifBlank { entry.question }
                "- Topic: $topic | Answer: ${entry.content}"
            }
    }
}
