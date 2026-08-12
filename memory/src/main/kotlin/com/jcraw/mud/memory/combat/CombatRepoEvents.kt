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

package com.jcraw.mud.memory.combat

import com.jcraw.mud.core.CombatEvent
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Combat event log for [SQLiteCombatRepository] (MUD-034m pure-move).
 */
internal object CombatRepoEvents {

    fun logEvent(database: CombatDatabase, json: Json, event: CombatEvent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement(insertSql()).use { stmt ->
                bindLog(stmt, json, event)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getEventHistory(
        database: CombatDatabase,
        json: Json,
        entityId: String,
        limit: Int
    ): Result<List<CombatEvent>> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement(historySql()).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, entityId)
                stmt.setInt(3, limit)
                Result.success(decodeEvents(stmt.executeQuery(), json))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun insertSql(): String = """
                INSERT INTO combat_events_log
                (game_time, event_type, source_entity_id, target_entity_id, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
    """.trimIndent()

    private fun historySql(): String = """
                SELECT event_data FROM combat_events_log
                WHERE source_entity_id = ? OR target_entity_id = ?
                ORDER BY game_time DESC
                LIMIT ?
    """.trimIndent()

    private fun bindLog(stmt: java.sql.PreparedStatement, json: Json, event: CombatEvent) {
        stmt.setLong(1, event.gameTime)
        stmt.setString(2, event::class.simpleName ?: "Unknown")
        stmt.setString(3, event.sourceEntityId)
        // Extract target entity ID if applicable
        stmt.setString(4, targetEntityId(event))
        stmt.setString(5, json.encodeToString(event))
        stmt.setLong(6, System.currentTimeMillis())
    }

    private fun decodeEvents(rs: java.sql.ResultSet, json: Json): List<CombatEvent> {
        val events = mutableListOf<CombatEvent>()
        while (rs.next()) {
            val eventData = rs.getString("event_data")
            try {
                events.add(json.decodeFromString(eventData))
            } catch (e: Exception) {
                // Skip malformed events
                continue
            }
        }
        return events
    }

    private fun targetEntityId(event: CombatEvent): String? = when (event) {
        is CombatEvent.DamageDealt -> event.targetEntityId
        is CombatEvent.HealingApplied -> event.targetEntityId
        is CombatEvent.StatusEffectApplied -> event.targetEntityId
        is CombatEvent.AttackMissed -> event.targetEntityId
        is CombatEvent.CriticalHit -> event.targetEntityId
        is CombatEvent.CombatStarted -> event.targetEntityId
        is CombatEvent.CombatEnded -> event.targetEntityId
        else -> null
    }
}
