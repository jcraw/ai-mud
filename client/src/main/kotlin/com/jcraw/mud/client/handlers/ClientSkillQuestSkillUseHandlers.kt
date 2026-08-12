@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.skill.SkillCheckResult
import com.jcraw.mud.reasoning.skill.SkillDefinitions

/**
 * Use-skill handler for [ClientSkillQuestHandlers] facade.
 * Healing and action→skill inference live in companion extracts.
 */
object ClientSkillQuestSkillUseHandlers {

    fun handleUseSkill(game: EngineGameClient, skill: String?, action: String) {
        val lower = action.lowercase()
        if (lower.contains("heal") || lower.contains("cure") || lower.contains("mend")) {
            ClientSkillQuestHealingHandlers.handleHealingSpell(game)
            return
        }
        val skillName = skill ?: ClientSkillQuestSkillInfer.inferSkillFromAction(action)
        if (skillName == null) {
            game.emitEvent(GameEvent.System(
                "Could not determine which skill to use for: $action",
                GameEvent.MessageLevel.WARNING
            ))
            return
        }
        if (SkillDefinitions.getSkill(skillName) == null) {
            game.emitEvent(GameEvent.System("Unknown skill: $skillName", GameEvent.MessageLevel.WARNING))
            return
        }
        runSkillCheckAndProgress(game, skillName, action)
    }

    private fun runSkillCheckAndProgress(game: EngineGameClient, skillName: String, action: String) {
        val difficulty = 15
        val checkResult = game.skillManager.checkSkill(
            entityId = game.worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            game.emitEvent(GameEvent.System(
                "Skill check failed: ${error.message}",
                GameEvent.MessageLevel.ERROR
            ))
            return
        }
        val xpEvents = grantUseSkillXp(game, skillName, action, checkResult.success) ?: return
        game.emitEvent(GameEvent.Narrative(
            formatSkillCheckOutput(skillName, action, checkResult, difficulty, xpEvents)
        ))
    }

    private fun grantUseSkillXp(
        game: EngineGameClient,
        skillName: String,
        action: String,
        success: Boolean
    ): List<SkillEvent>? {
        return game.skillManager.grantXp(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = 50L,
            success = success
        ).getOrElse {
            val message = "You attempt to $action with $skillName, but the skill is not unlocked.\n" +
                "Try 'train $skillName with <npc>' or use it repeatedly to unlock it."
            game.emitEvent(GameEvent.System(message, GameEvent.MessageLevel.INFO))
            return null
        }
    }

    private fun formatSkillCheckOutput(
        skillName: String,
        action: String,
        checkResult: SkillCheckResult,
        difficulty: Int,
        xpEvents: List<SkillEvent>
    ): String = buildString {
        appendLine("You attempt to $action using $skillName:")
        appendLine()
        val total = checkResult.roll + checkResult.skillLevel
        appendLine("Roll: d20(${checkResult.roll}) + Level(${checkResult.skillLevel}) = $total vs DC $difficulty")
        appendLine(checkResult.narrative)
        appendLine()
        appendXpLines(this, skillName, xpEvents)
    }

    private fun appendXpLines(sb: StringBuilder, skillName: String, xpEvents: List<SkillEvent>) {
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> {
                    sb.appendLine("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                }
                is SkillEvent.LevelUp -> {
                    sb.appendLine()
                    sb.appendLine("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                    if (event.isAtPerkMilestone) {
                        sb.appendLine("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                    }
                }
                else -> {}
            }
        }
    }
}
