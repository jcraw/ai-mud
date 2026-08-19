package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Skill progression + health descriptors for combat (MUD-039).
 */
internal object CombatSkillProgressHandlers {

    fun processSkillProgression(game: MudGame, attackResult: AttackResult) {
        val (attackerOk, defenderOk) = CombatHandlerPures.successFlags(attackResult)
        CombatHandlerPures.progressUsedSkills(
            game.skillManager,
            game.worldState.player.id,
            attackResult.attackerSkillsUsed,
            attackerOk
        ).forEach { (skillName, events) -> displaySkillEvents(events, skillName) }
        progressDefender(game, attackResult, defenderOk)
    }

    private fun progressDefender(game: MudGame, attackResult: AttackResult, success: Boolean) {
        val defenderId = attackResult.defenderId
        val isPlayer = defenderId == game.worldState.player.id
        if (!isPlayer && !GameConfig.enableNPCLuckyProgression) return
        CombatHandlerPures.progressUsedSkills(
            game.skillManager,
            defenderId,
            attackResult.defenderSkillsUsed,
            success
        ).forEach { (skillName, events) ->
            if (isPlayer) displaySkillEvents(events, skillName)
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

    fun getHealthDescriptor(currentHp: Int, maxHp: Int, entityName: String): String =
        CombatHandlerPures.healthDescriptor(currentHp, maxHp, entityName)
}
