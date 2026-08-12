@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity

/**
 * Attack / combat handlers for V3 test engine (MUD-034f).
 */
internal object V3TestCombatHandlers {

    fun handleAttack(state: V3TestEngineState, target: String?): String {
        val spaceId = state.worldState.player.currentRoomId
        val space = state.worldState.getCurrentSpace()
        if (space?.isSafeZone == true) {
            return "This is a safe zone. Combat is not allowed here."
        }
        val entities = state.worldState.getEntitiesInSpace(spaceId)
        val npc = resolveAttackTarget(entities, target) ?: return "No target to attack."
        val combatComponent = npc.getComponent<CombatComponent>(ComponentType.COMBAT)
        if (combatComponent == null || combatComponent.currentHp <= 0) {
            return "${npc.name} is already dead."
        }
        return applyAttackDamage(state, spaceId, npc, combatComponent)
    }

    private fun resolveAttackTarget(entities: List<Entity>, target: String?): Entity.NPC? {
        return if (target != null) {
            entities.filterIsInstance<Entity.NPC>()
                .find { it.name.contains(target, ignoreCase = true) }
        } else {
            entities.filterIsInstance<Entity.NPC>().firstOrNull { it.isHostile }
        }
    }

    private fun applyAttackDamage(
        state: V3TestEngineState,
        spaceId: String,
        npc: Entity.NPC,
        combatComponent: CombatComponent
    ): String {
        val damage = (5..15).random()
        val newHealth = (combatComponent.currentHp - damage).coerceAtLeast(0)
        replaceNpc(state, npc, combatComponent.copy(currentHp = newHealth))
        return narrateAttack(state, spaceId, npc, damage, newHealth)
    }

    private fun replaceNpc(state: V3TestEngineState, npc: Entity.NPC, combat: CombatComponent) {
        val newNpc = npc.withComponent(combat)
        state.worldState = state.worldState.replaceEntityInSpace(
            state.worldState.player.currentRoomId, npc.id, newNpc
        )
    }

    private fun narrateAttack(
        state: V3TestEngineState,
        spaceId: String,
        npc: Entity.NPC,
        damage: Int,
        newHealth: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("You attack ${npc.name} for $damage damage!")
        if (newHealth <= 0) {
            sb.appendLine("${npc.name} has been defeated!")
            state.worldState = state.worldState.removeEntityFromSpace(spaceId, npc.id)
        } else {
            sb.appendLine("${npc.name} has $newHealth health remaining.")
        }
        return sb.toString().trim()
    }
}
