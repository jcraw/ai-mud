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
    "UnusedParameter",
    "ForbiddenComment"
)

package com.jcraw.mud.memory.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.CombatEvent
import com.jcraw.mud.core.StatusEffect
import com.jcraw.mud.core.repository.CombatRepository
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of CombatRepository
 * Thin facade — bodies in CombatRepo* extracts (MUD-034m).
 * Stubs findActiveThreats / findCombatantsInRoom stay empty TODOs.
 */
class SQLiteCombatRepository(
    private val database: CombatDatabase
) : CombatRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityId(entityId: String): Result<CombatComponent?> =
        CombatRepoComponents.findByEntityId(database, json, entityId)

    override fun save(entityId: String, component: CombatComponent): Result<Unit> =
        CombatRepoComponents.save(database, json, entityId, component)

    override fun delete(entityId: String): Result<Unit> =
        CombatRepoComponents.delete(database, entityId)

    override fun updateHp(entityId: String, newHp: Int): Result<Unit> =
        CombatRepoComponents.updateHp(database, json, entityId, newHp)

    override fun applyEffect(entityId: String, effect: StatusEffect): Result<Unit> =
        CombatRepoEffects.applyEffect(database, json, entityId, effect)

    override fun removeEffect(entityId: String, effectId: Int): Result<Unit> =
        CombatRepoEffects.removeEffect(database, entityId, effectId)

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

    override fun logEvent(event: CombatEvent): Result<Unit> =
        CombatRepoEvents.logEvent(database, json, event)

    override fun getEventHistory(entityId: String, limit: Int): Result<List<CombatEvent>> =
        CombatRepoEvents.getEventHistory(database, json, entityId, limit)

    override fun findAll(): Result<Map<String, CombatComponent>> =
        CombatRepoComponents.findAll(database, json)
}
