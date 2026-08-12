@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Component
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.reasoning.skill.SkillManager

/**
 * Shared entity/component lookup for attack + flee resolve (MUD-034k pure-move).
 */
internal object CombatEntityLookup {

    fun findEntity(worldState: WorldState, entityId: String): Entity? {
        if (worldState.player.id != entityId) {
            return worldState.entities[entityId]
        }
        return playerAsEntity(worldState)
    }

    private fun playerAsEntity(worldState: WorldState): Entity.Player {
        val inv = worldState.player.inventoryComponent
        val equippedWeapon = inv.getEquipped(EquipSlot.HANDS_MAIN)
            ?: inv.getEquipped(EquipSlot.HANDS_BOTH)
        val equippedArmor = inv.getEquipped(EquipSlot.CHEST)
        return Entity.Player(
            id = worldState.player.id,
            name = worldState.player.name,
            description = "Player character",
            playerId = worldState.player.id,
            health = worldState.player.health,
            maxHealth = worldState.player.maxHealth,
            equippedWeapon = equippedWeapon?.id,
            equippedArmor = equippedArmor?.id
        )
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Component> getComponent(
        entity: Entity,
        type: ComponentType,
        worldState: WorldState,
        skillManager: SkillManager
    ): T? = when (entity) {
        is Entity.Player -> playerComponent(entity, type, worldState, skillManager)
        is Entity.NPC -> entity.components[type] as? T
        is Entity.Item, is Entity.Feature, is Entity.Corpse -> null
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Component> playerComponent(
        entity: Entity.Player,
        type: ComponentType,
        worldState: WorldState,
        skillManager: SkillManager
    ): T? = when (type) {
        ComponentType.SKILL -> skillManager.getSkillComponent(entity.id) as? T
        ComponentType.COMBAT -> CombatComponent(
            currentHp = worldState.player.health,
            maxHp = worldState.player.maxHealth
        ) as? T
        else -> null
    }
}
