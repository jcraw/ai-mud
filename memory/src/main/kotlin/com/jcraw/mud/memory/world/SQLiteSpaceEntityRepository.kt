package com.jcraw.mud.memory.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.repository.SpaceEntityRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite-backed repository for persisted space entities.
 * Currently supports NPC entities with full serialization.
 */
class SQLiteSpaceEntityRepository(
    private val database: WorldDatabase
) : SpaceEntityRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(entity: Entity): Result<Unit> = runCatching {
        val (type, payload) = serializeEntity(entity)
        val conn = database.getConnection()
        val sql = """
            INSERT OR REPLACE INTO space_entities
            (id, entity_type, entity_json)
            VALUES (?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, entity.id)
            stmt.setString(2, type)
            stmt.setString(3, payload)
            stmt.executeUpdate()
        }
    }

    override fun saveAll(entities: List<Entity>): Result<Unit> = runCatching {
        if (entities.isEmpty()) return@runCatching
        val conn = database.getConnection()
        conn.autoCommit = false

        try {
            val sql = """
                INSERT OR REPLACE INTO space_entities
                (id, entity_type, entity_json)
                VALUES (?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                entities.forEach { entity ->
                    val (type, payload) = serializeEntity(entity)
                    stmt.setString(1, entity.id)
                    stmt.setString(2, type)
                    stmt.setString(3, payload)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = true
        }
    }

    override fun findById(id: String): Result<Entity?> = runCatching {
        val conn = database.getConnection()
        val sql = "SELECT entity_type, entity_json FROM space_entities WHERE id = ?"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id)
            val rs = stmt.executeQuery()
            if (!rs.next()) return@runCatching null

            val type = rs.getString("entity_type")
            val payload = rs.getString("entity_json")
            deserializeEntity(type, payload)
        }
    }

    override fun delete(id: String): Result<Unit> = runCatching {
        val conn = database.getConnection()
        conn.prepareStatement("DELETE FROM space_entities WHERE id = ?").use { stmt ->
            stmt.setString(1, id)
            stmt.executeUpdate()
        }
    }

    private fun serializeEntity(entity: Entity): Pair<String, String> {
        return when (entity) {
            is Entity.NPC -> "NPC" to json.encodeToString(Entity.NPC.serializer(), entity)
            is Entity.Feature -> "Feature" to json.encodeToString(Entity.Feature.serializer(), entity)
            else -> throw UnsupportedOperationException("SpaceEntityRepository currently supports only NPC and Feature entities (got ${entity::class.simpleName})")
        }
    }

    private fun deserializeEntity(type: String, payload: String): Entity? {
        return when (type) {
            "NPC" -> json.decodeFromString(Entity.NPC.serializer(), payload)
            "Feature" -> json.decodeFromString(Entity.Feature.serializer(), payload)
            else -> null
        }
    }
}
