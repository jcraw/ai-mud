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
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.repository.SkillRepository
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of SkillRepository
 * Thin facade — bodies in SkillRepo* extracts (MUD-034m).
 */
class SQLiteSkillRepository(
    private val database: SkillDatabase
) : SkillRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityAndSkill(entityId: String, skillName: String): Result<SkillState?> =
        SkillRepoQueries.findByEntityAndSkill(database, json, entityId, skillName)

    override fun findByEntityId(entityId: String): Result<Map<String, SkillState>> =
        SkillRepoQueries.findByEntityId(database, json, entityId)

    override fun findByTag(tag: String): Result<Map<Pair<String, String>, SkillState>> =
        SkillRepoQueries.findByTag(database, json, tag)

    override fun save(entityId: String, skillName: String, skillState: SkillState): Result<Unit> =
        SkillRepoWrites.save(database, json, entityId, skillName, skillState)

    override fun updateXp(entityId: String, skillName: String, newXp: Long, newLevel: Int): Result<Unit> =
        SkillRepoWrites.updateXp(database, entityId, skillName, newXp, newLevel)

    override fun unlockSkill(entityId: String, skillName: String): Result<Unit> =
        SkillRepoWrites.unlockSkill(database, entityId, skillName)

    override fun delete(entityId: String, skillName: String): Result<Unit> =
        SkillRepoWrites.delete(database, entityId, skillName)

    override fun deleteAllForEntity(entityId: String): Result<Unit> =
        SkillRepoWrites.deleteAllForEntity(database, entityId)

    override fun logEvent(event: SkillEvent): Result<Unit> =
        SkillRepoEvents.logEvent(database, json, event)

    override fun getEventHistory(
        entityId: String,
        skillName: String?,
        limit: Int
    ): Result<List<SkillEvent>> =
        SkillRepoEvents.getEventHistory(database, json, entityId, skillName, limit)
}
