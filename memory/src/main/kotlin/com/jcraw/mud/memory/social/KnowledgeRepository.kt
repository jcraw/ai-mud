package com.jcraw.mud.memory.social

import com.jcraw.mud.core.KnowledgeEntry
import com.jcraw.mud.core.KnowledgeSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for NPC knowledge persistence
 */
interface KnowledgeRepository {
    fun save(entry: KnowledgeEntry): Result<Unit>
    fun findById(id: String): Result<KnowledgeEntry?>
    fun findByNpcId(npcId: String): Result<List<KnowledgeEntry>>
    fun findByCategory(npcId: String, category: String): Result<List<KnowledgeEntry>>
    fun delete(id: String): Result<Unit>
    fun deleteAllForNpc(npcId: String): Result<Unit>
}

/**
 * SQLite implementation of KnowledgeRepository
 */
class SqliteKnowledgeRepository(
    private val database: SocialDatabase
) : KnowledgeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(entry: KnowledgeEntry): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO knowledge_entries
                (id, npc_id, topic, question, content, category, timestamp, source, is_canon, tags)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            // Extract first tag value as category for backward compatibility
            val category = entry.tags["category"] ?: "general"
            val tagsJson = if (entry.tags.isEmpty()) {
                "{}"
            } else {
                json.encodeToString(entry.tags)
            }

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entry.id)
                stmt.setString(2, entry.entityId)
                stmt.setString(3, entry.topic)
                stmt.setString(4, entry.question)
                stmt.setString(5, entry.content)
                stmt.setString(6, category)
                stmt.setLong(7, entry.timestamp)
                stmt.setString(8, entry.source.name)
                stmt.setBoolean(9, entry.isCanon)
                stmt.setString(10, tagsJson)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findById(id: String): Result<KnowledgeEntry?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM knowledge_entries WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val tagsStr = rs.getString("tags").orEmpty()
                    val tags = tagsStr.takeIf { it.isNotBlank() }?.let {
                        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
                    } ?: emptyMap()

                    val entry = KnowledgeEntry(
                        id = rs.getString("id"),
                        entityId = rs.getString("npc_id"),
                        topic = rs.getString("topic"),
                        question = rs.getString("question"),
                        content = rs.getString("content"),
                        isCanon = rs.getBoolean("is_canon"),
                        source = KnowledgeSource.valueOf(rs.getString("source")),
                        timestamp = rs.getLong("timestamp"),
                        tags = tags
                    )
                    Result.success(entry)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByNpcId(npcId: String): Result<List<KnowledgeEntry>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM knowledge_entries WHERE npc_id = ? ORDER BY timestamp DESC"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                val rs = stmt.executeQuery()

                val entries = mutableListOf<KnowledgeEntry>()
                while (rs.next()) {
                    val tagsStr = rs.getString("tags").orEmpty()
                    val tags = tagsStr.takeIf { it.isNotBlank() }?.let {
                        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
                    } ?: emptyMap()

                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            entityId = rs.getString("npc_id"),
                            topic = rs.getString("topic"),
                            question = rs.getString("question"),
                            content = rs.getString("content"),
                            isCanon = rs.getBoolean("is_canon"),
                            source = KnowledgeSource.valueOf(rs.getString("source")),
                            timestamp = rs.getLong("timestamp"),
                            tags = tags
                        )
                    )
                }
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByCategory(npcId: String, category: String): Result<List<KnowledgeEntry>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT * FROM knowledge_entries
                WHERE npc_id = ? AND category = ?
                ORDER BY timestamp DESC
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.setString(2, category)
                val rs = stmt.executeQuery()

                val entries = mutableListOf<KnowledgeEntry>()
                while (rs.next()) {
                    val tagsStr = rs.getString("tags").orEmpty()
                    val tags = tagsStr.takeIf { it.isNotBlank() }?.let {
                        runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrDefault(emptyMap())
                    } ?: emptyMap()

                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            entityId = rs.getString("npc_id"),
                            topic = rs.getString("topic"),
                            question = rs.getString("question"),
                            content = rs.getString("content"),
                            isCanon = rs.getBoolean("is_canon"),
                            source = KnowledgeSource.valueOf(rs.getString("source")),
                            timestamp = rs.getLong("timestamp"),
                            tags = tags
                        )
                    )
                }
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(id: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM knowledge_entries WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteAllForNpc(npcId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM knowledge_entries WHERE npc_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
