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
    "WildcardImport"
)

package com.jcraw.mud.memory.item

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.core.repository.ItemRepository
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of ItemRepository
 * Thin facade — bodies in ItemRepo* extracts (MUD-034m).
 */
class SQLiteItemRepository(
    private val database: ItemDatabase
) : ItemRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findTemplateById(templateId: String): Result<ItemTemplate?> =
        ItemRepoTemplates.findById(database, json, templateId)

    override fun findAllTemplates(): Result<Map<String, ItemTemplate>> =
        ItemRepoTemplates.findAll(database, json)

    override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> =
        ItemRepoTemplates.findByType(database, json, type)

    override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> =
        ItemRepoTemplates.findByRarity(database, json, rarity)

    override fun saveTemplate(template: ItemTemplate): Result<Unit> =
        ItemRepoTemplates.save(database, json, template)

    override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> =
        ItemRepoTemplates.saveAll(database, json, templates)

    override fun deleteTemplate(templateId: String): Result<Unit> =
        ItemRepoTemplates.delete(database, templateId)

    override fun findInstanceById(instanceId: String): Result<ItemInstance?> =
        ItemRepoInstances.findById(database, instanceId)

    override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> =
        ItemRepoInstances.findByTemplate(database, templateId)

    override fun saveInstance(instance: ItemInstance): Result<Unit> =
        ItemRepoInstances.save(database, instance)

    override fun deleteInstance(instanceId: String): Result<Unit> =
        ItemRepoInstances.delete(database, instanceId)

    override fun findAllInstances(): Result<Map<String, ItemInstance>> =
        ItemRepoInstances.findAll(database)
}
