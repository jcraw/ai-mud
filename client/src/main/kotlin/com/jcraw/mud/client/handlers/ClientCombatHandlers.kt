package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * Handles combat system in the GUI client.
 * Uses simple combat mechanics for GUI client.
 */
object ClientCombatHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) {
        // Validate target
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return
        }

        // Get entities in current space
        val entities = game.worldState.getEntitiesInSpace(game.worldState.player.currentRoomId)

        // Find the target NPC
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("You don't see anyone by that name to attack.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Get V2 equipped items for V2-aware bonuses (if available)
        val player = game.worldState.player
        val playerInventory = player.inventoryComponent

        // Calculate player damage: base + weapon + STR modifier
        val playerBaseDamage = Random.nextInt(5, 16)
        // TODO: Update to use V2 inventory for weapon damage calculation
        val weaponBonus = game.worldState.player.getWeaponDamageBonus()
        val strModifier = game.worldState.player.stats.strModifier()
        val playerDamage = (playerBaseDamage + weaponBonus + strModifier).coerceAtLeast(1)

        // Calculate NPC damage: base + STR modifier - armor defense
        val npcBaseDamage = Random.nextInt(3, 13)
        val npcStrModifier = npc.stats.strModifier()
        // TODO: Update to use V2 inventory for armor defense calculation
        val armorDefense = game.worldState.player.getArmorDefenseBonus()
        val npcDamage = (npcBaseDamage + npcStrModifier - armorDefense).coerceAtLeast(1)

        // Apply damage to NPC
        val npcHealth = npc.health - playerDamage
        val npcDied = npcHealth <= 0

        // Generate narrative - Get weapon name from V2 inventory if available
        val weapon = if (playerInventory != null) {
            // Build templates map for weapon lookup
            val weaponInstance = playerInventory.equipped[EquipSlot.HANDS_MAIN]
                ?: playerInventory.equipped[EquipSlot.HANDS_OFF]
            if (weaponInstance != null) {
                val template = game.itemRepository.findTemplateById(weaponInstance.templateId).getOrNull()
                template?.name ?: "bare fists"
            } else {
                "bare fists"
            }
        } else {
            game.worldState.player.equippedWeapon?.name ?: "bare fists"
        }
        val narrative = if (game.combatNarrator != null) {
            runBlocking {
                game.combatNarrator.narrateAction(
                    weapon = weapon,
                    damage = playerDamage,
                    maxHp = npc.maxHealth,
                    isHit = true,
                    isCritical = false,
                    isDeath = npcDied,
                    isSpell = false,
                    targetName = npc.name
                )
            }
        } else {
            "You attack ${npc.name} with $weapon for $playerDamage damage!"
        }

        game.emitEvent(GameEvent.Combat(narrative))

        // Check if NPC died
        if (npcDied) {
            game.emitEvent(GameEvent.Combat("\nVictory! ${npc.name} has been defeated!"))
            game.worldState = game.worldState.removeEntityFromSpace(game.worldState.player.currentRoomId, npc.id) ?: game.worldState

            // Track NPC kill for quests
            game.trackQuests(QuestAction.KilledNPC(npc.id))

            // Update player health if they took damage from the last exchange
            if (npcDamage > 0) {
                val updatedPlayer = game.worldState.player.takeDamage(npcDamage)
                game.worldState = game.worldState.updatePlayer(updatedPlayer)
            }

            // Update status
            game.emitEvent(GameEvent.StatusUpdate(
                hp = game.worldState.player.health,
                maxHp = game.worldState.player.maxHealth
            ))
            return
        }

        // NPC counter-attacks
        if (npcDamage > 0) {
            val counterNarrative = "${npc.name} strikes back for $npcDamage damage!"
            game.emitEvent(GameEvent.Combat("\n$counterNarrative"))
        }

        // Apply damage to player
        val updatedPlayer = game.worldState.player.takeDamage(npcDamage)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)

        // Check if player died
        if (updatedPlayer.isDead()) {
            game.emitEvent(GameEvent.Combat("\nYou have been defeated! Game over."))
            game.emitEvent(GameEvent.StatusUpdate(
                hp = 0,
                maxHp = game.worldState.player.maxHealth
            ))
            game.handlePlayerDeath()
            return
        }

        // Update status
        game.emitEvent(GameEvent.StatusUpdate(
            hp = game.worldState.player.health,
            maxHp = game.worldState.player.maxHealth
        ))
    }
}
