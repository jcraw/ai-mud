package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Catalog lookup for floor take/drop. Shared by console and GUI (MUD-039).
 */
object FloorItemTemplates {

    /** Prefer property templateId lookup, then full catalog for name-match. */
    fun forTake(itemRepository: ItemRepository, item: Entity.Item): Map<String, ItemTemplate> {
        val templates = mutableMapOf<String, ItemTemplate>()
        item.properties["templateId"]?.let { tid ->
            itemRepository.findTemplateById(tid).getOrNull()?.let { templates[it.id] = it }
        }
        if (templates.isEmpty()) {
            itemRepository.findAllTemplates().getOrNull()?.let { templates.putAll(it) }
        }
        return templates
    }

    /** Full catalog for name-match; fallback per inventory templateId. */
    fun forDrop(itemRepository: ItemRepository, player: PlayerState): Map<String, ItemTemplate> {
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
}
