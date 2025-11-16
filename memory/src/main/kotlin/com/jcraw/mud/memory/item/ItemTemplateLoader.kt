package com.jcraw.mud.memory.item

import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.repository.ItemRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Utility to load item templates from JSON into the database
 */
object ItemTemplateLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load item templates from JSON resource file into the repository.
     * This should be called once during application initialization.
     *
     * @param repository The item repository to save templates to
     * @return Number of templates loaded
     */
    fun loadTemplatesFromResource(repository: ItemRepository): Int {
        try {
            // Check if templates already loaded
            val existing = repository.findAllTemplates().getOrNull()
            if (existing != null && existing.isNotEmpty()) {
                println("[ItemTemplateLoader] Templates already loaded (${existing.size} templates)")
                return existing.size
            }

            // Load from JSON resource
            val resourceStream = ItemTemplateLoader::class.java.classLoader
                .getResourceAsStream("item_templates.json")
                ?: throw IllegalStateException("Could not find item_templates.json resource")

            val jsonText = resourceStream.bufferedReader().use { it.readText() }
            val templates = json.decodeFromString<List<ItemTemplate>>(jsonText)

            // Save all templates to database
            templates.forEach { template ->
                repository.saveTemplate(template)
            }

            println("[ItemTemplateLoader] Loaded ${templates.size} item templates from JSON")
            return templates.size
        } catch (e: Exception) {
            System.err.println("[ItemTemplateLoader] Failed to load templates: ${e.message}")
            e.printStackTrace()
            return 0
        }
    }
}
