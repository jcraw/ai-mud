package com.jcraw.mud.core.repository

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity

/**
 * Repository interface for item system persistence
 * Manages item templates and instances
 */
interface ItemRepository {
    /**
     * Find template by ID
     * Returns null if template not found
     */
    fun findTemplateById(templateId: String): Result<ItemTemplate?>

    /**
     * Find all templates
     * Returns map of templateId -> ItemTemplate
     */
    fun findAllTemplates(): Result<Map<String, ItemTemplate>>

    /**
     * Find templates by type
     */
    fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>>

    /**
     * Find templates by rarity
     */
    fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>>

    /**
     * Save or update item template
     * Overwrites existing template if ID matches
     */
    fun saveTemplate(template: ItemTemplate): Result<Unit>

    /**
     * Save multiple templates (bulk operation for initial load)
     */
    fun saveTemplates(templates: List<ItemTemplate>): Result<Unit>

    /**
     * Delete template by ID
     */
    fun deleteTemplate(templateId: String): Result<Unit>

    /**
     * Find instance by ID
     * Returns null if instance not found
     */
    fun findInstanceById(instanceId: String): Result<ItemInstance?>

    /**
     * Find all instances for a template
     */
    fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>>

    /**
     * Save or update item instance
     */
    fun saveInstance(instance: ItemInstance): Result<Unit>

    /**
     * Delete instance by ID
     */
    fun deleteInstance(instanceId: String): Result<Unit>

    /**
     * Find all instances
     * Returns map of instanceId -> ItemInstance
     */
    fun findAllInstances(): Result<Map<String, ItemInstance>>
}
