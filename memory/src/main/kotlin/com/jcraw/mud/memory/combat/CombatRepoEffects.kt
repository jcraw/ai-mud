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

import com.jcraw.mud.core.StatusEffect
import kotlinx.serialization.json.Json

/**
 * Status-effect writes for [SQLiteCombatRepository] (MUD-034m pure-move).
 */
internal object CombatRepoEffects {

    fun applyEffect(
        database: CombatDatabase,
        json: Json,
        entityId: String,
        effect: StatusEffect
    ): Result<Unit> {
        return try {
            // Load current component
            val currentResult = CombatRepoComponents.findByEntityId(database, json, entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no combat component"))
            }

            // Apply effect using component logic and save
            val updated = currentResult.getOrNull()!!.applyStatus(effect)
            CombatRepoComponents.save(database, json, entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun removeEffect(database: CombatDatabase, entityId: String, effectId: Int): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = "DELETE FROM status_effects WHERE entity_id = ? AND id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setInt(2, effectId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Private helper to save status effects to denormalized table
     */
    fun saveStatusEffects(database: CombatDatabase, entityId: String, effects: List<StatusEffect>): Result<Unit> {
        return try {
            val conn = database.getConnection()
            deleteExisting(conn, entityId)
            insertEffects(conn, entityId, effects)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deleteExisting(conn: java.sql.Connection, entityId: String) {
        // First, delete existing effects for this entity
        conn.prepareStatement("DELETE FROM status_effects WHERE entity_id = ?").use { stmt ->
            stmt.setString(1, entityId)
            stmt.executeUpdate()
        }
    }

    private fun insertEffects(conn: java.sql.Connection, entityId: String, effects: List<StatusEffect>) {
        // Then, insert new effects
        val sql = """
                INSERT INTO status_effects
                (entity_id, effect_type, magnitude, duration, source)
                VALUES (?, ?, ?, ?, ?)
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            for (effect in effects) {
                stmt.setString(1, entityId)
                stmt.setString(2, effect.type.name)
                stmt.setInt(3, effect.magnitude)
                stmt.setInt(4, effect.duration)
                stmt.setString(5, effect.source)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }
}
