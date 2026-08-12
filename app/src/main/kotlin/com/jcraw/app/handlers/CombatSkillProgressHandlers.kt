@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.combat.AttackResult

/**
 * Skill progression + health descriptors for combat (MUD-034k pure-move).
 */
internal object CombatSkillProgressHandlers {

    fun processSkillProgression(game: MudGame, attackResult: AttackResult) {
        val (attackerOk, defenderOk) = successFlags(attackResult)
        progressAttacker(game, attackResult, attackerOk)
        progressDefender(game, attackResult, defenderOk)
    }

    private fun successFlags(attackResult: AttackResult): Pair<Boolean, Boolean> {
        val attackerSuccess = when (attackResult) {
            is AttackResult.Hit -> true
            is AttackResult.Miss -> false
            else -> false
        }
        val defenderSuccess = when (attackResult) {
            is AttackResult.Hit -> false
            is AttackResult.Miss -> true
            else -> false
        }
        return attackerSuccess to defenderSuccess
    }

    private fun progressAttacker(game: MudGame, attackResult: AttackResult, success: Boolean) {
        val playerId = game.worldState.player.id
        attackResult.attackerSkillsUsed.forEach { skillName ->
            game.skillManager.attemptSkillProgress(playerId, skillName, 10L, success)
                .onSuccess { events -> displaySkillEvents(events, skillName) }
        }
    }

    private fun progressDefender(game: MudGame, attackResult: AttackResult, success: Boolean) {
        val defenderId = attackResult.defenderId
        val isPlayer = defenderId == game.worldState.player.id
        if (!isPlayer && !GameConfig.enableNPCLuckyProgression) return
        attackResult.defenderSkillsUsed.forEach { skillName ->
            val result = game.skillManager.attemptSkillProgress(
                defenderId, skillName, 10L, success
            )
            if (isPlayer) {
                result.onSuccess { events -> displaySkillEvents(events, skillName) }
            }
        }
    }

    fun displaySkillEvents(events: List<SkillEvent>, skillName: String) {
        events.forEach { event ->
            when (event) {
                is SkillEvent.SkillUnlocked ->
                    println("🎉 Unlocked $skillName (lucky progression)!")
                is SkillEvent.LevelUp -> displayLevelUp(event, skillName)
                else -> {}
            }
        }
    }

    private fun displayLevelUp(event: SkillEvent.LevelUp, skillName: String) {
        val method = if (event.oldLevel == 0) "(lucky progression)" else "(lucky level-up)"
        println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel} $method")
        if (event.isAtPerkMilestone) {
            println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
        }
    }

    fun getHealthDescriptor(currentHp: Int, maxHp: Int, entityName: String): String {
        val healthPercent = (currentHp.toDouble() / maxHp.toDouble() * 100).toInt()
        val descriptor = healthBand(healthPercent)
        return "The $entityName $descriptor."
    }

    private fun healthBand(healthPercent: Int): String = when {
        healthPercent >= 100 -> "is in perfect health"
        healthPercent >= 90 -> "has a few scratches"
        healthPercent >= 75 -> "has some small wounds"
        healthPercent >= 50 -> "has quite a few wounds"
        healthPercent >= 30 -> "is bleeding badly"
        healthPercent >= 15 -> "looks pretty hurt"
        healthPercent >= 5 -> "is in awful condition"
        else -> "is nearly dead"
    }
}
