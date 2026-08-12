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

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.sophia.llm.LLMClient

/**
 * Handles AI decision-making for NPCs in combat.
 * Bodies live in MonsterAI* extracts (MUD-034k pure-move).
 */
class MonsterAIHandler(
    private val llmClient: LLMClient?
) {

    suspend fun decideAction(npcId: String, worldState: WorldState): AIDecision {
        val npc = worldState.getEntity(npcId) as? Entity.NPC
            ?: return AIDecision.Error("NPC not found: $npcId")
        return decideForNpc(npc, worldState)
    }

    private suspend fun decideForNpc(npc: Entity.NPC, worldState: WorldState): AIDecision {
        val skills = npc.getComponent<SkillComponent>(ComponentType.SKILL)
        val combat = npc.getComponent<CombatComponent>(ComponentType.COMBAT)
            ?: return AIDecision.Error("NPC has no combat component")
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        val intelligence = skills?.getEffectiveLevel("Intelligence") ?: 0
        val wisdom = skills?.getEffectiveLevel("Wisdom") ?: 0
        val decision = pickDecision(npc, combat, social, intelligence, wisdom, worldState)
        return PersonalityAI.modifyDecision(decision, social, combat)
    }

    private suspend fun pickDecision(
        npc: Entity.NPC,
        combat: CombatComponent,
        social: SocialComponent?,
        intelligence: Int,
        wisdom: Int,
        worldState: WorldState
    ): AIDecision {
        if (llmClient == null) {
            return MonsterAIFallback.decide(npc, combat, social, worldState)
        }
        return MonsterAILlm.tryDecision(
            MonsterAILlm.Ctx(llmClient, npc, combat, social, intelligence, wisdom, worldState)
        ) ?: MonsterAIFallback.decide(npc, combat, social, worldState)
    }
}
