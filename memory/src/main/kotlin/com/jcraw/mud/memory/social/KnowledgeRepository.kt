package com.jcraw.mud.memory.social

/**
 * Represents a piece of knowledge an NPC has learned
 */
data class KnowledgeEntry(
    val id: String,
    val npcId: String,
    val content: String,
    val category: String, // "quest", "rumor", "secret", "fact", etc.
    val timestamp: Long,
    val source: String // How they learned it (e.g., "player told", "witnessed", "book")
)

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
                (id, npc_id, content, category, timestamp, source)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entry.id)
                stmt.setString(2, entry.npcId)
                stmt.setString(3, entry.content)
                stmt.setString(4, entry.category)
                stmt.setLong(5, entry.timestamp)
                stmt.setString(6, entry.source)
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
                    val entry = KnowledgeEntry(
                        id = rs.getString("id"),
                        npcId = rs.getString("npc_id"),
                        content = rs.getString("content"),
                        category = rs.getString("category"),
                        timestamp = rs.getLong("timestamp"),
                        source = rs.getString("source")
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
                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            npcId = rs.getString("npc_id"),
                            content = rs.getString("content"),
                            category = rs.getString("category"),
                            timestamp = rs.getLong("timestamp"),
                            source = rs.getString("source")
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
                    entries.add(
                        KnowledgeEntry(
                            id = rs.getString("id"),
                            npcId = rs.getString("npc_id"),
                            content = rs.getString("content"),
                            category = rs.getString("category"),
                            timestamp = rs.getLong("timestamp"),
                            source = rs.getString("source")
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
