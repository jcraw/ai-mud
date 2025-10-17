package com.jcraw.mud.memory.social

import com.jcraw.mud.core.KnowledgeEntry
import com.jcraw.mud.core.KnowledgeSource

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

    override fun save(entry: KnowledgeEntry): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO knowledge_entries
                (id, npc_id, content, category, timestamp, source, is_canon, tags)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            // Extract first tag value as category for backward compatibility
            val category = entry.tags["category"] ?: "general"
            val tagsJson = entry.tags.entries.joinToString(",") { "${it.key}:${it.value}" }

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entry.id)
                stmt.setString(2, entry.entityId)
                stmt.setString(3, entry.content)
                stmt.setString(4, category)
                stmt.setLong(5, entry.timestamp)
                stmt.setString(6, entry.source.name)
                stmt.setBoolean(7, entry.isCanon)
                stmt.setString(8, tagsJson)
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
                    val tagsStr = rs.getString("tags") ?: ""
                    val tags = if (tagsStr.isNotEmpty()) {
                        tagsStr.split(",").associate {
                            val parts = it.split(":")
                            parts[0] to (parts.getOrNull(1) ?: "")
                        }
                    } else {
                        emptyMap()
                    }

                    val entry = KnowledgeEntry(
                        id = rs.getString("id"),
                        entityId = rs.getString("npc_id"),
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
                    val tagsStr = rs.getString("tags") ?: ""
                    val tags = if (tagsStr.isNotEmpty()) {
                        tagsStr.split(",").associate {
                            val parts = it.split(":")
                            parts[0] to (parts.getOrNull(1) ?: "")
                        }
                    } else {
                        emptyMap()
                    }

                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            entityId = rs.getString("npc_id"),
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
                    val tagsStr = rs.getString("tags") ?: ""
                    val tags = if (tagsStr.isNotEmpty()) {
                        tagsStr.split(",").associate {
                            val parts = it.split(":")
                            parts[0] to (parts.getOrNull(1) ?: "")
                        }
                    } else {
                        emptyMap()
                    }

                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            entityId = rs.getString("npc_id"),
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
