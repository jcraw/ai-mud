package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.config.GameConfig
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatHandlerPures

/**
 * Skill progression + health descriptors for client combat (MUD-039).
 */
internal object ClientCombatSkillProgressHandlers {

    fun processSkillProgression(game: EngineGameClient, attackResult: AttackResult) {
        val skillManager = game.skillManager ?: return
        val (attackerOk, defenderOk) = CombatHandlerPures.successFlags(attackResult)
        CombatHandlerPures.progressUsedSkills(
            skillManager,
            game.worldState.player.id,
            attackResult.attackerSkillsUsed,
            attackerOk
        ).forEach { (skillName, events) -> displaySkillEvents(game, events, skillName) }
        progressDefender(game, attackResult, defenderOk)
    }

    private fun progressDefender(
        game: EngineGameClient,
        attackResult: AttackResult,
        success: Boolean
    ) {
        val skillManager = game.skillManager ?: return
        val defenderId = attackResult.defenderId
        val isPlayer = defenderId == game.worldState.player.id
        if (!isPlayer && !GameConfig.enableNPCLuckyProgression) return
        CombatHandlerPures.progressUsedSkills(
            skillManager,
            defenderId,
            attackResult.defenderSkillsUsed,
            success
        ).forEach { (skillName, events) ->
            if (isPlayer) displaySkillEvents(game, events, skillName)
        }
    }

    fun displaySkillEvents(game: EngineGameClient, events: List<SkillEvent>, skillName: String) {
        events.forEach { event ->
            when (event) {
                is SkillEvent.SkillUnlocked -> game.emitEvent(
                    GameEvent.System(
                        "🎉 Unlocked $skillName (lucky progression)!",
                        GameEvent.MessageLevel.INFO
                    )
                )
                is SkillEvent.LevelUp -> displayLevelUp(game, event, skillName)
                else -> {}
            }
        }
    }

    private fun displayLevelUp(
        game: EngineGameClient,
        event: SkillEvent.LevelUp,
        skillName: String
    ) {
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

    fun getHealthDescriptor(currentHp: Int, maxHp: Int, entityName: String): String =
        CombatHandlerPures.healthDescriptor(currentHp, maxHp, entityName)
}
