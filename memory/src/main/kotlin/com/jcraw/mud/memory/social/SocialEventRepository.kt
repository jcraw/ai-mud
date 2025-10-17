package com.jcraw.mud.memory.social

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Represents a persisted social event
 */
data class SocialEventRecord(
    val id: Long = 0,
    val npcId: String,
    val eventType: String,
    val dispositionDelta: Int,
    val description: String,
    val timestamp: Long,
    val metadata: String? = null // JSON-encoded additional data
)

/**
 * Repository for social event history
 */
interface SocialEventRepository {
    fun save(event: SocialEventRecord): Result<Long>
    fun findByNpcId(npcId: String): Result<List<SocialEventRecord>>
    fun findRecentByNpcId(npcId: String, limit: Int): Result<List<SocialEventRecord>>
    fun findByEventType(npcId: String, eventType: String): Result<List<SocialEventRecord>>
    fun deleteAllForNpc(npcId: String): Result<Unit>
}

/**
 * SQLite implementation of SocialEventRepository
 */
class SqliteSocialEventRepository(
    private val database: SocialDatabase
) : SocialEventRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(event: SocialEventRecord): Result<Long> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT INTO social_events
                (npc_id, event_type, disposition_delta, description, timestamp, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, event.npcId)
                stmt.setString(2, event.eventType)
                stmt.setInt(3, event.dispositionDelta)
                stmt.setString(4, event.description)
                stmt.setLong(5, event.timestamp)
                stmt.setString(6, event.metadata)
                stmt.executeUpdate()

                // Get generated ID
                val rs = stmt.generatedKeys
                val id = if (rs.next()) rs.getLong(1) else 0L
                Result.success(id)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByNpcId(npcId: String): Result<List<SocialEventRecord>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM social_events WHERE npc_id = ? ORDER BY timestamp DESC"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                val rs = stmt.executeQuery()

                val events = mutableListOf<SocialEventRecord>()
                while (rs.next()) {
                    events.add(
                        SocialEventRecord(
                            id = rs.getLong("id"),
                            npcId = rs.getString("npc_id"),
                            eventType = rs.getString("event_type"),
                            dispositionDelta = rs.getInt("disposition_delta"),
                            description = rs.getString("description"),
                            timestamp = rs.getLong("timestamp"),
                            metadata = rs.getString("metadata")
                        )
                    )
                }
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findRecentByNpcId(npcId: String, limit: Int): Result<List<SocialEventRecord>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT * FROM social_events
                WHERE npc_id = ?
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.setInt(2, limit)
                val rs = stmt.executeQuery()

                val events = mutableListOf<SocialEventRecord>()
                while (rs.next()) {
                    events.add(
                        SocialEventRecord(
                            id = rs.getLong("id"),
                            npcId = rs.getString("npc_id"),
                            eventType = rs.getString("event_type"),
                            dispositionDelta = rs.getInt("disposition_delta"),
                            description = rs.getString("description"),
                            timestamp = rs.getLong("timestamp"),
                            metadata = rs.getString("metadata")
                        )
                    )
                }
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByEventType(npcId: String, eventType: String): Result<List<SocialEventRecord>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT * FROM social_events
                WHERE npc_id = ? AND event_type = ?
                ORDER BY timestamp DESC
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.setString(2, eventType)
                val rs = stmt.executeQuery()

                val events = mutableListOf<SocialEventRecord>()
                while (rs.next()) {
                    events.add(
                        SocialEventRecord(
                            id = rs.getLong("id"),
                            npcId = rs.getString("npc_id"),
                            eventType = rs.getString("event_type"),
                            dispositionDelta = rs.getInt("disposition_delta"),
                            description = rs.getString("description"),
                            timestamp = rs.getLong("timestamp"),
                            metadata = rs.getString("metadata")
                        )
                    )
                }
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun deleteAllForNpc(npcId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM social_events WHERE npc_id = ?"

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
