@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter",
    "UseCheckOrError"
)

package com.jcraw.mud.core

/**
 * Entity CRUD + space membership for [WorldState] members (MUD-034m).
 */
internal object WorldStateEntities {

    fun get(world: WorldState, entityId: String): Entity? = world.entities[entityId]

    fun update(world: WorldState, entity: Entity): WorldState =
        world.copy(entities = world.entities + (entity.id to entity))

    fun remove(world: WorldState, entityId: String): WorldState =
        world.copy(entities = world.entities - entityId)

    fun inSpace(world: WorldState, spaceId: SpaceId): List<Entity> {
        val space = world.getSpace(spaceId) ?: return emptyList()
        return space.entities.mapNotNull { entityId -> world.entities[entityId] }
    }

    fun findSpaceContaining(world: WorldState, entityId: String): SpaceId? =
        world.spaces.entries.firstOrNull { (_, space) -> space.entities.contains(entityId) }?.key

    fun addToSpace(world: WorldState, spaceId: SpaceId, entity: Entity): WorldState {
        val space = world.getSpace(spaceId) ?: return world
        val updatedSpace = space.addEntity(entity.id)
        return world.updateEntity(entity).updateSpace(spaceId, updatedSpace)
    }

    fun removeFromSpace(world: WorldState, spaceId: SpaceId, entityId: String): WorldState {
        val space = world.getSpace(spaceId) ?: return world
        val updatedSpace = space.removeEntity(entityId)
        return world.updateSpace(spaceId, updatedSpace).removeEntity(entityId)
    }

    fun replaceInSpace(
        world: WorldState,
        spaceId: SpaceId,
        oldEntityId: String,
        newEntity: Entity
    ): WorldState {
        val space = world.getSpace(spaceId) ?: return world
        val updatedSpace = space.removeEntity(oldEntityId).addEntity(newEntity.id)
        return world.removeEntity(oldEntityId)
            .updateEntity(newEntity)
            .updateSpace(spaceId, updatedSpace)
    }
}
