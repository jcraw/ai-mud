package com.jcraw.app.handlers

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Templates for floor take: prefer property templateId lookup, then full catalog for name-match.
 */
internal fun floorTakeTemplates(
    itemRepository: ItemRepository,
    item: Entity.Item
): Map<String, ItemTemplate> {
    val templates = mutableMapOf<String, ItemTemplate>()
    item.properties["templateId"]?.let { tid ->
        itemRepository.findTemplateById(tid).getOrNull()?.let { templates[it.id] = it }
    }
    if (templates.isEmpty()) {
        itemRepository.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
    }
    return templates
}

/**
 * Templates for floor drop: full catalog for name-match; fallback per inventory templateId.
 */
internal fun floorDropTemplates(
    itemRepository: ItemRepository,
    player: PlayerState
): Map<String, ItemTemplate> {
    val templates = mutableMapOf<String, ItemTemplate>()
    itemRepository.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
    if (templates.isEmpty()) {
        player.inventoryComponent.items.forEach { instance ->
            itemRepository.findTemplateById(instance.templateId).getOrNull()?.let {
                templates[it.id] = it
            }
        }
    }
    return templates
}
