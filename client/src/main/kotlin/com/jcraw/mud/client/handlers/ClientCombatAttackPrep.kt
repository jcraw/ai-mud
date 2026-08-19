@file:Suppress("ReturnCount")

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.town.SafeZoneValidator

/**
 * Attack pre-checks + equipment prep for [ClientCombatAttackHandlers] (MUD-039).
 */
internal object ClientCombatAttackPrep {

    fun prepare(game: EngineGameClient, target: String?): CombatHandlerPures.Prepared? {
        val spaceId = game.worldState.player.currentRoomId
        if (blockedSafeZone(game, spaceId, target)) return null
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return null
        }
        val npc = CombatHandlerPures.matchNpc(game.worldState.getEntitiesInSpace(spaceId), target) ?: run {
            game.emitEvent(
                GameEvent.System(
                    "You don't see anyone by that name to attack.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return null
        }
        return CombatHandlerPures.loadGear(
            spaceId,
            npc,
            game.worldState.player.inventoryComponent
        ) { id -> game.itemRepository.findTemplateById(id).getOrNull() }
    }

    private fun blockedSafeZone(game: EngineGameClient, spaceId: String, target: String?): Boolean {
        val currentSpace = game.spacePropertiesRepository.findByChunkId(spaceId).getOrNull()
        if (currentSpace != null && SafeZoneValidator.isSafeZone(currentSpace)) {
            game.emitEvent(
                GameEvent.System(
                    SafeZoneValidator.getCombatBlockedMessage(target ?: "unknown"),
                    GameEvent.MessageLevel.WARNING
                )
            )
            return true
        }
        return false
    }
}
