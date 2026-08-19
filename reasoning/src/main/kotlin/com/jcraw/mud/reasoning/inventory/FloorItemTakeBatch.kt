package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SpaceId
import com.jcraw.mud.core.WorldState

/**
 * Batch / convenience take on [FloorItemTakeApply]. Shared by console and GUI (MUD-039).
 */
object FloorItemTakeBatch {

    data class Taken(
        val itemName: String,
        val floorEntityId: String
    )

    data class Batch(
        val world: WorldState,
        val taken: List<Taken>,
        val failed: List<FloorItemTakeApply.Result.Failure>
    )

    fun apply(
        world: WorldState,
        spaceId: SpaceId,
        floorItem: Entity.Item,
        templates: Map<String, ItemTemplate>
    ): FloorItemTakeApply.Result = FloorItemTakeApply.apply(world, world.player, spaceId, floorItem, templates)

    fun takeMany(
        world: WorldState,
        spaceId: SpaceId,
        items: List<Entity.Item>,
        templatesFor: (Entity.Item) -> Map<String, ItemTemplate>
    ): Batch {
        var current = world
        val taken = mutableListOf<Taken>()
        val failed = mutableListOf<FloorItemTakeApply.Result.Failure>()
        items.forEach { item ->
            when (val result = apply(current, spaceId, item, templatesFor(item))) {
                is FloorItemTakeApply.Result.Success -> {
                    current = result.world
                    taken.add(Taken(result.itemName, item.id))
                }
                is FloorItemTakeApply.Result.Failure -> failed.add(result)
            }
        }
        return Batch(current, taken, failed)
    }
}
