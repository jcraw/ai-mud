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
    "UnusedParameter"
)

package com.jcraw.mud.memory.skill

import com.jcraw.mud.core.SkillEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Skill event log path for [SQLiteSkillRepository] (MUD-034m pure-move).
 */
internal object SkillRepoEvents {

    fun logEvent(database: SkillDatabase, json: Json, event: SkillEvent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT INTO skill_events_log
                (entity_id, skill_name, event_type, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, event.entityId)
                stmt.setString(2, event.skillName)
                stmt.setString(3, event.eventType)
                stmt.setString(4, json.encodeToString(SkillEvent.serializer(), event))
                stmt.setLong(5, event.timestamp)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEventHistory(
        database: SkillDatabase,
        json: Json,
        entityId: String,
        skillName: String?,
        limit: Int
    ): Result<List<SkillEvent>> {
        return try {
            val conn = database.getConnection()
            val sql = historySql(skillName)

            conn.prepareStatement(sql).use { stmt ->
                bindHistoryParams(stmt, entityId, skillName, limit)
                val rs = stmt.executeQuery()
                val events = mutableListOf<SkillEvent>()

                while (rs.next()) {
                    val eventData = rs.getString("event_data")
                    val event = json.decodeFromString(SkillEvent.serializer(), eventData)
                    events.add(event)
                }

                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun historySql(skillName: String?): String = if (skillName != null) {
        """
                SELECT * FROM skill_events_log
                WHERE entity_id = ? AND skill_name = ?
                ORDER BY timestamp DESC
                LIMIT ?
        """.trimIndent()
    } else {
        """
                SELECT * FROM skill_events_log
                WHERE entity_id = ?
                ORDER BY timestamp DESC
                LIMIT ?
        """.trimIndent()
    }

    private fun bindHistoryParams(
        stmt: java.sql.PreparedStatement,
        entityId: String,
        skillName: String?,
        limit: Int
    ) {
        stmt.setString(1, entityId)
        if (skillName != null) {
            stmt.setString(2, skillName)
            stmt.setInt(3, limit)
        } else {
            stmt.setInt(2, limit)
        }
    }
}
