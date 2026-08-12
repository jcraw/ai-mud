@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.MemoryManager
import com.jcraw.mud.memory.combat.NarrationMatcher
import com.jcraw.sophia.llm.LLMClient

/**
 * Generates vivid, atmospheric combat narratives using LLM with combat history.
 * Bodies live in CombatNarrator* extracts (MUD-034k pure-move).
 */
class CombatNarrator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager? = null,
    private val narrationMatcher: NarrationMatcher? = null
) {

    suspend fun narrateAction(
        weapon: String,
        damage: Int,
        maxHp: Int,
        isHit: Boolean,
        isCritical: Boolean = false,
        isDeath: Boolean = false,
        isSpell: Boolean = false,
        targetName: String = "enemy"
    ): String = CombatNarratorAction.narrate(
        ActionNarrateParams(
            llmClient, memoryManager, narrationMatcher,
            weapon, damage, maxHp, isHit, isCritical, isDeath, isSpell, targetName
        )
    )

    suspend fun narrateCombatRound(
        worldState: WorldState,
        npc: Entity.NPC,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean,
        playerDied: Boolean
    ): String = CombatNarratorRound.narrate(
        RoundNarrateParams(
            llmClient, memoryManager, worldState, npc,
            playerDamage, npcDamage, npcDied, playerDied
        )
    )

    suspend fun narrateCombatStart(worldState: WorldState, npc: Entity.NPC): String =
        CombatNarratorStart.narrate(llmClient, worldState, npc)
}
