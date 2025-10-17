package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*
import com.jcraw.mud.memory.social.*

/**
 * Manages NPC disposition and social event application
 *
 * Responsibilities:
 * - Apply social events to NPCs and persist changes
 * - Calculate disposition-based behavior (dialogue tone, quest hints, prices)
 * - Log social event history for analytics
 */
class DispositionManager(
    private val socialRepo: SocialComponentRepository,
    private val eventRepo: SocialEventRepository
) {

    /**
     * Apply social event to NPC, persisting changes to database
     *
     * @param npc The NPC to apply the event to
     * @param event The social event that occurred
     * @return Updated NPC with new disposition, or original NPC if persistence fails
     */
    fun applyEvent(
        npc: Entity.NPC,
        event: SocialEvent
    ): Entity.NPC {
        // Get existing social component or create default
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL) ?: SocialComponent(
            personality = "ordinary",
            traits = emptyList()
        )

        // Apply disposition change (clamped to -100..100)
        val updated = social.applyDispositionChange(event.dispositionDelta)

        // Persist to database
        val saveResult = socialRepo.save(npc.id, updated)
        if (saveResult.isFailure) {
            println("Warning: Failed to save social component for ${npc.id}: ${saveResult.exceptionOrNull()?.message}")
        }

        // Log event to history
        val eventRecord = SocialEventRecord(
            npcId = npc.id,
            eventType = event.eventType,
            dispositionDelta = event.dispositionDelta,
            description = event.description,
            timestamp = System.currentTimeMillis()
        )
        val logResult = eventRepo.save(eventRecord)
        if (logResult.isFailure) {
            println("Warning: Failed to log social event for ${npc.id}: ${logResult.exceptionOrNull()?.message}")
        }

        // Return updated NPC
        return npc.withComponent(updated) as Entity.NPC
    }

    /**
     * Determine if NPC should provide quest hints based on disposition
     *
     * NPCs with FRIENDLY or ALLIED disposition will provide hints
     */
    fun shouldProvideQuestHints(npc: Entity.NPC): Boolean {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
        return tier == DispositionTier.ALLIED || tier == DispositionTier.FRIENDLY
    }

    /**
     * Get dialogue tone instruction for LLM based on disposition
     *
     * Returns a string that can be included in LLM prompts to adjust NPC behavior
     */
    fun getDialogueTone(npc: Entity.NPC): String {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL

        return when (tier) {
            DispositionTier.ALLIED -> "extremely friendly, helpful, and warm. Offer hints and secrets willingly."
            DispositionTier.FRIENDLY -> "friendly and helpful. Be accommodating."
            DispositionTier.NEUTRAL -> "neutral and professional. Neither helpful nor rude."
            DispositionTier.UNFRIENDLY -> "cold and curt. Give short, unhelpful responses."
            DispositionTier.HOSTILE -> "hostile and threatening. Refuse to help."
        }
    }

    /**
     * Calculate price modifier for trading based on disposition
     *
     * Future enhancement: adjust shop prices based on disposition
     * - Allied NPCs give 30% discount (0.7x)
     * - Friendly NPCs give 15% discount (0.85x)
     * - Neutral NPCs use normal prices (1.0x)
     * - Unfriendly NPCs charge 15% markup (1.15x)
     * - Hostile NPCs charge 50% markup (1.5x)
     */
    fun getPriceModifier(npc: Entity.NPC): Double {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL

        return when (tier) {
            DispositionTier.ALLIED -> 0.7    // 30% discount
            DispositionTier.FRIENDLY -> 0.85  // 15% discount
            DispositionTier.NEUTRAL -> 1.0    // Normal price
            DispositionTier.UNFRIENDLY -> 1.15 // 15% markup
            DispositionTier.HOSTILE -> 1.5    // 50% markup
        }
    }

    /**
     * Get recent social event history for an NPC
     *
     * Useful for understanding NPC's relationship with player
     */
    fun getRecentEvents(npcId: String, limit: Int = 10): List<SocialEventRecord> {
        return eventRepo.findRecentByNpcId(npcId, limit).getOrElse { emptyList() }
    }

    /**
     * Get current disposition value for an NPC
     *
     * Returns 0 if NPC has no social component
     */
    fun getDisposition(npc: Entity.NPC): Int {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
    }

    /**
     * Get disposition tier for an NPC
     *
     * Returns NEUTRAL if NPC has no social component
     */
    fun getDispositionTier(npc: Entity.NPC): DispositionTier {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
    }
}
