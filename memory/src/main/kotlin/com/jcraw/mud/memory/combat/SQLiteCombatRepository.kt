package com.jcraw.mud.memory.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.CombatEvent
import com.jcraw.mud.core.StatusEffect
import com.jcraw.mud.core.repository.CombatRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of CombatRepository
 * Manages combat component persistence, status effects, events, and corpses
 */
class SQLiteCombatRepository(
    private val database: CombatDatabase
) : CombatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityId(entityId: String): Result<CombatComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT component_data FROM combat_components WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<CombatComponent>(componentData)
                    Result.success(component)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun save(entityId: String, component: CombatComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO combat_components
                (entity_id, component_data, current_hp, max_hp, action_timer_end, position)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, json.encodeToString(component))
                stmt.setInt(3, component.currentHp)
                stmt.setInt(4, component.maxHp)
                stmt.setLong(5, component.actionTimerEnd)
                stmt.setString(6, component.position.name)
                stmt.executeUpdate()
            }

            // Also update status_effects table
            saveStatusEffects(entityId, component.statusEffects)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // Delete from combat_components
            conn.prepareStatement("DELETE FROM combat_components WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }

            // Delete from status_effects
            conn.prepareStatement("DELETE FROM status_effects WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateHp(entityId: String, newHp: Int): Result<Unit> {
        return try {
            // Load current component
            val currentResult = findByEntityId(entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no combat component"))
            }

            // Update HP and save
            val updated = currentResult.getOrNull()!!.copy(currentHp = newHp)
            save(entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun applyEffect(entityId: String, effect: StatusEffect): Result<Unit> {
        return try {
            // Load current component
            val currentResult = findByEntityId(entityId)
            if (currentResult.isFailure || currentResult.getOrNull() == null) {
                return Result.failure(IllegalStateException("Entity $entityId has no combat component"))
            }

            // Apply effect using component logic and save
            val updated = currentResult.getOrNull()!!.applyStatus(effect)
            save(entityId, updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun removeEffect(entityId: String, effectId: Int): Result<Unit> {
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

    override fun findActiveThreats(roomId: String): Result<List<String>> {
        // TODO: This requires integration with SocialRepository to check dispositions
        // For now, return empty list - will be implemented in Phase 4
        return Result.success(emptyList())
    }

    override fun findCombatantsInRoom(roomId: String): Result<List<String>> {
        // TODO: This requires access to WorldState to determine room occupants
        // For now, return empty list - will be implemented when integrated with game engine
        return Result.success(emptyList())
    }

    override fun logEvent(event: CombatEvent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT INTO combat_events_log
                (game_time, event_type, source_entity_id, target_entity_id, event_data, timestamp)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, event.gameTime)
                stmt.setString(2, event::class.simpleName ?: "Unknown")
                stmt.setString(3, event.sourceEntityId)

                // Extract target entity ID if applicable
                val targetId = when (event) {
                    is CombatEvent.DamageDealt -> event.targetEntityId
                    is CombatEvent.HealingApplied -> event.targetEntityId
                    is CombatEvent.StatusEffectApplied -> event.targetEntityId
                    is CombatEvent.AttackMissed -> event.targetEntityId
                    is CombatEvent.CriticalHit -> event.targetEntityId
                    is CombatEvent.CombatStarted -> event.targetEntityId
                    is CombatEvent.CombatEnded -> event.targetEntityId
                    else -> null
                }
                stmt.setString(4, targetId)

                stmt.setString(5, json.encodeToString(event))
                stmt.setLong(6, System.currentTimeMillis())
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getEventHistory(entityId: String, limit: Int): Result<List<CombatEvent>> {
        return try {
            val conn = database.getConnection()
            val sql = """
                SELECT event_data FROM combat_events_log
                WHERE source_entity_id = ? OR target_entity_id = ?
                ORDER BY game_time DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setString(2, entityId)
                stmt.setInt(3, limit)
                val rs = stmt.executeQuery()

                val events = mutableListOf<CombatEvent>()
                while (rs.next()) {
                    val eventData = rs.getString("event_data")
                    try {
                        val event = json.decodeFromString<CombatEvent>(eventData)
                        events.add(event)
                    } catch (e: Exception) {
                        // Skip malformed events
                        continue
                    }
                }
                Result.success(events)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<Map<String, CombatComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT entity_id, component_data FROM combat_components"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()

                val components = mutableMapOf<String, CombatComponent>()
                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val componentData = rs.getString("component_data")
                    val component = json.decodeFromString<CombatComponent>(componentData)
                    components[entityId] = component
                }
                Result.success(components)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Private helper to save status effects to denormalized table
     */
    private fun saveStatusEffects(entityId: String, effects: List<StatusEffect>): Result<Unit> {
        return try {
            val conn = database.getConnection()

            // First, delete existing effects for this entity
            conn.prepareStatement("DELETE FROM status_effects WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }

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

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
