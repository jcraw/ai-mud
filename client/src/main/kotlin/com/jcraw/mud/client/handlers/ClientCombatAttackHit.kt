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

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.combat.CombatHitApply
import com.jcraw.mud.reasoning.combat.DeathHandler
import kotlinx.coroutines.runBlocking

/**
 * Hit branch for client combat attack (MUD-034k pure-move).
 */
internal object ClientCombatAttackHit {

    fun apply(
        game: EngineGameClient,
        prep: ClientCombatAttackPrep.Prepared,
        attackResult: AttackResult.Hit
    ) {
        applyDamage(game, prep, attackResult)
        emitNarration(game, prep, attackResult)
        ClientCombatSkillProgressHandlers.processSkillProgression(game, attackResult)
        if (attackResult.wasKilled) {
            handleDeath(game, prep.spaceId, prep.npc)
            return
        }
        triggerCounterAttack(game, prep.npc.id, prep.spaceId)
    }

    private fun applyDamage(
        game: EngineGameClient,
        prep: ClientCombatAttackPrep.Prepared,
        attackResult: AttackResult.Hit
    ) {
        val applied = CombatHitApply.apply(game.worldState, prep.spaceId, prep.npc, attackResult)
        if (applied is CombatHitApply.Result.Success) {
            game.worldState = applied.world
        }
    }

    private fun emitNarration(
        game: EngineGameClient,
        prep: ClientCombatAttackPrep.Prepared,
        attackResult: AttackResult.Hit
    ) {
        game.emitEvent(
            GameEvent.Combat(
                narrateHit(game, prep.playerInventory, prep.templates, prep.npc, attackResult)
            )
        )
        game.emitEvent(
            GameEvent.Combat(
                ClientCombatSkillProgressHandlers.getHealthDescriptor(
                    attackResult.updatedDefenderCombat.currentHp,
                    attackResult.updatedDefenderCombat.maxHp,
                    prep.npc.name
                )
            )
        )
    }

    private fun narrateHit(
        game: EngineGameClient,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>,
        npc: Entity.NPC,
        attackResult: AttackResult.Hit
    ): String {
        val weapon = resolveWeapon(game, playerInventory, templates)
        val narrator = game.combatNarrator
            ?: return "You hit ${npc.name} for ${attackResult.damage} damage!"
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
        game: EngineGameClient,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>
    ): String {
        if (playerInventory == null) {
            return game.worldState.player.equippedWeapon?.name ?: "bare fists"
        }
        val weaponInstance = playerInventory.equipped[EquipSlot.HANDS_MAIN]
            ?: playerInventory.equipped[EquipSlot.HANDS_OFF]
            ?: playerInventory.equipped[EquipSlot.HANDS_BOTH]
        return if (weaponInstance != null) {
            templates[weaponInstance.templateId]?.name ?: "bare fists"
        } else {
            "bare fists"
        }
    }

    private fun handleDeath(game: EngineGameClient, spaceId: String, npc: Entity.NPC) {
        game.emitEvent(GameEvent.Combat("\nVictory! ${npc.name} has been defeated!"))
        val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
        game.worldState = when (deathResult) {
            is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
            else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
        }
        game.trackQuests(QuestAction.KilledNPC(npc.id))
    }

    private fun triggerCounterAttack(game: EngineGameClient, npcId: String, spaceId: String) {
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
