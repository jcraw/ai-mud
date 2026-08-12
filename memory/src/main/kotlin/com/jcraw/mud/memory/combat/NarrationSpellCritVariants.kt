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
 * Spell + critical variants for [NarrationVariantGenerator] (MUD-034m).
 */
internal object NarrationSpellCritVariants {

    /**
     * Generates variants for spell casts.
     */
    suspend fun generateSpells(llmClient: LLMClient, memoryManager: MemoryManager) {
        val spellTypes = listOf("fire", "ice", "lightning", "healing", "poison")
        val damageTiers = listOf("low", "medium", "high")

        for (spellType in spellTypes) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    NarrationVariantSupport.rememberVariant(
                        llmClient, memoryManager,
                        scenario = "spell cast",
                        weapon = "$spellType spell",
                        damageTier = tier,
                        variantNumber = variantNum + 1,
                        tags = spellTags(spellType, tier)
                    )
                }
            }
        }
    }

    /**
     * Generates variants for critical hits (double damage).
     */
    suspend fun generateCriticals(llmClient: LLMClient, memoryManager: MemoryManager) {
        val weapons = listOf("sword", "axe", "dagger", "bow")

        for (weapon in weapons) {
            repeat(3) { variantNum ->
                NarrationVariantSupport.rememberVariant(
                    llmClient, memoryManager,
                    scenario = "critical hit",
                    weapon = weapon,
                    damageTier = "critical",
                    variantNumber = variantNum + 1,
                    tags = critTags(weapon)
                )
            }
        }
    }

    private fun spellTags(spellType: String, tier: String) = mapOf(
        "type" to "combat_narration",
        "scenario" to "spell_cast",
        "spell_type" to spellType,
        "damage_tier" to tier,
        "outcome" to "hit"
    )

    private fun critTags(weapon: String) = mapOf(
        "type" to "combat_narration",
        "scenario" to "critical_hit",
        "weapon" to weapon,
        "damage_tier" to "critical",
        "outcome" to "critical"
    )
}
