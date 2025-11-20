package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.combat.DeathHandler
import com.jcraw.mud.reasoning.town.SafeZoneValidator
import kotlinx.coroutines.runBlocking

/**
 * Handles combat system in the GUI client.
 * Uses V2 combat system (AttackResolver, skills, equipment) - identical to console.
 */
object ClientCombatHandlers {

    fun handleAttack(game: EngineGameClient, target: String?) {
        val spaceId = game.worldState.player.currentRoomId

        // Check if current space is a safe zone (blocks combat)
        val currentSpace = game.spacePropertiesRepository.findByChunkId(spaceId).getOrNull()

        if (currentSpace != null && SafeZoneValidator.isSafeZone(currentSpace)) {
            game.emitEvent(GameEvent.System(
                SafeZoneValidator.getCombatBlockedMessage(target ?: "unknown"),
                GameEvent.MessageLevel.WARNING
            ))
            return
        }

        // Validate target
        if (target.isNullOrBlank()) {
            game.emitEvent(GameEvent.System("Attack whom?", GameEvent.MessageLevel.WARNING))
            return
        }

        // Find the target NPC
        val npc = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            game.emitEvent(GameEvent.System("You don't see anyone by that name to attack.", GameEvent.MessageLevel.WARNING))
            return
        }

        // Get V2 equipped items for damage calculation
        val player = game.worldState.player
        val playerInventory = player.inventoryComponent
        val attackerEquipped = playerInventory?.equipped ?: emptyMap()

        // Get NPC equipped items (if any)
        val npcInventory = npc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
        val defenderEquipped = npcInventory?.equipped ?: emptyMap()

        // Build item templates map for both attacker and defender
        val allTemplateIds = (attackerEquipped.values + defenderEquipped.values).map { it.templateId }.toSet()
        val templates = allTemplateIds.mapNotNull { templateId ->
            game.itemRepository.findTemplateById(templateId).getOrNull()?.let { template -> template.id to template }
        }.toMap()

        // Get weapon name for skill classification
        val weaponInstance = attackerEquipped[EquipSlot.HANDS_MAIN]
            ?: attackerEquipped[EquipSlot.HANDS_OFF]
            ?: attackerEquipped[EquipSlot.HANDS_BOTH]
        val weaponName = if (weaponInstance != null) {
            templates[weaponInstance.templateId]?.name ?: "weapon"
        } else {
            "bare fists"
        }

        // Resolve attack using AttackResolver (V2 Combat System)
        val attackResult = if (game.attackResolver != null && game.skillManager != null) {
            try {
                runBlocking {
                    game.attackResolver.resolveAttack(
                        attackerId = game.worldState.player.id,
                        defenderId = npc.id,
                        action = "attack ${npc.name} with $weaponName",
                        worldState = game.worldState,
                        skillManager = game.skillManager,
                        attackerEquipped = attackerEquipped,
                        defenderEquipped = defenderEquipped,
                        templates = templates
                    )
                }
            } catch (e: Exception) {
                // Fallback to simple attack if resolver fails
                null
            }
        } else {
            null
        }

        when (attackResult) {
            is AttackResult.Hit -> {
                // Apply damage from AttackResolver - damage already applied to component
                val updatedNpc = npc.withComponent(attackResult.updatedDefenderCombat) as Entity.NPC

                // Update space with damaged NPC
                game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc) ?: game.worldState

                // Generate and display attack narrative
                // Get weapon name from V2 inventory if available, fallback to legacy
                val weapon = if (playerInventory != null) {
                    val weaponInstance = playerInventory.equipped[EquipSlot.HANDS_MAIN]
                        ?: playerInventory.equipped[EquipSlot.HANDS_OFF]
                        ?: playerInventory.equipped[EquipSlot.HANDS_BOTH]
                    if (weaponInstance != null) {
                        templates[weaponInstance.templateId]?.name ?: "bare fists"
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
                            damage = attackResult.damage,
                            maxHp = npc.maxHealth,
                            isHit = true,
                            isCritical = false,
                            isDeath = attackResult.wasKilled,
                            isSpell = false,
                            targetName = npc.name
                        )
                    }
                } else {
                    "You hit ${npc.name} for ${attackResult.damage} damage!"
                }
                game.emitEvent(GameEvent.Combat(narrative))

                // Grant skill XP for skills used in the attack
                attemptSkillUnlocks(game, attackResult.attackerSkillsUsed, success = true)

                // Check if NPC died
                if (attackResult.wasKilled) {
                    game.emitEvent(GameEvent.Combat("\nVictory! ${npc.name} has been defeated!"))

                    // Handle corpse + loot creation
                    val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
                    game.worldState = when (deathResult) {
                        is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
                        else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
                    }

                    // Track NPC kill for quests
                    game.trackQuests(QuestAction.KilledNPC(npc.id))
                    return
                }

