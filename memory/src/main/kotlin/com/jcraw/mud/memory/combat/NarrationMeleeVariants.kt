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
 * Melee hit/miss variants for [NarrationVariantGenerator] (MUD-034m).
 */
internal object NarrationMeleeVariants {

    /**
     * Generates 10 variants for melee weapon hits.
     * Covers sword, axe, mace, dagger attacks with low/medium/high damage tiers.
     */
    suspend fun generateHits(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("sword", "axe", "mace", "dagger", "spear")
        val damageTiers = listOf("low", "medium", "high")

        for (weapon in weapons) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    NarrationVariantSupport.rememberVariant(
                        llmClient, memoryManager,
                        scenario = "melee hit",
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
     * Generates variants for missed melee attacks.
     */
    suspend fun generateMisses(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("sword", "axe", "mace", "dagger", "spear")

        for (weapon in weapons) {
            repeat(2) { variantNum ->
                NarrationVariantSupport.rememberVariant(
                    llmClient, memoryManager,
                    scenario = "melee miss",
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
        "scenario" to "melee_hit",
        "weapon" to weapon,
        "damage_tier" to tier,
        "outcome" to "hit"
    )

    private fun missTags(weapon: String) = mapOf(
        "type" to "combat_narration",
        "scenario" to "melee_miss",
        "weapon" to weapon,
        "outcome" to "miss"
    )
}
