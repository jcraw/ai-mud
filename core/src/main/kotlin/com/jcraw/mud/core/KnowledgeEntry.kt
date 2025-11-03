package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Represents a piece of knowledge known by an NPC
 * Stored in database with vector embeddings for RAG retrieval
 */
@Serializable
data class KnowledgeEntry(
    val id: String,
    val entityId: String, // Which NPC knows this
    val topic: String, // Normalized topic identifier
    val question: String, // Player's phrasing of the question
    val content: String, // The actual knowledge text
    val isCanon: Boolean, // True if this is official lore
    val source: KnowledgeSource,
    val timestamp: Long = System.currentTimeMillis(),
    val tags: Map<String, String> = emptyMap() // For categorization and metadata
)

@Serializable
enum class KnowledgeSource {
    PREDEFINED,    // Written by developers, always canon
    GENERATED,     // LLM-generated in response to question, becomes canon
    PLAYER_TAUGHT, // Player told NPC something, may or may not be canon
    OBSERVED       // NPC witnessed an event
}
