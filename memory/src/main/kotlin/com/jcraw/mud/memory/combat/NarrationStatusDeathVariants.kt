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
 * Status-effect + death-blow variants for [NarrationVariantGenerator] (MUD-034m).
 */
internal object NarrationStatusDeathVariants {

    /**
     * Generates variants for status effect applications.
     */
    suspend fun generateStatusEffects(llmClient: LLMClient, memoryManager: MemoryManager) {
        val effects = listOf("poison", "slow", "burn", "freeze", "stun", "weaken")

        for (effect in effects) {
            repeat(2) { variantNum ->
                NarrationVariantSupport.rememberVariant(
                    llmClient, memoryManager,
                    scenario = "status effect",
                    weapon = effect,
                    damageTier = "effect",
                    variantNumber = variantNum + 1,
                    tags = mapOf(
                        "type" to "combat_narration",
                        "scenario" to "status_effect",
                        "effect_type" to effect,
                        "outcome" to "effect_applied"
                    )
                )
            }
        }
    }

    /**
     * Generates variants for killing blows.
     */
    suspend fun generateDeathBlows(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("sword", "axe", "dagger", "bow", "spell")

        for (weapon in weapons) {
            repeat(3) { variantNum ->
                NarrationVariantSupport.rememberVariant(
                    llmClient, memoryManager,
                    scenario = "death blow",
                    weapon = weapon,
                    damageTier = "lethal",
                    variantNumber = variantNum + 1,
                    tags = mapOf(
                        "type" to "combat_narration",
                        "scenario" to "death_blow",
                        "weapon" to weapon,
                        "outcome" to "death"
                    )
                )
            }
        }
    }
}
