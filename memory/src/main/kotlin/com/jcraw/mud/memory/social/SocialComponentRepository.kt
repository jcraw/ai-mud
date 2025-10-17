package com.jcraw.mud.memory.social

import com.jcraw.mud.core.SocialComponent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for SocialComponent persistence
 */
interface SocialComponentRepository {
    fun save(npcId: String, component: SocialComponent): Result<Unit>
    fun findByNpcId(npcId: String): Result<SocialComponent?>
    fun delete(npcId: String): Result<Unit>
    fun findAll(): Result<Map<String, SocialComponent>>
}

/**
 * SQLite implementation of SocialComponentRepository
 */
class SqliteSocialComponentRepository(
    private val database: SocialDatabase
) : SocialComponentRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(npcId: String, component: SocialComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO social_components
                (npc_id, disposition, personality, traits, conversation_count, last_interaction_time)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.setInt(2, component.disposition)
                stmt.setString(3, component.personality)
                stmt.setString(4, json.encodeToString(component.traits))
                stmt.setInt(5, component.conversationCount)
                stmt.setLong(6, component.lastInteractionTime)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByNpcId(npcId: String): Result<SocialComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM social_components WHERE npc_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val component = SocialComponent(
                        disposition = rs.getInt("disposition"),
                        personality = rs.getString("personality"),
                        traits = json.decodeFromString(rs.getString("traits")),
                        conversationCount = rs.getInt("conversation_count"),
                        lastInteractionTime = rs.getLong("last_interaction_time")
                        // knowledgeEntries handled separately via KnowledgeRepository
                    )
                    Result.success(component)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(npcId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM social_components WHERE npc_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, npcId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<Map<String, SocialComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM social_components"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()

                val components = mutableMapOf<String, SocialComponent>()
                while (rs.next()) {
                    val npcId = rs.getString("npc_id")
                    val component = SocialComponent(
                        disposition = rs.getInt("disposition"),
                        personality = rs.getString("personality"),
                        traits = json.decodeFromString(rs.getString("traits")),
                        conversationCount = rs.getInt("conversation_count"),
                        lastInteractionTime = rs.getLong("last_interaction_time")
                    )
                    components[npcId] = component
                }
                Result.success(components)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
