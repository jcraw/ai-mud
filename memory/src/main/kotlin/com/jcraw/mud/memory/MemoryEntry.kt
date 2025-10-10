package com.jcraw.mud.memory

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Represents a single memory entry in the vector store.
 * Contains the event description, its embedding, and metadata.
 */
@Serializable
data class MemoryEntry(
    val id: String,
    val content: String,
    val embedding: List<Double>,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Type of game event being stored in memory
 */
sealed class MemoryEventType {
    data object RoomEntry : MemoryEventType()
    data object Combat : MemoryEventType()
    data object Conversation : MemoryEventType()
    data object ItemInteraction : MemoryEventType()
    data object SkillCheck : MemoryEventType()
    data object Other : MemoryEventType()
}
