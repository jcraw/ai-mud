package com.jcraw.mud.client

import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import com.jcraw.mud.memory.item.SQLiteItemRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Item template load/cache/fallback helpers for [EngineGameClient].
 * Pure extract from the facade — no behavior change.
 */
object ClientItemTemplateCache {

    fun load(
        itemRepository: SQLiteItemRepository,
        itemJson: Json,
        onWarning: (String) -> Unit
    ): MutableMap<String, ItemTemplate> {
        val existing = itemRepository.findAllTemplates().getOrElse { emptyMap() }
        if (existing.isNotEmpty()) {
            return existing.toMutableMap()
        }

        val templates = loadTemplatesFromResource(itemJson)
        if (templates.isNotEmpty()) {
            itemRepository.saveTemplates(templates).onFailure {
                onWarning("Warning: failed to seed item templates (${it.message})")
            }
        }
        return templates.associateBy { it.id }.toMutableMap()
    }

    fun loadTemplatesFromResource(itemJson: Json): List<ItemTemplate> {
        val resourceCandidates = listOf(
            "item_templates.json",
            "memory/src/main/resources/item_templates.json"
        )

        for (resource in resourceCandidates) {
            val jsonText = readResourceText(resource)
            if (jsonText != null) {
                return runCatching {
                    itemJson.decodeFromString<List<ItemTemplate>>(jsonText)
                }.getOrElse { emptyList() }
            }
        }

        return emptyList()
    }

    fun readResourceText(path: String): String? {
        val classLoaderStream = EngineGameClient::class.java.classLoader.getResourceAsStream(path)
        if (classLoaderStream != null) {
            return classLoaderStream.bufferedReader().use { it.readText() }
        }

        val filePath = Paths.get(path)
        return if (Files.exists(filePath)) {
            Files.readString(filePath)
        } else {
            null
        }
    }

    fun getItemTemplate(
        templateId: String,
        cache: MutableMap<String, ItemTemplate>,
        itemRepository: SQLiteItemRepository
    ): ItemTemplate {
        return cache[templateId]
            ?: itemRepository.findTemplateById(templateId).getOrNull()?.also {
                cache[templateId] = it
            }
            ?: createFallbackTemplate(templateId, cache)
    }

    fun createFallbackTemplate(
        templateId: String,
        cache: MutableMap<String, ItemTemplate>
    ): ItemTemplate {
        val displayName = templateId.split('_').joinToString(" ") { token ->
            token.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
        }.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }

        val fallback = ItemTemplate(
            id = templateId,
            name = displayName,
            type = ItemType.MISC,
            tags = emptyList(),
            properties = mapOf("value" to "10"),
            rarity = Rarity.COMMON,
            description = "A $displayName."
        )
        cache[templateId] = fallback
        return fallback
    }
}
