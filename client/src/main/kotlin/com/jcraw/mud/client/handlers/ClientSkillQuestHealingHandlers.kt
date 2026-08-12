@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.reasoning.skill.ResourceManager

/**
 * Healing-spell fragment for client skill-use cluster.
 * Pure extract from [ClientSkillQuestHandlers].
 */
object ClientSkillQuestHealingHandlers {

    fun handleHealingSpell(game: EngineGameClient) {
        val player = game.worldState.player
        val waterMagic = resolveWaterMagic(game, player) ?: return
        val manaCost = 20
        val healAmount = 15 + (waterMagic.getEffectiveLevel() * 3)
        val resourceManager = ResourceManager(game.skillManager.getSkillComponentRepository())
        if (!consumeManaOrWarn(game, resourceManager, player.id, manaCost)) return
        if (player.health >= player.maxHealth) {
            game.emitEvent(GameEvent.System("You are already at full health.", GameEvent.MessageLevel.INFO))
            return
        }
        applyHealing(game, player, resourceManager, manaCost, healAmount)
    }

    private fun resolveWaterMagic(game: EngineGameClient, player: PlayerState): SkillState? {
        val skillComponent = game.skillManager.getSkillComponent(player.id)
        val waterMagic = skillComponent.getSkill("Water Magic")
        if (waterMagic == null || !waterMagic.unlocked) {
            game.emitEvent(GameEvent.System(
                "You don't know Water Magic. The Cleric archetype starts with this skill, or train with a healer.",
                GameEvent.MessageLevel.WARNING
            ))
            return null
        }
        return waterMagic
    }

    private fun consumeManaOrWarn(
        game: EngineGameClient,
        resourceManager: ResourceManager,
        playerId: String,
        manaCost: Int
    ): Boolean {
        val manaPool = resourceManager.getResourcePool(playerId, "mana").getOrNull()
        if (manaPool == null || manaPool.current < manaCost) {
            val current = manaPool?.current ?: 0
            game.emitEvent(GameEvent.System(
                "Not enough mana. Need $manaCost, have $current. Rest in town or use mana potions.",
                GameEvent.MessageLevel.WARNING
            ))
            return false
        }
        resourceManager.consumeResource(playerId, "mana", manaCost)
        return true
    }

    private fun applyHealing(
        game: EngineGameClient,
        player: PlayerState,
        resourceManager: ResourceManager,
        manaCost: Int,
        healAmount: Int
    ) {
        val actualHeal = minOf(healAmount, player.maxHealth - player.health)
        val healed = player.heal(actualHeal)
        game.worldState = game.worldState.updatePlayer(healed)
        val newMana = resourceManager.getResourcePool(player.id, "mana").getOrNull()?.current ?: 0
        val output = buildString {
            appendLine("You channel Water Magic, calling forth restorative energies.")
            appendLine("HP restored: +$actualHeal (${healed.health}/${healed.maxHealth})")
            appendLine("Mana: -$manaCost ($newMana remaining)")
        }
        game.emitEvent(GameEvent.Narrative(appendHealingXp(game, player.id, output)))
    }

    private fun appendHealingXp(game: EngineGameClient, playerId: String, base: String): String {
        val xpOutput = StringBuilder(base)
        game.skillManager.attemptSkillProgress(
            entityId = playerId,
            skillName = "Water Magic",
            baseXp = 30L,
            success = true
        ).getOrNull()?.forEach { event ->
            when (event) {
                is SkillEvent.XpGained ->
                    xpOutput.appendLine("+${event.xpAmount} XP to Water Magic")
                is SkillEvent.LevelUp ->
                    xpOutput.appendLine("Water Magic leveled up! ${event.oldLevel} -> ${event.newLevel}")
                else -> {}
            }
        }
        return xpOutput.toString()
    }
}
