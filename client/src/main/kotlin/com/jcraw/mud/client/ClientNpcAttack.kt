@file:Suppress("TooManyFunctions")

package com.jcraw.mud.client

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.combat.AIDecision
import com.jcraw.mud.reasoning.combat.AttackResult
import kotlinx.coroutines.runBlocking

private const val FALLBACK_DAMAGE_SIDES = 6

/**
 * NPC attack resolution and skill progression for [EngineGameClient]. Pure extract.
 */
object ClientNpcAttack {

    fun executeNPCDecision(game: EngineGameClient, npc: Entity.NPC, decision: AIDecision) {
        val msg = when (decision) {
            is AIDecision.Attack -> {
                game.emitEvent(GameEvent.Combat("\n${npc.name} attacks you!"))
                executeNPCAttack(game, npc)
                return
            }
            is AIDecision.Defend -> "\n${npc.name} takes a defensive stance."
            is AIDecision.UseItem -> "\n${npc.name} attempts to use an item."
            is AIDecision.Flee -> "\n${npc.name} attempts to flee!"
            is AIDecision.Wait -> "\n${npc.name} waits, watching carefully."
            is AIDecision.Error -> return
        }
        game.emitEvent(GameEvent.Combat(msg))
    }

    fun executeNPCAttack(game: EngineGameClient, npc: Entity.NPC) {
        val resolver = game.attackResolver
        if (resolver == null) {
            applySimpleDamage(game, npc)
            return
        }
        val result = runBlocking {
            resolver.resolveAttack(
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
    }

    private fun applyHit(game: EngineGameClient, npc: Entity.NPC, result: AttackResult.Hit) {
        val newPlayer = game.worldState.player.takeDamage(result.damage)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        val flavor = attackFlavor(game, npc.name, result.damage, newPlayer.isDead())
        val dmg =
            "${npc.name} hits you for ${result.damage} damage! " +
                "(HP: ${newPlayer.health}/${newPlayer.maxHealth})"
        game.emitEvent(GameEvent.Combat("$flavor\n$dmg"))
        processNPCAttackSkillProgression(game, result, defenderSuccess = false)
        if (newPlayer.isDead()) game.handlePlayerDeath()
    }

    private fun applyMiss(game: EngineGameClient, npc: Entity.NPC, result: AttackResult.Miss) {
        val narrative = if (result.wasDodged) {
            "You dodge ${npc.name}'s attack!"
        } else {
            "${npc.name} misses you!"
        }
        game.emitEvent(GameEvent.Combat(narrative))
        processNPCAttackSkillProgression(game, result, defenderSuccess = true)
    }

    private fun applySimpleDamage(game: EngineGameClient, npc: Entity.NPC) {
        val damage = (1..FALLBACK_DAMAGE_SIDES).random()
        val newPlayer = game.worldState.player.takeDamage(damage)
        game.worldState = game.worldState.updatePlayer(newPlayer)
        game.emitEvent(
            GameEvent.Combat(
                "${npc.name} hits you for $damage damage! " +
                    "(HP: ${newPlayer.health}/${newPlayer.maxHealth})"
            )
        )
        if (newPlayer.isDead()) game.handlePlayerDeath()
    }

    private fun attackFlavor(
        game: EngineGameClient,
        npcName: String,
        damage: Int,
        isDeath: Boolean
    ): String {
        if (game.llmClient == null) return "$npcName strikes with deadly precision!"
        return runBlocking { generateNPCAttackNarration(game, npcName, damage, isDeath) }
    }

    fun processNPCAttackSkillProgression(
        game: EngineGameClient,
        attackResult: AttackResult,
        defenderSuccess: Boolean
    ) {
        val playerId = game.worldState.player.id
        attackResult.defenderSkillsUsed.forEach { skillName ->
            game.skillManager.attemptSkillProgress(
                entityId = playerId,
                skillName = skillName,
                baseXp = 10L,
                success = defenderSuccess
            ).onSuccess { events ->
                events.forEach { emitSkillEvent(game, skillName, it) }
            }
        }
    }

    private fun emitSkillEvent(game: EngineGameClient, skillName: String, event: SkillEvent) {
        when (event) {
            is SkillEvent.SkillUnlocked -> emitUnlocked(game, skillName)
            is SkillEvent.LevelUp -> emitLevelUp(game, skillName, event)
            is SkillEvent.PerkUnlocked -> game.emitEvent(
                GameEvent.System(
                    "⚡ Applied perk: ${event.perk.name}",
                    GameEvent.MessageLevel.INFO
                )
            )
            is SkillEvent.XpGained, is SkillEvent.SkillCheckAttempt -> Unit
        }
    }

    private fun emitUnlocked(game: EngineGameClient, skillName: String) {
        game.emitEvent(
            GameEvent.System(
                "🎉 Unlocked $skillName (lucky progression)!",
                GameEvent.MessageLevel.INFO
            )
        )
    }

    private fun emitLevelUp(game: EngineGameClient, skillName: String, event: SkillEvent.LevelUp) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        game.emitEvent(
            GameEvent.System(
                "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method",
                GameEvent.MessageLevel.INFO
            )
        )
        if (event.isAtPerkMilestone) {
            game.emitEvent(
                GameEvent.System(
                    "⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.",
                    GameEvent.MessageLevel.INFO
                )
            )
        }
    }

    suspend fun generateNPCAttackNarration(
        game: EngineGameClient,
        npcName: String,
        damage: Int,
        isDeath: Boolean
    ): String {
        val client = game.llmClient ?: return "$npcName strikes with deadly force!"
        val death = if (isDeath) " The blow is fatal." else ""
        val user = attackPrompt(npcName, damage, death)
        return try {
            val text = client.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = "You are a vivid combat narrator for a fantasy game.",
                userContext = user,
                temperature = 0.8
            ).choices.firstOrNull()?.message?.content ?: "$npcName strikes with deadly force!"
            text.trim().removeSuffix(".")
        } catch (_: Exception) {
            "$npcName strikes with brutal force!"
        }
    }

    private fun attackPrompt(npcName: String, damage: Int, death: String): String =
        "Generate a single short sentence (10-15 words) describing how $npcName attacks " +
            "the player, dealing $damage damage.$death\n" +
            "Be vivid and visceral, from the NPC's perspective. " +
            "Just the action description, no damage numbers."
}
