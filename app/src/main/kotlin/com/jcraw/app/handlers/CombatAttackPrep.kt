@file:Suppress("ReturnCount")

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.reasoning.combat.CombatHandlerPures
import com.jcraw.mud.reasoning.town.SafeZoneValidator

/**
 * Attack pre-checks + equipment prep for [CombatAttackHandlers] (MUD-039).
 */
internal object CombatAttackPrep {

    fun prepare(game: MudGame, target: String?): CombatHandlerPures.Prepared? {
        val spaceId = game.worldState.player.currentRoomId
        if (blockedSafeZone(game, spaceId, target)) return null
        if (target.isNullOrBlank()) {
            println("Attack whom?")
            return null
        }
        val npc = CombatHandlerPures.matchNpc(game.worldState.getEntitiesInSpace(spaceId), target) ?: run {
            println("You don't see anyone by that name to attack.")
            return null
        }
        return CombatHandlerPures.loadGear(
            spaceId,
            npc,
            game.worldState.player.inventoryComponent
        ) { id -> game.itemRepository.findTemplateById(id).getOrNull() }
    }

    private fun blockedSafeZone(game: MudGame, spaceId: String, target: String?): Boolean {
        val currentSpace = game.spacePropertiesRepository.findByChunkId(spaceId).getOrNull()
        if (currentSpace != null && SafeZoneValidator.isSafeZone(currentSpace)) {
            println(SafeZoneValidator.getCombatBlockedMessage(target ?: "unknown"))
            return true
        }
        return false
    }
}
