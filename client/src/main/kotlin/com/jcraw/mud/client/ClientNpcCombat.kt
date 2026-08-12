@file:Suppress("ReturnCount")

package com.jcraw.mud.client

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.perception.Intent
import com.jcraw.mud.reasoning.combat.ActionCosts
import com.jcraw.mud.reasoning.combat.MonsterAIHandler
import com.jcraw.mud.reasoning.combat.SpeedCalculator
import com.jcraw.mud.reasoning.combat.TurnQueueManager
import kotlinx.coroutines.runBlocking

/**
 * NPC turn loop and action costs for [EngineGameClient]. Pure extract.
 */
object ClientNpcCombat {

    fun processNPCTurns(game: EngineGameClient) {
        println(
            "[PROCESS NPC DEBUG] Called processNPCTurns(), " +
                "turnQueue=${game.turnQueue != null}, " +
                "monsterAIHandler=${game.monsterAIHandler != null}"
        )
        val queue = game.turnQueue ?: return
        val aiHandler = game.monsterAIHandler ?: return
        while (true) {
            if (!processNextNpcTurn(game, queue, aiHandler)) break
        }
    }

    private fun processNextNpcTurn(
        game: EngineGameClient,
        queue: TurnQueueManager,
        aiHandler: MonsterAIHandler
    ): Boolean {
        val currentTime = game.worldState.gameTime
        val nextEntry = queue.peek()
        println(
            "[PROCESS NPC DEBUG] currentTime=$currentTime, " +
                "queueSize=${queue.size()}, nextEntry=$nextEntry"
        )
        if (nextEntry == null) return false
        if (nextEntry.second > currentTime) {
            println(
                "[PROCESS NPC DEBUG] NPC not ready yet: " +
                    "timerEnd=${nextEntry.second} > currentTime=$currentTime"
            )
            return false
        }
        val entityId = queue.dequeue(currentTime) ?: return false
        handleDequeuedNpc(game, queue, aiHandler, entityId, currentTime)
        return true
    }

    private fun handleDequeuedNpc(
        game: EngineGameClient,
        queue: TurnQueueManager,
        aiHandler: MonsterAIHandler,
        entityId: String,
        currentTime: Long
    ) {
        val spaceId = game.worldState.findSpaceContainingEntity(entityId)
        val npc = game.worldState.getEntity(entityId) as? Entity.NPC
        if (npc == null || spaceId == null) return
        if (spaceId != game.worldState.player.currentRoomId) {
            reenqueueNpc(queue, entityId, currentTime, npc)
            return
        }
        val decision = runBlocking { aiHandler.decideAction(entityId, game.worldState) }
        ClientNpcAttack.executeNPCDecision(game, npc, decision)
        val combat = npc.components[ComponentType.COMBAT] as? CombatComponent
        if (combat == null || !combat.isDead()) {
            reenqueueNpc(queue, entityId, currentTime, npc)
        }
    }

    private fun reenqueueNpc(
        queue: TurnQueueManager,
        entityId: String,
        currentTime: Long,
        npc: Entity.NPC
    ) {
        val skillComponent = npc.components[ComponentType.SKILL] as? SkillComponent
        val speedLevel = skillComponent?.getEffectiveLevel("Speed") ?: 0
        val cost = SpeedCalculator.calculateActionCost("melee_attack", speedLevel)
        queue.enqueue(entityId, currentTime + cost)
    }

    fun getBaseCostForIntent(intent: Intent): Int = when (intent) {
        is Intent.Attack -> ActionCosts.MELEE_ATTACK
        is Intent.Move, is Intent.Travel -> ActionCosts.MOVE
        is Intent.Use, is Intent.UseItem -> ActionCosts.ITEM_USE
        is Intent.Talk, is Intent.Say, is Intent.Persuade, is Intent.Intimidate,
        is Intent.Check -> ActionCosts.SOCIAL
        is Intent.Pickpocket -> ActionCosts.HIDE
        else -> ActionCosts.SOCIAL
    }
}
