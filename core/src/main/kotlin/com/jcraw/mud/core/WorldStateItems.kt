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
 * Dropped-item removal for [WorldState] members (MUD-034m). Gold/corpse order preserved.
 */
internal object WorldStateItems {

    fun removeDroppedItem(
        world: WorldState,
        spaceId: SpaceId,
        instanceId: String,
        removeEntity: Boolean,
        updateCorpses: Boolean
    ): WorldState {
        val (afterSpace, removedInstance) = stripFromSpace(world, spaceId, instanceId)
        var updated = afterSpace

        if (removeEntity) {
            updated = stripLinkedDropEntities(updated, spaceId, instanceId)
        }

        if (updateCorpses && removedInstance != null) {
            updated = stripFromCorpses(updated, spaceId, instanceId, removedInstance)
        }

        return updated
    }

    private fun stripFromSpace(
        world: WorldState,
        spaceId: SpaceId,
        instanceId: String
    ): Pair<WorldState, ItemInstance?> {
        val space = world.getSpace(spaceId) ?: return world to null
        val instance = space.itemsDropped.find { it.id == instanceId } ?: return world to null
        val updatedSpace = space.copy(
            itemsDropped = space.itemsDropped.filterNot { it.id == instanceId }
        )
        return world.updateSpace(spaceId, updatedSpace) to instance
    }

    private fun stripLinkedDropEntities(
        world: WorldState,
        spaceId: SpaceId,
        instanceId: String
    ): WorldState {
        var updated = world
        val dropEntities = updated.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Item>()
        dropEntities.filter { it.properties["instanceId"] == instanceId }.forEach { drop ->
            updated = updated.removeEntityFromSpace(spaceId, drop.id)
        }
        return updated
    }

    private fun stripFromCorpses(
        world: WorldState,
        spaceId: SpaceId,
        instanceId: String,
        removedInstance: ItemInstance
    ): WorldState {
        var updated = world
        val corpses = updated.getEntitiesInSpace(spaceId).filterIsInstance<Entity.Corpse>()
        corpses.forEach { corpse ->
            updated = applyCorpseRemoval(updated, spaceId, corpse, instanceId, removedInstance)
        }
        return updated
    }

    private fun applyCorpseRemoval(
        world: WorldState,
        spaceId: SpaceId,
        corpse: Entity.Corpse,
        instanceId: String,
        removedInstance: ItemInstance
    ): WorldState {
        var newCorpse = corpse
        var modified = false

        if (corpse.contents.any { it.id == instanceId }) {
            newCorpse = newCorpse.removeItem(instanceId)
            modified = true
        }

        if (removedInstance.templateId == GOLD_TEMPLATE_ID && corpse.goldAmount > 0) {
            val newGold = (newCorpse.goldAmount - removedInstance.quantity).coerceAtLeast(0)
            if (newGold != newCorpse.goldAmount) {
                newCorpse = newCorpse.copy(goldAmount = newGold)
                modified = true
            }
        }

        return if (modified) {
            world.replaceEntityInSpace(spaceId, corpse.id, newCorpse) ?: world
        } else {
            world
        }
    }
}
