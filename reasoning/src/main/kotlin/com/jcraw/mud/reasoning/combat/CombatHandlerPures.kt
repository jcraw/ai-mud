@file:Suppress("TooManyFunctions", "LongParameterList", "MagicNumber", "LongMethod")

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.reasoning.CombatNarrator
import com.jcraw.mud.reasoning.skill.SkillManager
import kotlinx.coroutines.runBlocking

/**
 * Shared combat handler pures: NPC match, gear, weapon, flags, health, miss, flee XP (MUD-039).
 * IO (println vs emitEvent) stays in app/client wrappers.
 */
object CombatHandlerPures {

    data class Prepared(
        val spaceId: String,
        val npc: Entity.NPC,
        val attackerEquipped: Map<EquipSlot, ItemInstance>,
        val defenderEquipped: Map<EquipSlot, ItemInstance>,
        val templates: Map<String, ItemTemplate>,
        val weaponName: String,
        val playerInventory: InventoryComponent?
    )

    fun matchNpc(entities: List<Entity>, target: String): Entity.NPC? =
        entities.filterIsInstance<Entity.NPC>().find { entity ->
            entity.name.lowercase().contains(target.lowercase()) ||
                entity.id.lowercase().contains(target.lowercase())
        }

    fun loadGear(
        spaceId: String,
        npc: Entity.NPC,
        playerInventory: InventoryComponent?,
        lookup: (String) -> ItemTemplate?
    ): Prepared {
        val attackerEquipped = playerInventory?.equipped ?: emptyMap()
        val defenderEquipped = npc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?.equipped ?: emptyMap()
        val ids = (attackerEquipped.values + defenderEquipped.values).map { it.templateId }.toSet()
        val templates = ids.mapNotNull { id -> lookup(id)?.let { it.id to it } }.toMap()
        return Prepared(
            spaceId,
            npc,
            attackerEquipped,
            defenderEquipped,
            templates,
            weaponName(attackerEquipped, templates),
            playerInventory
        )
    }

    fun weaponName(
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

    /**
     * Hit narration weapon. Includes [EquipSlot.HANDS_BOTH] (prep already did; console hit drifted).
     */
    fun resolveWeapon(
        equippedWeaponName: String?,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>
    ): String {
        if (playerInventory == null) {
            return equippedWeaponName ?: "bare fists"
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

    fun successFlags(attackResult: AttackResult): Pair<Boolean, Boolean> {
        val attackerSuccess = when (attackResult) {
            is AttackResult.Hit -> true
            is AttackResult.Miss -> false
            else -> false
        }
        val defenderSuccess = when (attackResult) {
            is AttackResult.Hit -> false
            is AttackResult.Miss -> true
            else -> false
        }
        return attackerSuccess to defenderSuccess
    }

    fun healthDescriptor(currentHp: Int, maxHp: Int, entityName: String): String {
        val healthPercent = (currentHp.toDouble() / maxHp.toDouble() * 100).toInt()
        return "The $entityName ${healthBand(healthPercent)}."
    }

    fun missNarrative(npcName: String, wasDodged: Boolean): String =
        if (wasDodged) "$npcName dodges your attack!" else "You miss $npcName!"

    fun narrateHit(
        narrator: CombatNarrator?,
        equippedWeaponName: String?,
        playerInventory: InventoryComponent?,
        templates: Map<String, ItemTemplate>,
        npc: Entity.NPC,
        attackResult: AttackResult.Hit
    ): String {
        val weapon = resolveWeapon(equippedWeaponName, playerInventory, templates)
        if (narrator == null) {
            return "You hit ${npc.name} for ${attackResult.damage} damage!"
        }
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

    fun resolvePlayerAttack(
        resolver: AttackResolver,
        playerId: String,
        prep: Prepared,
        worldState: com.jcraw.mud.core.WorldState,
        skillManager: SkillManager
    ): AttackResult = runBlocking {
        resolver.resolveAttack(
            attackerId = playerId,
            defenderId = prep.npc.id,
            action = "attack ${prep.npc.name} with ${prep.weaponName}",
            worldState = worldState,
            skillManager = skillManager,
            attackerEquipped = prep.attackerEquipped,
            defenderEquipped = prep.defenderEquipped,
            templates = prep.templates
        )
    }

    fun progressUsedSkills(
        skillManager: SkillManager,
        entityId: String,
        skills: List<String>,
        success: Boolean
    ): List<Pair<String, List<com.jcraw.mud.core.SkillEvent>>> {
        return skills.map { skillName ->
            val events = skillManager.attemptSkillProgress(entityId, skillName, 10L, success)
                .getOrNull() ?: emptyList()
            skillName to events
        }
    }

    fun applyFreeHitDamage(
        worldState: com.jcraw.mud.core.WorldState,
        damage: Int
    ): com.jcraw.mud.core.WorldState =
        worldState.updatePlayer(
            worldState.player.copy(health = worldState.player.health - damage)
        )

    fun maybeCounterAttack(
        worldState: com.jcraw.mud.core.WorldState,
        npcId: String,
        spaceId: String,
        turnQueue: TurnQueueManager?
    ): com.jcraw.mud.core.WorldState {
        if (turnQueue == null) return worldState
        return CombatBehavior.triggerCounterAttack(
            npcId = npcId,
            spaceId = spaceId,
            worldState = worldState,
            turnQueue = turnQueue
        )
    }

    fun attemptFlee(
        resolver: AttackResolver?,
        playerId: String,
        hostiles: List<String>,
        direction: com.jcraw.mud.core.Direction,
        worldState: com.jcraw.mud.core.WorldState,
        skillManager: SkillManager
    ): FleeResult? {
        if (resolver == null) return null
        return resolveFlee(resolver, playerId, hostiles, direction, worldState, skillManager)
    }

    fun resolveFlee(
        resolver: AttackResolver,
        playerId: String,
        hostiles: List<String>,
        direction: com.jcraw.mud.core.Direction,
        worldState: com.jcraw.mud.core.WorldState,
        skillManager: SkillManager
    ): FleeResult = runBlocking {
        val result = FleeResolver(resolver).resolveFlee(
            fleeingEntityId = playerId,
            pursuers = hostiles,
            targetDirection = direction,
            worldState = worldState,
            skillManager = skillManager
        )
        grantFleeXp(skillManager, result)
        result
    }

    fun grantFleeXp(skillManager: SkillManager, result: FleeResult) {
        if (result.escapeSkillUsed) {
            skillManager.attemptSkillProgress(
                entityId = result.fleeingEntityId,
                skillName = "Escape",
                baseXp = 10L,
                success = result is FleeResult.Success
            )
        }
        result.pursuitSkillsUsed.forEach { (pursuerId, pursuitLevel) ->
            if (pursuitLevel > 0) {
                skillManager.attemptSkillProgress(
                    entityId = pursuerId,
                    skillName = "Pursuit",
                    baseXp = 10L,
                    success = result is FleeResult.Failure
                )
            }
        }
    }

    private fun healthBand(healthPercent: Int): String = when {
        healthPercent >= 100 -> "is in perfect health"
        healthPercent >= 90 -> "has a few scratches"
        healthPercent >= 75 -> "has some small wounds"
        healthPercent >= 50 -> "has quite a few wounds"
        healthPercent >= 30 -> "is bleeding badly"
        healthPercent >= 15 -> "looks pretty hurt"
        healthPercent >= 5 -> "is in awful condition"
        else -> "is nearly dead"
    }
}
