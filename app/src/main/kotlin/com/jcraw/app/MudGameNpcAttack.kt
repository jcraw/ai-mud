@file:Suppress("TooManyFunctions", "MagicNumber")

package com.jcraw.app

import com.jcraw.mud.core.Entity
import com.jcraw.mud.reasoning.combat.AIDecision
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.SpeedCalculator
import kotlinx.coroutines.runBlocking

private const val FALLBACK_DAMAGE_SIDES = 6

/**
 * NPC attack resolution and boss summon for [MudGame]. Pure extract.
 */
object MudGameNpcAttack {

    /** Execute an NPC's AI decision */
    fun executeNPCDecision(game: MudGame, npc: Entity.NPC, decision: AIDecision) {
        when (decision) {
            is AIDecision.Attack -> {
                println("\n${npc.name} attacks you!")
                executeNPCAttack(game, npc)
            }
            is AIDecision.Defend -> println("\n${npc.name} takes a defensive stance.")
            is AIDecision.UseItem -> println("\n${npc.name} attempts to use an item.")
            is AIDecision.Flee -> println("\n${npc.name} attempts to flee!")
            is AIDecision.Wait -> println("\n${npc.name} waits, watching carefully.")
            is AIDecision.Error -> Unit
        }
    }

    /** Execute NPC attack on player */
    fun executeNPCAttack(game: MudGame, npc: Entity.NPC) {
        val result = runBlocking {
            game.attackResolver.resolveAttack(
                attackerId = npc.id,
                defenderId = game.worldState.player.id,
                action = "${npc.name} attacks",
                worldState = game.worldState,
                skillManager = game.skillManager
            )
        }
        when (result) {
            is AttackResult.Hit -> applyHit(game, npc, result)
            is AttackResult.Miss -> applyMiss(game, npc, result)
            is AttackResult.Failure -> applySimpleDamage(game, npc)
        }
        maybeBossSummon(game, npc)
    }

    private fun applyHit(game: MudGame, npc: Entity.NPC, result: AttackResult.Hit) {
        val newPlayer = game.worldState.player.takeDamage(result.damage)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        val flavor = hitFlavor(game, npc.name, result.damage, newPlayer.isDead())
        val dmg =
            "${npc.name} hits you for ${result.damage} damage! " +
                "(HP: ${newPlayer.health}/${newPlayer.maxHealth})"
        println("$flavor\n$dmg")
        MudGameNpcAttackSkills.processProgression(game, result)
        if (newPlayer.isDead()) game.handlePlayerDeath()
    }

    private fun hitFlavor(game: MudGame, npcName: String, damage: Int, isDeath: Boolean): String {
        if (game.llmService == null) return "$npcName strikes with deadly precision!"
        return runBlocking {
            MudGameNpcAttackSkills.generateNarration(game, npcName, damage, isDeath)
        }
    }

    private fun applyMiss(game: MudGame, npc: Entity.NPC, result: AttackResult.Miss) {
        val narrative = if (result.wasDodged) {
            "You dodge ${npc.name}'s attack!"
        } else {
            "${npc.name} misses you!"
        }
        println(narrative)
        MudGameNpcAttackSkills.processProgression(game, result)
    }

    private fun applySimpleDamage(game: MudGame, npc: Entity.NPC) {
        val damage = (1..FALLBACK_DAMAGE_SIDES).random()
        val newPlayer = game.worldState.player.takeDamage(damage)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        println(
            "${npc.name} hits you for $damage damage! " +
                "(HP: ${newPlayer.health}/${newPlayer.maxHealth})"
        )
        if (newPlayer.isDead()) game.handlePlayerDeath()
    }

    private fun maybeBossSummon(game: MudGame, npc: Entity.NPC) {
        val hasSummoned = game.bossSummonedTracker.contains(npc.id)
        if (!game.bossCombatEnhancements.shouldSummon(npc, hasSummoned)) return
        val summonResult = game.bossCombatEnhancements.summonMinions(npc, difficulty = 5)
        summonResult.onSuccess { minions ->
            if (minions.isNotEmpty()) applyBossSummon(game, npc, minions)
        }.onFailure { e ->
            println("Debug: Boss summon failed: ${e.message}")
        }
    }

    private fun applyBossSummon(game: MudGame, npc: Entity.NPC, minions: List<Entity.NPC>) {
        game.bossSummonedTracker.add(npc.id)
        val narration =
            com.jcraw.mud.reasoning.boss.BossCombatEnhancements.getSummonNarration(npc, minions.size)
        println("\n" + "=".repeat(60))
        println(narration)
        println("=".repeat(60))
        val spaceId = game.worldState.player.currentRoomId
        minions.forEach { m ->
            game.worldState = game.worldState.addEntityToSpace(spaceId, m)
        }
        enqueueMinions(game, minions)
    }

    private fun enqueueMinions(game: MudGame, minions: List<Entity.NPC>) {
        val queue = game.turnQueue ?: return
        minions.forEach { minion ->
            val cost = SpeedCalculator.calculateActionCost("melee_attack", 0)
            queue.enqueue(minion.id, game.worldState.gameTime + cost)
        }
    }
}
