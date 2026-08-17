package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.WorldState

/**
 * Pure apply for a resolved [AttackResult.Hit] → write defender combat HP into the space.
 * Shared by console and GUI hit branches. Narration / skill-progress / death stay in handlers.
 */
object CombatHitApply {

    sealed class Result {
        data class Success(val world: WorldState) : Result()
        data class Failure(val message: String) : Result()
    }

    fun apply(
        world: WorldState,
        spaceId: String,
        npc: Entity.NPC,
        hit: AttackResult.Hit
    ): Result {
        if (!defenderPresent(world, spaceId, npc.id)) {
            return Result.Failure("Invalid attacker or defender")
        }
        val updatedNpc = npc.withComponent(hit.updatedDefenderCombat)
        return Result.Success(world.replaceEntityInSpace(spaceId, npc.id, updatedNpc))
    }

    /** Pass-through when [AttackResolver] already failed (e.g. missing defender). */
    fun apply(result: AttackResult.Failure): Result = Result.Failure(result.reason)

    private fun defenderPresent(world: WorldState, spaceId: String, npcId: String): Boolean =
        world.getEntitiesInSpace(spaceId).any { it.id == npcId }
}
