package com.jcraw.mud.client.handlers

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.repository.ItemRepository
import com.jcraw.mud.reasoning.inventory.FloorItemTemplates

/** Thin GUI wrapper around [FloorItemTemplates] (MUD-039). */
internal fun floorTakeTemplates(
    itemRepository: ItemRepository,
    item: Entity.Item
): Map<String, ItemTemplate> = FloorItemTemplates.forTake(itemRepository, item)

internal fun floorDropTemplates(
    itemRepository: ItemRepository,
    player: PlayerState
): Map<String, ItemTemplate> = FloorItemTemplates.forDrop(itemRepository, player)
