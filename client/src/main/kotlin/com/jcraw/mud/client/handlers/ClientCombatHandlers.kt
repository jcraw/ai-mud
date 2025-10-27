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
        val room = game.worldState.getCurrentRoom() ?: return

        // Validate target
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return
        }

        // Find the target NPC
        val npc = room.entities.filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("You don't see anyone by that name to attack.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Calculate player damage: base + weapon + STR modifier
        val playerBaseDamage = Random.nextInt(5, 16)
        val weaponBonus = game.worldState.player.getWeaponDamageBonus()
        val strModifier = game.worldState.player.stats.strModifier()
        val playerDamage = (playerBaseDamage + weaponBonus + strModifier).coerceAtLeast(1)

        // Calculate NPC damage: base + STR modifier - armor defense
        val npcBaseDamage = Random.nextInt(3, 13)
        val npcStrModifier = npc.stats.strModifier()
        val armorDefense = game.worldState.player.getArmorDefenseBonus()
        val npcDamage = (npcBaseDamage + npcStrModifier - armorDefense).coerceAtLeast(1)

        // Apply damage to NPC
        val npcHealth = npc.health - playerDamage
        val npcDied = npcHealth <= 0

        // Generate narrative
        val weapon = game.worldState.player.equippedWeapon?.name ?: "bare fists"
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
            game.worldState = game.worldState.removeEntityFromRoom(room.id, npc.id) ?: game.worldState

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
            game.running = false
            game.emitEvent(GameEvent.StatusUpdate(
                hp = 0,
                maxHp = game.worldState.player.maxHealth
            ))
            return
        }

        // Update status
        game.emitEvent(GameEvent.StatusUpdate(
            hp = game.worldState.player.health,
            maxHp = game.worldState.player.maxHealth
        ))
    }
}
