@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.reasoning.skill.ResourceManager

/**
 * Healing-spell fragment for skill-use cluster.
 * Pure extract from [SkillQuestHandlers].
 */
object SkillQuestHealingHandlers {

    fun handleHealingSpell(game: MudGame) {
        val player = game.worldState.player
        val waterMagic = resolveWaterMagic(game, player) ?: return
        val manaCost = 20
        val healAmount = 15 + (waterMagic.getEffectiveLevel() * 3)
        val resourceManager = ResourceManager(game.skillManager.getSkillComponentRepository())
        if (!consumeManaOrWarn(resourceManager, player.id, manaCost)) return
        if (player.health >= player.maxHealth) {
            println("\nYou are already at full health.")
            return
        }
        applyHealing(game, player, resourceManager, manaCost, healAmount)
    }

    private fun resolveWaterMagic(game: MudGame, player: PlayerState): SkillState? {
        val skillComponent = game.skillManager.getSkillComponent(player.id)
        val waterMagic = skillComponent.getSkill("Water Magic")
        if (waterMagic == null || !waterMagic.unlocked) {
            println("\nYou don't know Water Magic.")
            println("The Cleric archetype starts with this skill, or train with a healer.")
            return null
        }
        return waterMagic
    }

    private fun consumeManaOrWarn(
        resourceManager: ResourceManager,
        playerId: String,
        manaCost: Int
    ): Boolean {
        val manaPool = resourceManager.getResourcePool(playerId, "mana").getOrNull()
        if (manaPool == null || manaPool.current < manaCost) {
            val current = manaPool?.current ?: 0
            println("\nNot enough mana. Need $manaCost, have $current.")
            println("Rest in town or use mana potions to restore.")
            return false
        }
        resourceManager.consumeResource(playerId, "mana", manaCost)
        return true
    }

    private fun applyHealing(
        game: MudGame,
        player: PlayerState,
        resourceManager: ResourceManager,
        manaCost: Int,
        healAmount: Int
    ) {
        val actualHeal = minOf(healAmount, player.maxHealth - player.health)
        val healed = player.heal(actualHeal)
        game.worldState = game.worldState.updatePlayer(healed)
        val newMana = resourceManager.getResourcePool(player.id, "mana").getOrNull()?.current ?: 0
        println("\nYou channel Water Magic, calling forth restorative energies.")
        println("HP restored: +$actualHeal (${healed.health}/${healed.maxHealth})")
        println("Mana: -$manaCost ($newMana remaining)")
        grantHealingXp(game, player.id)
    }

    private fun grantHealingXp(game: MudGame, playerId: String) {
        game.skillManager.attemptSkillProgress(
            entityId = playerId,
            skillName = "Water Magic",
            baseXp = 30L,
            success = true
        ).getOrNull()?.forEach { event ->
            when (event) {
                is SkillEvent.XpGained ->
                    println("+${event.xpAmount} XP to Water Magic")
                is SkillEvent.LevelUp ->
                    println("Water Magic leveled up! ${event.oldLevel} -> ${event.newLevel}")
                else -> {}
            }
        }
    }
}
