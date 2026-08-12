@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.reasoning.skill.SkillCheckResult
import com.jcraw.mud.reasoning.skill.SkillDefinitions

/**
 * Use-skill handler for [SkillQuestHandlers] facade.
 * Healing and action→skill inference live in companion extracts.
 */
object SkillQuestSkillUseHandlers {

    fun handleUseSkill(game: MudGame, skill: String?, action: String) {
        val lower = action.lowercase()
        if (lower.contains("heal") || lower.contains("cure") || lower.contains("mend")) {
            SkillQuestHealingHandlers.handleHealingSpell(game)
            return
        }
        val skillName = skill ?: SkillQuestSkillInfer.inferSkillFromAction(action)
        if (skillName == null) {
            println("\nCould not determine which skill to use for: $action")
            return
        }
        if (SkillDefinitions.getSkill(skillName) == null) {
            println("\nUnknown skill: $skillName")
            return
        }
        runSkillCheckAndProgress(game, skillName, action)
    }

    private fun runSkillCheckAndProgress(game: MudGame, skillName: String, action: String) {
        val difficulty = 15
        val checkResult = game.skillManager.checkSkill(
            entityId = game.worldState.player.id,
            skillName = skillName,
            difficulty = difficulty
        ).getOrElse { error ->
            println("\nSkill check failed: ${error.message}")
            return
        }
        val xpEvents = grantUseSkillXp(game, skillName, action, checkResult.success) ?: return
        printSkillCheckHeader(skillName, action, checkResult, difficulty)
        printXpEvents(skillName, xpEvents)
    }

    private fun grantUseSkillXp(
        game: MudGame,
        skillName: String,
        action: String,
        success: Boolean
    ): List<SkillEvent>? {
        return game.skillManager.attemptSkillProgress(
            entityId = game.worldState.player.id,
            skillName = skillName,
            baseXp = 50L,
            success = success
        ).getOrElse {
            println("\nYou attempt to $action with $skillName, but the skill is not unlocked.")
            println("Try 'train $skillName with <npc>' or use it repeatedly to unlock it.")
            return null
        }
    }

    private fun printSkillCheckHeader(
        skillName: String,
        action: String,
        checkResult: SkillCheckResult,
        difficulty: Int
    ) {
        println("\nYou attempt to $action using $skillName:")
        println()
        val total = checkResult.roll + checkResult.skillLevel
        println("Roll: d20(${checkResult.roll}) + Level(${checkResult.skillLevel}) = $total vs DC $difficulty")
        println(checkResult.narrative)
        println()
    }

    private fun printXpEvents(skillName: String, xpEvents: List<SkillEvent>) {
        xpEvents.forEach { event ->
            when (event) {
                is SkillEvent.XpGained -> {
                    println("+${event.xpAmount} XP to $skillName (${event.currentXp} total, level ${event.currentLevel})")
                }
                is SkillEvent.LevelUp -> {
                    println()
                    println("🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}")
                    if (event.isAtPerkMilestone) {
                        println("⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.")
                    }
                }
                else -> {}
            }
        }
    }
}
