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
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.reasoning.town.SafeZoneValidator

/**
 * Attack pre-checks + equipment prep for [ClientCombatAttackHandlers] (MUD-034k).
 */
internal object ClientCombatAttackPrep {

    data class Prepared(
        val spaceId: String,
        val npc: Entity.NPC,
        val attackerEquipped: Map<EquipSlot, ItemInstance>,
        val defenderEquipped: Map<EquipSlot, ItemInstance>,
        val templates: Map<String, ItemTemplate>,
        val weaponName: String,
        val playerInventory: InventoryComponent?
    )

    fun prepare(game: EngineGameClient, target: String?): Prepared? {
        val spaceId = game.worldState.player.currentRoomId
        if (blockedSafeZone(game, spaceId, target)) return null
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return null
        }
        val npc = findNpc(game, spaceId, target) ?: run {
            game.emitEvent(
                GameEvent.System(
                    "You don't see anyone by that name to attack.",
                    GameEvent.MessageLevel.WARNING
                )
            )
            return null
        }
        return loadEquipment(game, spaceId, npc)
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

    private fun findNpc(game: EngineGameClient, spaceId: String, target: String): Entity.NPC? =
        game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

    private fun loadEquipment(
        game: EngineGameClient,
        spaceId: String,
        npc: Entity.NPC
    ): Prepared {
        val playerInventory = game.worldState.player.inventoryComponent
        val attackerEquipped = playerInventory?.equipped ?: emptyMap()
        val defenderEquipped = npc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?.equipped ?: emptyMap()
        val templates = loadTemplates(game, attackerEquipped, defenderEquipped)
        val weaponName = weaponName(attackerEquipped, templates)
        return Prepared(
            spaceId, npc, attackerEquipped, defenderEquipped, templates, weaponName, playerInventory
        )
    }

    private fun loadTemplates(
        game: EngineGameClient,
        attackerEquipped: Map<EquipSlot, ItemInstance>,
        defenderEquipped: Map<EquipSlot, ItemInstance>
    ): Map<String, ItemTemplate> {
        val ids = (attackerEquipped.values + defenderEquipped.values).map { it.templateId }.toSet()
        return ids.mapNotNull { templateId ->
            game.itemRepository.findTemplateById(templateId).getOrNull()
                ?.let { template -> template.id to template }
        }.toMap()
    }

    private fun weaponName(
        equipped: Map<EquipSlot, ItemInstance>,
        templates: Map<String, ItemTemplate>
    ): String {
        val weaponInstance = equipped[EquipSlot.HANDS_MAIN]
            ?: equipped[EquipSlot.HANDS_OFF]
            ?: equipped[EquipSlot.HANDS_BOTH]
        return if (weaponInstance != null) {
            templates[weaponInstance.templateId]?.name ?: "weapon"
        } else {
            "bare fists"
        }
    }
}
