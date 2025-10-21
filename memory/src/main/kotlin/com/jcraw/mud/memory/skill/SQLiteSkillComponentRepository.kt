package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.repository.SkillComponentRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of SkillComponentRepository
 * Manages complete skill component persistence as JSON
 */
class SQLiteSkillComponentRepository(
    private val database: SkillDatabase
) : SkillComponentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(entityId: String, component: SkillComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO skill_components
                (entity_id, component_data)
                VALUES (?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, json.encodeToString(component))
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun load(entityId: String): Result<SkillComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT component_data FROM skill_components WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<SkillComponent>(componentData)
                    Result.success(component)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM skill_components WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<Map<String, SkillComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT entity_id, component_data FROM skill_components"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()

                val components = mutableMapOf<String, SkillComponent>()
                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<SkillComponent>(componentData)
                    components[entityId] = component
                }
                Result.success(components)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
