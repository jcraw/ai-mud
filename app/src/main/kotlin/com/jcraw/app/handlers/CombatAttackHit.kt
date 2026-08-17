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
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.combat.CombatHitApply
import com.jcraw.mud.reasoning.combat.DeathHandler
import kotlinx.coroutines.runBlocking

/**
 * Hit branch for console combat attack (MUD-034k pure-move).
 */
internal object CombatAttackHit {

    fun apply(game: MudGame, prep: CombatAttackPrep.Prepared, attackResult: AttackResult.Hit) {
        val applied = CombatHitApply.apply(game.worldState, prep.spaceId, prep.npc, attackResult)
        if (applied is CombatHitApply.Result.Success) {
            game.worldState = applied.world
        }
        println("\n${narrateHit(game, prep.playerInventory, prep.templates, prep.npc, attackResult)}")
        println(
            CombatSkillProgressHandlers.getHealthDescriptor(
                attackResult.updatedDefenderCombat.currentHp,
                attackResult.updatedDefenderCombat.maxHp,
                prep.npc.name
            )
        )
        CombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        if (attackResult.wasKilled) {
            handleDeath(game, prep.spaceId, prep.npc)
            return
        }
        triggerCounterAttack(game, prep.npc.id, prep.spaceId)
    }

    private fun narrateHit(
        game: MudGame,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>,
        npc: Entity.NPC,
        attackResult: AttackResult.Hit
    ): String {
        val weapon = resolveWeapon(game, playerInventory, templates)
        val narrator = game.combatNarrator ?: return "You hit ${npc.name} for ${attackResult.damage} damage!"
        return runBlocking {
            narrator.narrateAction(
                weapon = weapon,
                damage = attackResult.damage,
                maxHp = npc.maxHealth,
                isHit = true,
                isCritical = false,
                isDeath = attackResult.wasKilled,
                isSpell = false,
                targetName = npc.name
            )
        }
    }

    private fun resolveWeapon(
        game: MudGame,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>
    ): String {
        if (playerInventory == null) {
            return game.worldState.player.equippedWeapon?.name ?: "bare fists"
        }
        val weaponInstance = playerInventory.equipped[EquipSlot.HANDS_MAIN]
            ?: playerInventory.equipped[EquipSlot.HANDS_OFF]
        return if (weaponInstance != null) {
            templates[weaponInstance.templateId]?.name ?: "bare fists"
        } else {
            "bare fists"
        }
    }

    private fun handleDeath(game: MudGame, spaceId: String, npc: Entity.NPC) {
        println("\nVictory! ${npc.name} has been defeated!")
        val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
        game.worldState = when (deathResult) {
            is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
            else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
        }
        game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)
        game.trackQuests(QuestAction.KilledNPC(npc.id))
    }

    private fun triggerCounterAttack(game: MudGame, npcId: String, spaceId: String) {
        if (game.turnQueue != null) {
            game.worldState = CombatBehavior.triggerCounterAttack(
                npcId = npcId,
                spaceId = spaceId,
                worldState = game.worldState,
                turnQueue = game.turnQueue
            )
        }
    }
}
