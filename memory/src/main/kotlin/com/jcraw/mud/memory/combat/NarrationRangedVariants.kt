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

package com.jcraw.mud.memory.combat

import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Ranged hit/miss variants for [NarrationVariantGenerator] (MUD-034m).
 */
internal object NarrationRangedVariants {

    /**
     * Generates variants for ranged weapon hits.
     */
    suspend fun generateHits(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("bow", "crossbow", "throwing knife", "javelin")
        val damageTiers = listOf("low", "medium", "high")

        for (weapon in weapons) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    NarrationVariantSupport.rememberVariant(
                        llmClient, memoryManager,
                        scenario = "ranged hit",
                        weapon = weapon,
                        damageTier = tier,
                        variantNumber = variantNum + 1,
                        tags = hitTags(weapon, tier)
                    )
                }
            }
        }
    }

    /**
     * Generates variants for ranged misses.
     */
    suspend fun generateMisses(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("bow", "crossbow", "throwing knife", "javelin")

        for (weapon in weapons) {
            repeat(2) { variantNum ->
                NarrationVariantSupport.rememberVariant(
                    llmClient, memoryManager,
                    scenario = "ranged miss",
                    weapon = weapon,
                    damageTier = "none",
                    variantNumber = variantNum + 1,
                    tags = missTags(weapon)
                )
            }
        }
    }

    private fun hitTags(weapon: String, tier: String) = mapOf(
        "type" to "combat_narration",
        "scenario" to "ranged_hit",
        "weapon" to weapon,
        "damage_tier" to tier,
        "outcome" to "hit"
    )

    private fun missTags(weapon: String) = mapOf(
        "type" to "combat_narration",
        "scenario" to "ranged_miss",
        "weapon" to weapon,
        "outcome" to "miss"
    )
}
