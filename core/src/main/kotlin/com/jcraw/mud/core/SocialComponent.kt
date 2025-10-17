package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Social interaction component for NPCs
 * Tracks disposition, personality, knowledge, and relationship history
 */
@Serializable
data class SocialComponent(
    val disposition: Int = 0, // -100 (hostile) to +100 (allied)
    val personality: String, // e.g., "gruff warrior", "wise scholar"
    val traits: List<String> = emptyList(), // ["honorable", "greedy", "cowardly"]
    val knowledgeEntries: List<String> = emptyList(), // IDs of knowledge in DB
    val conversationCount: Int = 0, // Track interaction frequency
    val lastInteractionTime: Long = 0L, // For time-based disposition decay (future)
    override val componentType: ComponentType = ComponentType.SOCIAL
) : Component {

    /**
     * Get disposition tier for behavior lookup
     */
    fun getDispositionTier(): DispositionTier = when {
        disposition >= 75 -> DispositionTier.ALLIED
        disposition >= 25 -> DispositionTier.FRIENDLY
        disposition >= -25 -> DispositionTier.NEUTRAL
        disposition >= -75 -> DispositionTier.UNFRIENDLY
        else -> DispositionTier.HOSTILE
    }

    /**
     * Apply disposition change, clamped to -100..100
     */
    fun applyDispositionChange(delta: Int): SocialComponent {
        val newDisposition = (disposition + delta).coerceIn(-100, 100)
        return copy(disposition = newDisposition)
    }

    /**
     * Increment conversation counter and update last interaction time
     */
    fun incrementConversationCount(): SocialComponent {
        return copy(
            conversationCount = conversationCount + 1,
            lastInteractionTime = System.currentTimeMillis()
        )
    }

    /**
     * Add knowledge entry reference
     */
    fun addKnowledge(knowledgeId: String): SocialComponent {
        return copy(knowledgeEntries = knowledgeEntries + knowledgeId)
    }
}

@Serializable
enum class DispositionTier {
    ALLIED,    // >= 75
    FRIENDLY,  // >= 25
    NEUTRAL,   // >= -25
    UNFRIENDLY, // >= -75
    HOSTILE    // < -75
}
