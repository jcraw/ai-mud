package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.*

/**
 * Applies status effects to entities in combat
 * Handles stacking rules, world state updates, and event logging
 *
 * Design principles:
 * - Stacking rules encapsulated in CombatComponent
 * - Immutable: Returns new WorldState
 * - Event logging: Tracks all effect applications
 * - Repository integration: Persists effects (when repository available)
 */
class StatusEffectApplicator {

    /**
     * Apply status effect to an entity
     * Handles stacking, updates world state, and logs event
     *
     * @param entityId ID of entity to apply effect to
     * @param effect Status effect to apply
     * @param worldState Current world state
     * @param gameTime Current game time for event logging
     * @return Updated world state with effect applied
     */
    fun applyEffectToEntity(
        entityId: String,
        effect: StatusEffect,
        worldState: WorldState,
        gameTime: Long
    ): ApplicationResult {
        // Find the entity
        val entity = worldState.findEntity(entityId)
            ?: return ApplicationResult.failure("Entity not found: $entityId")

        // Get combat component
        val combatComponent = entity.getComponent<CombatComponent>(ComponentType.COMBAT)
            ?: return ApplicationResult.failure("Entity has no combat component")

        // Check if effect already exists (for event logging)
        val existingEffect = combatComponent.statusEffects.find { it.type == effect.type }
        val wasStacked = existingEffect != null && effect.type in stackableEffects
        val wasReplaced = existingEffect != null && effect.type !in stackableEffects

        // Apply effect to combat component (handles stacking rules internally)
        val updatedCombat = combatComponent.applyStatus(effect)

        // Update entity in world state
        val updatedWorldState = worldState.updateEntityCombat(entityId, updatedCombat)

        // Create event
        val event = CombatEvent.StatusEffectApplied(
            gameTime = gameTime,
            sourceEntityId = effect.source,
            targetEntityId = entityId,
            effect = effect,
            wasStacked = wasStacked,
            wasReplaced = wasReplaced
        )

        return ApplicationResult.success(
            worldState = updatedWorldState,
            event = event,
            wasStacked = wasStacked,
            wasReplaced = wasReplaced
        )
    }

    /**
     * Remove status effect from an entity
     *
     * @param entityId ID of entity to remove effect from
     * @param effectType Type of effect to remove
     * @param worldState Current world state
     * @param gameTime Current game time for event logging
     * @param wasExpired True if effect expired naturally, false if removed manually
     * @return Updated world state with effect removed
     */
    fun removeEffectFromEntity(
        entityId: String,
        effectType: StatusEffectType,
        worldState: WorldState,
        gameTime: Long,
        wasExpired: Boolean = true
    ): ApplicationResult {
        // Find the entity
        val entity = worldState.findEntity(entityId)
            ?: return ApplicationResult.failure("Entity not found: $entityId")

        // Get combat component
        val combatComponent = entity.getComponent<CombatComponent>(ComponentType.COMBAT)
            ?: return ApplicationResult.failure("Entity has no combat component")

        // Remove effect
        val updatedCombat = combatComponent.removeStatus(effectType)

        // Update entity in world state
        val updatedWorldState = worldState.updateEntityCombat(entityId, updatedCombat)

        // Create event
        val event = CombatEvent.StatusEffectRemoved(
            gameTime = gameTime,
            sourceEntityId = entityId,
            effectType = effectType,
            wasExpired = wasExpired
        )

        return ApplicationResult.success(
            worldState = updatedWorldState,
            event = event
        )
    }

    /**
     * Process tick for all status effects on an entity
     * Handles duration decrement, expiration, and DOT/regen application
     *
     * @param entityId ID of entity whose effects to tick
     * @param worldState Current world state
     * @param gameTime Current game time
     * @return Updated world state with effects ticked
     */
    fun tickEffects(
        entityId: String,
        worldState: WorldState,
        gameTime: Long
    ): ApplicationResult {
        // Find the entity
        val entity = worldState.findEntity(entityId)
            ?: return ApplicationResult.failure("Entity not found: $entityId")

        // Get combat component
        val combatComponent = entity.getComponent<CombatComponent>(ComponentType.COMBAT)
            ?: return ApplicationResult.failure("Entity has no combat component")

        // Tick effects (handles duration, DOT, regen internally)
        val (updatedCombat, applications) = combatComponent.tickEffects(gameTime)

        // Update entity in world state
        val updatedWorldState = worldState.updateEntityCombat(entityId, updatedCombat)

        // Create event if any effects were applied
        val event = if (applications.isNotEmpty()) {
            CombatEvent.StatusEffectsTicked(
                gameTime = gameTime,
                sourceEntityId = entityId,
                applications = applications
            )
        } else {
            null
        }

        return ApplicationResult.success(
            worldState = updatedWorldState,
            event = event
        )
    }

    companion object {
        /**
         * Status effect types that stack (can have multiple instances)
         */
        private val stackableEffects = setOf(
            StatusEffectType.STRENGTH_BOOST,
            StatusEffectType.REGENERATION,
            StatusEffectType.SHIELD
        )
    }
}

/**
 * Result of applying or removing a status effect
 */
sealed class ApplicationResult {
    abstract val isSuccess: Boolean

    /**
     * Effect was successfully applied/removed
     */
    data class Success(
        val worldState: WorldState,
        val event: CombatEvent?,
        val wasStacked: Boolean = false,
        val wasReplaced: Boolean = false
    ) : ApplicationResult() {
        override val isSuccess = true
    }

    /**
     * Application/removal failed
     */
    data class Failure(
        val reason: String
    ) : ApplicationResult() {
        override val isSuccess = false
    }

    companion object {
        fun success(
            worldState: WorldState,
            event: CombatEvent? = null,
            wasStacked: Boolean = false,
            wasReplaced: Boolean = false
        ) = Success(worldState, event, wasStacked, wasReplaced)

        fun failure(reason: String) = Failure(reason)
    }
}

/**
 * Helper to find entity by ID in world state
 */
private fun WorldState.findEntity(entityId: String): Entity? {
    if (player.id == entityId) {
        // Player is not a true Entity yet, return null for now
        // This will be fixed when Player becomes an Entity in the world
        return null
    }

    // Search all rooms for the entity
    return rooms.values.flatMap { it.entities }.find { it.id == entityId }
}

/**
 * Helper to get component from entity
 */
private inline fun <reified T : Component> Entity.getComponent(type: ComponentType): T? {
    return when (this) {
        is Entity.NPC -> {
            components[type] as? T
        }
        else -> null
    }
}

/**
 * Helper to update entity's combat component in world state
 * Returns new WorldState with updated entity
 */
private fun WorldState.updateEntityCombat(
    entityId: String,
    updatedCombat: CombatComponent
): WorldState {
    // Find which room contains the entity
    val roomWithEntity = rooms.values.find { room ->
        room.entities.any { it.id == entityId }
    } ?: return this // Entity not found, return unchanged

    // Update the entity in the room
    val updatedEntities = roomWithEntity.entities.map { entity ->
        if (entity.id == entityId && entity is Entity.NPC) {
            // Replace combat component using ComponentHost interface
            entity.withComponent(updatedCombat)
        } else {
            entity
        }
    }

    // Update room with updated entities
    val updatedRoom = roomWithEntity.copy(entities = updatedEntities)

    // Update world state with updated room
    return copy(rooms = rooms + (updatedRoom.id to updatedRoom))
}
