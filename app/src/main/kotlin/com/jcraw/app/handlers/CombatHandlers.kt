package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.*
import com.jcraw.mud.reasoning.combat.AttackResolver
import com.jcraw.mud.reasoning.combat.AttackResult
import com.jcraw.mud.reasoning.combat.CombatBehavior
import com.jcraw.mud.reasoning.combat.DeathHandler
import com.jcraw.mud.reasoning.QuestAction
import com.jcraw.mud.reasoning.town.SafeZoneValidator
import kotlinx.coroutines.runBlocking

/**
 * Combat handlers - attack, damage resolution, victory/defeat
 *
 * Phase 4: Refactored to use emergent combat system
 * - No combat modes, combat emerges from hostile dispositions
 * - Uses AttackResolver for attack resolution
 * - Uses CombatBehavior for counter-attack triggers
 */
object CombatHandlers {

    /**
     * Handle player attack action
     *
     * @param game MudGame instance with world state and systems
     * @param target Target identifier (NPC name or ID)
     */
    fun handleAttack(game: MudGame, target: String?) {
        // Try V3 first
        val space = game.worldState.getCurrentSpace()
        val spaceId = game.worldState.player.currentRoomId

        // Check if current space is a safe zone (blocks combat)
        val currentSpace = game.spacePropertiesRepository.findByChunkId(spaceId).getOrNull()

        if (currentSpace != null && SafeZoneValidator.isSafeZone(currentSpace)) {
            println(SafeZoneValidator.getCombatBlockedMessage(target ?: "unknown"))
            return
        }

        // Validate target
        if (target.isNullOrBlank()) {
            println("Attack whom?")
            return
        }

        // Find the target NPC (V3)
        val npc = game.worldState.getEntitiesInSpace(spaceId)
            .filterIsInstance<Entity.NPC>()
            .find { entity ->
                entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
            }

        if (npc == null) {
            println("You don't see anyone by that name to attack.")
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

        // Resolve attack using AttackResolver (Phase 3)
        println("[ATTACK HANDLER DEBUG] Initiating attack: ${game.worldState.player.name} -> ${npc.name}")
        println("[ATTACK HANDLER DEBUG] AttackResolver available: ${game.attackResolver != null}")
        println("[ATTACK HANDLER DEBUG] SkillManager available: ${game.skillManager != null}")

        val attackResult = if (game.attackResolver != null && game.skillManager != null) {
            try {
                runBlocking {
                    game.attackResolver.resolveAttack(
                        attackerId = game.worldState.player.id,
                        defenderId = npc.id,
                        action = "attack ${npc.name}",
                        worldState = game.worldState,
                        skillManager = game.skillManager,
                        attackerEquipped = attackerEquipped,
                        defenderEquipped = defenderEquipped,
                        templates = templates
                    )
                }
            } catch (e: Exception) {
                // Fallback to simple attack if resolver fails
                println("Debug: AttackResolver failed: ${e.message}, using fallback")
                e.printStackTrace()
                null
            }
        } else {
            null
        }

        when (attackResult) {
            is AttackResult.Hit -> {
                // Apply damage from AttackResolver - damage already applied to component
                val updatedNpc = npc.withComponent(attackResult.updatedDefenderCombat) as Entity.NPC

                // Update space with damaged NPC (V3)
                game.worldState = game.worldState.replaceEntityInSpace(spaceId, npc.id, updatedNpc) ?: game.worldState

                // Generate and display attack narrative
                // Get weapon name from V2 inventory if available, fallback to legacy
                val weapon = if (playerInventory != null) {
                    val weaponInstance = playerInventory.equipped[EquipSlot.HANDS_MAIN]
                        ?: playerInventory.equipped[EquipSlot.HANDS_OFF]
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
                println("\n$narrative")

                // Attempt to unlock skills used in the attack
                attemptSkillUnlocks(game, attackResult.skillsUsed, success = true)

                // Check if NPC died
                if (attackResult.wasKilled) {
                    println("\nVictory! ${npc.name} has been defeated!")

                    // V3: Handle corpse + loot creation
                    val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
                    game.worldState = when (deathResult) {
                        is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
                        else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
                    }

                    // Mark entity death for respawn system
                    game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)

                    // Track NPC kill for quests
                    game.trackQuests(QuestAction.KilledNPC(npc.id))
                    return
                }

                // Trigger counter-attack behavior (Phase 4)
                // This makes the NPC hostile and adds them to the turn queue
                if (game.turnQueue != null) {
                    game.worldState = CombatBehavior.triggerCounterAttack(
                        npcId = npc.id,
                        spaceId = spaceId,  // Use spaceId which works for both V2 and V3
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
                println("\n$narrative")

                // Attempt to unlock skills even on miss (learn from failure)
                attemptSkillUnlocks(game, attackResult.skillsUsed, success = false)

                // Trigger counter-attack even on miss
                if (game.turnQueue != null) {
                    game.worldState = CombatBehavior.triggerCounterAttack(
                        npcId = npc.id,
                        spaceId = spaceId,  // Use spaceId which works for both V2 and V3
                        worldState = game.worldState,
                        turnQueue = game.turnQueue
                    )
                }
            }
            is AttackResult.Failure -> {
                println("Attack failed: ${attackResult.reason}")
            }
            null -> {
                // Fallback: Use old combat system for backward compatibility
                handleLegacyAttack(game, npc)
            }
        }
    }

    /**
     * Legacy attack handler for backward compatibility
     * Uses the old CombatResolver system
     *
     * TODO: Remove this once all combat is migrated to V2
     */
    private fun handleLegacyAttack(game: MudGame, npc: Entity.NPC) {
        // Start combat using old system
        val result = game.combatResolver.initiateCombat(game.worldState, npc.id)
        if (result == null) {
            println("You cannot initiate combat with that target.")
            return
        }

        // Generate narrative for combat start
        val narrative = if (game.combatNarrator != null) {
            runBlocking {
                game.combatNarrator.narrateCombatStart(game.worldState, npc)
            }
        } else {
            result.narrative
        }

        println("\n$narrative")

        // Execute the attack
        val attackResult = game.combatResolver.executePlayerAttack(game.worldState)

        // Display combat narrative
        val combatNarrative = if (game.combatNarrator != null && !attackResult.playerDied && !attackResult.npcDied) {
            val spaceId = game.worldState.player.currentRoomId
            val combatNpc = game.worldState.getEntitiesInSpace(spaceId)
                .filterIsInstance<Entity.NPC>()
                .find { it.id == npc.id }

            if (combatNpc != null) {
                runBlocking {
                    game.combatNarrator.narrateCombatRound(
                        game.worldState, combatNpc, attackResult.playerDamage, attackResult.npcDamage,
                        attackResult.npcDied, attackResult.playerDied
                    )
                }
            } else {
                attackResult.narrative
            }
        } else {
            attackResult.narrative
        }

        println("\n$combatNarrative")

        // Handle combat results
        when {
            attackResult.npcDied -> {
                println("\nVictory! The enemy has been defeated!")

                val spaceId = game.worldState.player.currentRoomId
                val deathResult = game.deathHandler.handleDeath(npc.id, game.worldState)
                game.worldState = when (deathResult) {
                    is DeathHandler.DeathResult.NPCDeath -> deathResult.updatedWorld
                    else -> game.worldState.removeEntityFromSpace(spaceId, npc.id) ?: game.worldState
                }

                // Mark entity death for respawn system
                game.respawnChecker?.markDeath(npc.id, game.worldState.gameTime)

                game.trackQuests(QuestAction.KilledNPC(npc.id))
            }
            attackResult.playerDied -> {
                // Use new permadeath system
                game.handlePlayerDeath()
            }
        }
    }

    /**
     * Attempt to unlock skills used in combat
     * Also grants XP to already-unlocked skills
     */
    private fun attemptSkillUnlocks(game: MudGame, skillsUsed: List<String>, success: Boolean) {
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
                        println("🎉 Through combat, you've discovered $skillName!")
                    } else {
                        // Unlock failed - grant 1 XP so player can eventually progress
                        game.skillManager.grantXp(
                            entityId = playerId,
                            skillName = skillName,
                            baseXp = 1L,
                            success = success
                        )
                    }
                }
            } else {
                // Skill already unlocked - grant XP
                val xpAmount = if (success) 10L else 2L  // Less XP on miss
                game.skillManager.grantXp(
                    entityId = playerId,
                    skillName = skillName,
                    baseXp = xpAmount,
                    success = success
                )
            }
        }
    }
}
