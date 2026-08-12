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

import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Template CRUD for [SQLiteItemRepository] (MUD-034m pure-move).
 */
internal object ItemRepoTemplates {

    fun findById(database: ItemDatabase, json: Json, templateId: String): Result<ItemTemplate?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, templateId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    Result.success(ItemRepoMapping.templateFrom(rs, json))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findAll(database: ItemDatabase, json: Json): Result<Map<String, ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val templates = mutableMapOf<String, ItemTemplate>()

                while (rs.next()) {
                    val template = ItemRepoMapping.templateFrom(rs, json)
                    templates[template.id] = template
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findByType(database: ItemDatabase, json: Json, type: ItemType): Result<List<ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE type = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, type.name)
                val rs = stmt.executeQuery()
                val templates = mutableListOf<ItemTemplate>()

                while (rs.next()) {
                    templates.add(ItemRepoMapping.templateFrom(rs, json))
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findByRarity(database: ItemDatabase, json: Json, rarity: Rarity): Result<List<ItemTemplate>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_templates WHERE rarity = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, rarity.name)
                val rs = stmt.executeQuery()
                val templates = mutableListOf<ItemTemplate>()

                while (rs.next()) {
                    templates.add(ItemRepoMapping.templateFrom(rs, json))
                }
                Result.success(templates)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun save(database: ItemDatabase, json: Json, template: ItemTemplate): Result<Unit> {
        return try {
            val conn = database.getConnection()
            bindTemplate(conn.prepareStatement(insertSql()), json, template).use { stmt ->
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveAll(database: ItemDatabase, json: Json, templates: List<ItemTemplate>): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement(insertSql()).use { stmt ->
                for (template in templates) {
                    bindTemplate(stmt, json, template)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun delete(database: ItemDatabase, templateId: String): Result<Unit> {
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

    private fun insertSql(): String = """
                INSERT OR REPLACE INTO item_templates
                (id, name, type, tags, properties, rarity, description, equip_slot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    private fun bindTemplate(
        stmt: java.sql.PreparedStatement,
        json: Json,
        template: ItemTemplate
    ): java.sql.PreparedStatement {
        stmt.setString(1, template.id)
        stmt.setString(2, template.name)
        stmt.setString(3, template.type.name)
        stmt.setString(4, json.encodeToString(template.tags))
        stmt.setString(5, json.encodeToString(template.properties))
        stmt.setString(6, template.rarity.name)
        stmt.setString(7, template.description)
        stmt.setString(8, template.equipSlot?.name)
        return stmt
    }
}
