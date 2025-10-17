package com.jcraw.mud.reasoning

import com.jcraw.mud.core.*

/**
 * Handles emote processing and narrative generation
 *
 * Emotes are non-verbal social interactions that affect NPC disposition.
 * Examples: bow, wave, laugh, insult, threaten, etc.
 */
class EmoteHandler(
    private val dispositionManager: DispositionManager
) {

    /**
     * Process an emote action and return narrative + updated NPC
     *
     * @param npc The target NPC
     * @param emoteType The type of emote performed
     * @param playerName The player performing the emote
     * @return Pair of narrative description and updated NPC
     */
    fun processEmote(
        npc: Entity.NPC,
        emoteType: EmoteType,
        playerName: String = "You"
    ): Pair<String, Entity.NPC> {
        // Create social event
        val event = SocialEvent.EmotePerformed(
            emoteType = emoteType,
            description = "You performed ${emoteType.name.lowercase().replace('_', ' ')} to ${npc.name}"
        )

        // Apply event to NPC
        val updatedNpc = dispositionManager.applyEvent(npc, event)

        // Generate narrative - use UPDATED npc so reactions match new disposition tier
        val narrative = generateEmoteNarrative(
            playerName = playerName,
            npc = updatedNpc,  // Use updated NPC for correct tier-based reactions
            emoteType = emoteType,
            newDisposition = dispositionManager.getDisposition(updatedNpc),
            oldDisposition = dispositionManager.getDisposition(npc)
        )

        return narrative to updatedNpc
    }

    /**
     * Parse emote keyword from player input
     *
     * Returns null if no emote type matches the keyword
     */
    fun parseEmoteKeyword(keyword: String): EmoteType? {
        return EmoteType.fromKeyword(keyword)
    }

    /**
     * Get all available emote keywords
     *
     * Useful for help text or autocomplete
     */
    fun getAllEmoteKeywords(): List<String> {
        return EmoteType.values().flatMap { it.keywords }
    }

    /**
     * Generate narrative description for an emote
     *
     * Includes:
     * - Player's emote action
     * - NPC's reaction (based on disposition change)
     * - Current relationship status (if significant change)
     */
    private fun generateEmoteNarrative(
        playerName: String,
        npc: Entity.NPC,
        emoteType: EmoteType,
        newDisposition: Int,
        oldDisposition: Int
    ): String {
        val action = getEmoteAction(playerName, npc.name, emoteType)
        val reaction = getEmoteReaction(npc, emoteType, newDisposition, oldDisposition)

        val parts = mutableListOf<String>()
        parts.add(action)

        if (reaction.isNotEmpty()) {
            parts.add(reaction)
        }

        // Add relationship status if significant change
        val dispositionChange = newDisposition - oldDisposition
        if (dispositionChange != 0) {
            val relationshipNote = getRelationshipNote(npc, newDisposition, dispositionChange)
            if (relationshipNote.isNotEmpty()) {
                parts.add(relationshipNote)
            }
        }

        return parts.joinToString("\n\n")
    }

    /**
     * Get emote action description
     */
    private fun getEmoteAction(playerName: String, npcName: String, emoteType: EmoteType): String {
        return when (emoteType) {
            EmoteType.BOW -> "$playerName bow respectfully to $npcName."
            EmoteType.WAVE -> "$playerName wave at $npcName."
            EmoteType.NOD -> "$playerName nod at $npcName."
            EmoteType.SHAKE_HEAD -> "$playerName shake your head at $npcName."
            EmoteType.LAUGH -> "$playerName laugh heartily at $npcName's remark."
            EmoteType.INSULT -> "$playerName hurl an insult at $npcName!"
            EmoteType.THREATEN -> "$playerName glare menacingly at $npcName!"
        }
    }

    /**
     * Get NPC reaction based on emote type and disposition
     */
    private fun getEmoteReaction(
        npc: Entity.NPC,
        emoteType: EmoteType,
        newDisposition: Int,
        oldDisposition: Int
    ): String {
        val tier = dispositionManager.getDispositionTier(npc)

        return when (emoteType) {
            EmoteType.BOW -> when (tier) {
                DispositionTier.ALLIED, DispositionTier.FRIENDLY -> "${npc.name} bows back with a warm smile."
                DispositionTier.NEUTRAL -> "${npc.name} nods in acknowledgment."
                DispositionTier.UNFRIENDLY -> "${npc.name} barely acknowledges your gesture."
                DispositionTier.HOSTILE -> "${npc.name} scoffs at your display."
            }
            EmoteType.WAVE -> when (tier) {
                DispositionTier.ALLIED -> "${npc.name} waves back enthusiastically!"
                DispositionTier.FRIENDLY -> "${npc.name} waves back."
                DispositionTier.NEUTRAL -> "${npc.name} gives a polite wave."
                DispositionTier.UNFRIENDLY -> "${npc.name} ignores your wave."
                DispositionTier.HOSTILE -> "${npc.name} glares at you."
            }
            EmoteType.NOD -> when (tier) {
                DispositionTier.ALLIED, DispositionTier.FRIENDLY -> "${npc.name} nods back with approval."
                DispositionTier.NEUTRAL -> "${npc.name} nods back."
                DispositionTier.UNFRIENDLY, DispositionTier.HOSTILE -> "${npc.name} stares at you coldly."
            }
            EmoteType.SHAKE_HEAD -> when (tier) {
                DispositionTier.ALLIED -> "${npc.name} looks concerned by your disapproval."
                DispositionTier.FRIENDLY -> "${npc.name} shrugs."
                DispositionTier.NEUTRAL -> "${npc.name} seems indifferent."
                DispositionTier.UNFRIENDLY, DispositionTier.HOSTILE -> "${npc.name} doesn't care."
            }
            EmoteType.LAUGH -> when (tier) {
                DispositionTier.ALLIED, DispositionTier.FRIENDLY -> "${npc.name} joins in the laughter!"
                DispositionTier.NEUTRAL -> "${npc.name} cracks a small smile."
                DispositionTier.UNFRIENDLY -> "${npc.name} doesn't find it amusing."
                DispositionTier.HOSTILE -> "${npc.name} scowls at you."
            }
            EmoteType.INSULT -> when (tier) {
                DispositionTier.ALLIED -> "${npc.name} looks hurt and disappointed."
                DispositionTier.FRIENDLY -> "${npc.name} frowns deeply."
                DispositionTier.NEUTRAL -> "${npc.name} takes offense!"
                DispositionTier.UNFRIENDLY, DispositionTier.HOSTILE -> "${npc.name} looks ready to fight!"
            }
            EmoteType.THREATEN -> when (tier) {
                DispositionTier.ALLIED -> "${npc.name} backs away, shocked and betrayed."
                DispositionTier.FRIENDLY -> "${npc.name} is taken aback by your hostility."
                DispositionTier.NEUTRAL -> "${npc.name} tenses up defensively."
                DispositionTier.UNFRIENDLY -> "${npc.name} returns the threatening glare."
                DispositionTier.HOSTILE -> "${npc.name} prepares to attack!"
            }
        }
    }

    /**
     * Get relationship status note if significant change occurred
     */
    private fun getRelationshipNote(npc: Entity.NPC, newDisposition: Int, change: Int): String {
        // Only show note for significant changes (>=10 points)
        if (kotlin.math.abs(change) < 10) {
            return ""
        }

        val tier = dispositionManager.getDispositionTier(npc)

        return when {
            change > 0 -> when (tier) {
                DispositionTier.ALLIED -> "${npc.name} now considers you a close ally."
                DispositionTier.FRIENDLY -> "${npc.name} seems friendlier toward you."
                DispositionTier.NEUTRAL -> "${npc.name} warms up a bit."
                else -> ""
            }
            change < 0 -> when (tier) {
                DispositionTier.HOSTILE -> "${npc.name} now despises you."
                DispositionTier.UNFRIENDLY -> "${npc.name} seems more hostile."
                DispositionTier.NEUTRAL -> "${npc.name} regards you with suspicion."
                else -> ""
            }
            else -> ""
        }
    }
}
