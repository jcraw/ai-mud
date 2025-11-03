package com.jcraw.mud.reasoning

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Simple in-memory ItemRepository for tests.
 * Supports template/instance CRUD with deterministic Result responses.
 */
class TestItemRepository(
    initialTemplates: Map<String, ItemTemplate> = emptyMap(),
    initialInstances: Map<String, ItemInstance> = emptyMap()
) : ItemRepository {

    private val templates = initialTemplates.toMutableMap()
    private val instances = initialInstances.toMutableMap()

    override fun findTemplateById(templateId: String): Result<ItemTemplate?> =
        Result.success(templates[templateId])

    override fun findAllTemplates(): Result<Map<String, ItemTemplate>> =
        Result.success(templates.toMap())

    override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> =
        Result.success(templates.values.filter { it.type == type })

    override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
        Result.success(templates.values.filter { it.rarity == rarity })

    override fun saveTemplate(template: ItemTemplate): Result<Unit> {
        templates[template.id] = template
        return Result.success(Unit)
    }

    override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> {
        templates.forEach { this.templates[it.id] = it }
        return Result.success(Unit)
    }

    override fun deleteTemplate(templateId: String): Result<Unit> {
        templates.remove(templateId)
        return Result.success(Unit)
    }

    override fun findInstanceById(instanceId: String): Result<ItemInstance?> =
        Result.success(instances[instanceId])

    override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> =
        Result.success(instances.values.filter { it.templateId == templateId })

    override fun saveInstance(instance: ItemInstance): Result<Unit> {
        instances[instance.id] = instance
        return Result.success(Unit)
    }

    override fun deleteInstance(instanceId: String): Result<Unit> {
        instances.remove(instanceId)
        return Result.success(Unit)
    }

    override fun findAllInstances(): Result<Map<String, ItemInstance>> =
        Result.success(instances.toMap())
}
