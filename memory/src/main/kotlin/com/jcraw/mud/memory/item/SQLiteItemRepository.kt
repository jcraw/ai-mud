package com.jcraw.mud.memory.item

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of ItemRepository
 * Manages item template and instance persistence
 */
class SQLiteItemRepository(
    private val database: ItemDatabase
) : ItemRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findTemplateById(templateId: String): Result<ItemTemplate?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, templateId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val template = ItemTemplate(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        type = ItemType.valueOf(rs.getString("type")),
                        tags = json.decodeFromString<List<String>>(rs.getString("tags")),
                        properties = json.decodeFromString<Map<String, String>>(rs.getString("properties")),
                        rarity = Rarity.valueOf(rs.getString("rarity")),
                        description = rs.getString("description"),
                        equipSlot = rs.getString("equip_slot")?.let { EquipSlot.valueOf(it) }
                    )
                    Result.success(template)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAllTemplates(): Result<Map<String, ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val templates = mutableMapOf<String, ItemTemplate>()

                while (rs.next()) {
                    val template = ItemTemplate(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        type = ItemType.valueOf(rs.getString("type")),
                        tags = json.decodeFromString<List<String>>(rs.getString("tags")),
                        properties = json.decodeFromString<Map<String, String>>(rs.getString("properties")),
                        rarity = Rarity.valueOf(rs.getString("rarity")),
                        description = rs.getString("description"),
                        equipSlot = rs.getString("equip_slot")?.let { EquipSlot.valueOf(it) }
                    )
                    templates[template.id] = template
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findTemplatesByType(type: ItemType): Result<List<ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE type = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, type.name)
                val rs = stmt.executeQuery()
                val templates = mutableListOf<ItemTemplate>()

                while (rs.next()) {
                    val template = ItemTemplate(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        type = ItemType.valueOf(rs.getString("type")),
                        tags = json.decodeFromString<List<String>>(rs.getString("tags")),
                        properties = json.decodeFromString<Map<String, String>>(rs.getString("properties")),
                        rarity = Rarity.valueOf(rs.getString("rarity")),
                        description = rs.getString("description"),
                        equipSlot = rs.getString("equip_slot")?.let { EquipSlot.valueOf(it) }
                    )
                    templates.add(template)
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findTemplatesByRarity(rarity: Rarity): Result<List<ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE rarity = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, rarity.name)
                val rs = stmt.executeQuery()
                val templates = mutableListOf<ItemTemplate>()

                while (rs.next()) {
                    val template = ItemTemplate(
                        id = rs.getString("id"),
                        name = rs.getString("name"),
                        type = ItemType.valueOf(rs.getString("type")),
                        tags = json.decodeFromString<List<String>>(rs.getString("tags")),
                        properties = json.decodeFromString<Map<String, String>>(rs.getString("properties")),
                        rarity = Rarity.valueOf(rs.getString("rarity")),
                        description = rs.getString("description"),
                        equipSlot = rs.getString("equip_slot")?.let { EquipSlot.valueOf(it) }
                    )
                    templates.add(template)
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun saveTemplate(template: ItemTemplate): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO item_templates
                (id, name, type, tags, properties, rarity, description, equip_slot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, template.id)
                stmt.setString(2, template.name)
                stmt.setString(3, template.type.name)
                stmt.setString(4, json.encodeToString(template.tags))
                stmt.setString(5, json.encodeToString(template.properties))
                stmt.setString(6, template.rarity.name)
                stmt.setString(7, template.description)
                stmt.setString(8, template.equipSlot?.name)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun saveTemplates(templates: List<ItemTemplate>): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO item_templates
                (id, name, type, tags, properties, rarity, description, equip_slot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                for (template in templates) {
                    stmt.setString(1, template.id)
                    stmt.setString(2, template.name)
                    stmt.setString(3, template.type.name)
                    stmt.setString(4, json.encodeToString(template.tags))
                    stmt.setString(5, json.encodeToString(template.properties))
                    stmt.setString(6, template.rarity.name)
                    stmt.setString(7, template.description)
                    stmt.setString(8, template.equipSlot?.name)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteTemplate(templateId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM item_templates WHERE id = ?").use { stmt ->
                stmt.setString(1, templateId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findInstanceById(instanceId: String): Result<ItemInstance?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, instanceId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val instance = ItemInstance(
                        id = rs.getString("id"),
                        templateId = rs.getString("template_id"),
                        quality = rs.getInt("quality"),
                        charges = rs.getObject("charges") as? Int,
                        quantity = rs.getInt("quantity")
                    )
                    Result.success(instance)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findInstancesByTemplate(templateId: String): Result<List<ItemInstance>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances WHERE template_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, templateId)
                val rs = stmt.executeQuery()
                val instances = mutableListOf<ItemInstance>()

                while (rs.next()) {
                    val instance = ItemInstance(
                        id = rs.getString("id"),
                        templateId = rs.getString("template_id"),
                        quality = rs.getInt("quality"),
                        charges = rs.getObject("charges") as? Int,
                        quantity = rs.getInt("quantity")
                    )
                    instances.add(instance)
                }
                Result.success(instances)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun saveInstance(instance: ItemInstance): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO item_instances
                (id, template_id, quality, charges, quantity)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, instance.id)
                stmt.setString(2, instance.templateId)
                stmt.setInt(3, instance.quality)
                val charges = instance.charges
                if (charges != null) {
                    stmt.setInt(4, charges)
                } else {
                    stmt.setNull(4, java.sql.Types.INTEGER)
                }
                stmt.setInt(5, instance.quantity)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteInstance(instanceId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM item_instances WHERE id = ?").use { stmt ->
                stmt.setString(1, instanceId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAllInstances(): Result<Map<String, ItemInstance>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val instances = mutableMapOf<String, ItemInstance>()

                while (rs.next()) {
                    val instance = ItemInstance(
                        id = rs.getString("id"),
                        templateId = rs.getString("template_id"),
                        quality = rs.getInt("quality"),
                        charges = rs.getObject("charges") as? Int,
                        quantity = rs.getInt("quantity")
                    )
                    instances[instance.id] = instance
                }
                Result.success(instances)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