                // Trigger counter-attack behavior (V2 Combat)
                // This makes the NPC hostile and adds them to the turn queue
                if (game.turnQueue != null) {
                    game.worldState = CombatBehavior.triggerCounterAttack(
                        npcId = npc.id,
                        spaceId = spaceId,
                        worldState = game.worldState,
                        turnQueue = game.turnQueue
                    )
                }
            }
            is AttackResult.Miss -> {
                // Generate and display miss narrative
                val narrative = if (attackResult.wasDodged) {
                    "${npc.name} dodges your attack!"
                } else {
                    "You miss ${npc.name}!"
                }
                game.emitEvent(GameEvent.Combat(narrative))

                // Grant skill XP for skills used in the attack (reduced XP on miss)
                attemptSkillUnlocks(game, attackResult.attackerSkillsUsed, success = false)

                // Trigger counter-attack even on miss
                if (game.turnQueue != null) {
                    game.worldState = CombatBehavior.triggerCounterAttack(
                        npcId = npc.id,
                        spaceId = spaceId,
                        worldState = game.worldState,
                        turnQueue = game.turnQueue
                    )
                }
            }
            is AttackResult.Failure -> {
                game.emitEvent(GameEvent.System("Attack failed: ${attackResult.reason}", GameEvent.MessageLevel.WARNING))
            }
            null -> {
                // Fallback: AttackResolver not available (no LLM client)
                game.emitEvent(GameEvent.System("Combat system not available (requires API key)", GameEvent.MessageLevel.WARNING))
            }
        }
    }

    /**
     * Attempt to unlock skills used in combat
     * Also grants XP to already-unlocked skills
     */
    private fun attemptSkillUnlocks(game: EngineGameClient, skillsUsed: List<String>, success: Boolean) {
        if (game.skillManager == null) return

        val playerId = game.worldState.player.id
        val playerSkills = game.skillManager.getSkillComponent(playerId)

        skillsUsed.forEach { skillName ->
            val skill = playerSkills.getSkill(skillName)

            if (skill == null || !skill.unlocked) {
                // Skill not unlocked - attempt to unlock via Attempt method
                val unlockResult = game.skillManager.unlockSkill(
                    entityId = playerId,
                    skillName = skillName,
                    method = com.jcraw.mud.reasoning.skill.UnlockMethod.Attempt
                )

                unlockResult.onSuccess { unlockEvent ->
                    if (unlockEvent != null) {
                        game.emitEvent(GameEvent.System("🎉 Through combat, you've discovered $skillName!", GameEvent.MessageLevel.INFO))
                    } else {
                        // Unlock failed - grant 5 XP so player can eventually progress via use-based unlocking
                        val xpResult = game.skillManager.grantXp(
                            entityId = playerId,
                            skillName = skillName,
                            baseXp = 5L,
                            success = true  // Always true - we're already adjusting baseXp
                        )
                        // Display unlocks and level-ups from use-based progression
                        xpResult.onSuccess { events ->
                            displaySkillEvents(game, events, skillName)
                        }
                    }
                }
            } else {
                // Skill already unlocked - grant XP (reduced on miss)
                val xpAmount = if (success) 10L else 2L
                val xpResult = game.skillManager.grantXp(
                    entityId = playerId,
                    skillName = skillName,
                    baseXp = xpAmount,
                    success = true  // Always true - we're already adjusting baseXp
                )
                // Display XP gains and level-ups
                xpResult.onSuccess { events ->
                    displaySkillEvents(game, events, skillName)
                }
            }
        }
    }

    /**
     * Display skill events (unlocks, level-ups) from combat
     * XP gains are silent to avoid spam - check 'skills' command for progress
     */
    private fun displaySkillEvents(game: EngineGameClient, events: List<SkillEvent>, skillName: String) {
        events.forEach { event ->
            when (event) {
                is SkillEvent.SkillUnlocked -> {
                    game.emitEvent(GameEvent.System(
                        "🎉 Unlocked $skillName through use-based progression!",
                        GameEvent.MessageLevel.INFO
                    ))
                }
                is SkillEvent.LevelUp -> {
                    game.emitEvent(GameEvent.System(
                        "🎉 $skillName leveled up! ${event.oldLevel} → ${event.newLevel}",
                        GameEvent.MessageLevel.INFO
                    ))
                    if (event.isAtPerkMilestone) {
                        game.emitEvent(GameEvent.System(
                            "⚡ Milestone reached! Use 'choose perk for $skillName' to select a perk.",
                            GameEvent.MessageLevel.INFO
                        ))
                    }
                }
                else -> {} // Silently accumulate XP
            }
        }
    }
}
