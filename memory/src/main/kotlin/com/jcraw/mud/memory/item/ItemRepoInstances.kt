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

/**
 * Instance CRUD for [SQLiteItemRepository] (MUD-034m pure-move).
 */
internal object ItemRepoInstances {

    fun findById(database: ItemDatabase, instanceId: String): Result<ItemInstance?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, instanceId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    Result.success(ItemRepoMapping.instanceFrom(rs))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun findByTemplate(database: ItemDatabase, templateId: String): Result<List<ItemInstance>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances WHERE template_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, templateId)
                val rs = stmt.executeQuery()
                val instances = mutableListOf<ItemInstance>()

                while (rs.next()) {
                    instances.add(ItemRepoMapping.instanceFrom(rs))
                }
                Result.success(instances)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun save(database: ItemDatabase, instance: ItemInstance): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement(insertSql()).use { stmt ->
                bindInstance(stmt, instance)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertSql(): String = """
                INSERT OR REPLACE INTO item_instances
                (id, template_id, quality, charges, quantity)
                VALUES (?, ?, ?, ?, ?)
    """.trimIndent()

    private fun bindInstance(stmt: java.sql.PreparedStatement, instance: ItemInstance) {
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
    }

    fun delete(database: ItemDatabase, instanceId: String): Result<Unit> {
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

    fun findAll(database: ItemDatabase): Result<Map<String, ItemInstance>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM item_instances"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val instances = mutableMapOf<String, ItemInstance>()

                while (rs.next()) {
                    val instance = ItemRepoMapping.instanceFrom(rs)
                    instances[instance.id] = instance
                }
                Result.success(instances)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
