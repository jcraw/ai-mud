@file:Suppress("TooManyFunctions", "MagicNumber")

package com.jcraw.app

import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.combat.AttackResult

/**
 * NPC-attack skill progression and LLM narration for [MudGame]. Pure extract.
 */
object MudGameNpcAttackSkills {

    /** Process skill progression for NPC attacks (player is defender). */
    fun processProgression(game: MudGame, attackResult: AttackResult) {
        val playerId = game.worldState.player.id
        val attackerSuccess = attackResult is AttackResult.Hit
        val defenderSuccess = attackResult is AttackResult.Miss
        processAttackerSkills(game, attackResult, attackerSuccess)
        processDefenderSkills(game, attackResult, playerId, defenderSuccess)
    }

    private fun processAttackerSkills(
        game: MudGame,
        attackResult: AttackResult,
        attackerSuccess: Boolean
    ) {
        if (!com.jcraw.mud.config.GameConfig.enableNPCLuckyProgression) return
        val attackerId = attackResult.attackerId
        attackResult.attackerSkillsUsed.forEach { skillName ->
            game.skillManager.attemptSkillProgress(
                entityId = attackerId,
                skillName = skillName,
                baseXp = 10L,
                success = attackerSuccess
            )
        }
    }

    private fun processDefenderSkills(
        game: MudGame,
        attackResult: AttackResult,
        playerId: String,
        defenderSuccess: Boolean
    ) {
        attackResult.defenderSkillsUsed.forEach { skillName ->
            game.skillManager.attemptSkillProgress(
                entityId = playerId,
                skillName = skillName,
                baseXp = 10L,
                success = defenderSuccess
            ).onSuccess { events -> displayEvents(events, skillName) }
        }
    }

    private fun displayEvents(events: List<SkillEvent>, skillName: String) {
        events.forEach { event ->
            when (event) {
                is SkillEvent.SkillUnlocked ->
                    println("🎉 Unlocked $skillName (lucky progression)!")
                is SkillEvent.LevelUp -> displayLevelUp(skillName, event)
                is SkillEvent.PerkUnlocked ->
                    println("⚡ Applied perk: ${event.perk.name}")
                is SkillEvent.XpGained, is SkillEvent.SkillCheckAttempt -> Unit
            }
        }
    }

    private fun displayLevelUp(skillName: String, event: SkillEvent.LevelUp) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method")
        if (event.isAtPerkMilestone) {
            println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
        }
    }

    /** Generate LLM narration for NPC attack */
    suspend fun generateNarration(
        game: MudGame,
        npcName: String,
        damage: Int,
        isDeath: Boolean
    ): String {
        val client = game.llmService ?: return "$npcName strikes with deadly force!"
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
