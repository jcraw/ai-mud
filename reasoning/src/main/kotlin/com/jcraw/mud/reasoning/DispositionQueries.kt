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
import com.jcraw.mud.core.DispositionTier
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent

/**
 * Disposition lookups (hints / tone / price / tier) for [DispositionManager] (MUD-034n).
 */
internal object DispositionQueries {

    fun shouldProvideQuestHints(npc: Entity.NPC): Boolean {
        val tier = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
        return tier == DispositionTier.ALLIED || tier == DispositionTier.FRIENDLY
    }

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

    fun getDisposition(npc: Entity.NPC): Int {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
    }

    fun getDispositionTier(npc: Entity.NPC): DispositionTier {
        return npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.getDispositionTier()
            ?: DispositionTier.NEUTRAL
    }
}
