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
 * Pre-generates combat narration variants for common scenarios.
 * This is typically run offline to populate the vector database with diverse
 * narration options that can be retrieved quickly during gameplay.
 *
 * Generated variants are tagged with metadata (weapon type, damage tier, outcome)
 * to enable semantic search via vector DB.
 *
 * Thin orchestrator — bodies in Narration* extracts (MUD-034m).
 */
class NarrationVariantGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager
) {

    /**
     * Generates and stores narration variants for all common combat scenarios.
     */
    suspend fun generateAllVariants() {
        NarrationMeleeVariants.generateHits(llmClient, memoryManager)
        NarrationMeleeVariants.generateMisses(llmClient, memoryManager)
        NarrationRangedVariants.generateHits(llmClient, memoryManager)
        NarrationRangedVariants.generateMisses(llmClient, memoryManager)
        NarrationSpellCritVariants.generateSpells(llmClient, memoryManager)
        NarrationSpellCritVariants.generateCriticals(llmClient, memoryManager)
        NarrationStatusDeathVariants.generateStatusEffects(llmClient, memoryManager)
        NarrationStatusDeathVariants.generateDeathBlows(llmClient, memoryManager)
    }
}
