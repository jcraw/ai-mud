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

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.SocialEvent
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.mud.memory.social.SocialEventRecord
import com.jcraw.mud.memory.social.SocialEventRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persist + log social events for [DispositionManager] (MUD-034n).
 */
internal object DispositionEvents {

    fun applyEvent(
        socialRepo: SocialComponentRepository,
        eventRepo: SocialEventRepository,
        json: Json,
        npc: Entity.NPC,
        event: SocialEvent
    ): Entity.NPC {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
            ?: SocialComponent(personality = "ordinary", traits = emptyList())
        val updated = social.applyDispositionChange(event.dispositionDelta)
        persistSocial(socialRepo, npc.id, updated)
        logEvent(eventRepo, json, npc.id, event)
        return npc.withComponent(updated) as Entity.NPC
    }

    private fun persistSocial(
        socialRepo: SocialComponentRepository,
        npcId: String,
        updated: SocialComponent
    ) {
        val saveResult = socialRepo.save(npcId, updated)
        if (saveResult.isFailure) {
            println("Warning: Failed to save social component for $npcId: ${saveResult.exceptionOrNull()?.message}")
        }
    }

    private fun logEvent(
        eventRepo: SocialEventRepository,
        json: Json,
        npcId: String,
        event: SocialEvent
    ) {
        val eventRecord = toRecord(json, npcId, event)
        val logResult = eventRepo.save(eventRecord)
        if (logResult.isFailure) {
            println("Warning: Failed to log social event for $npcId: ${logResult.exceptionOrNull()?.message}")
        }
    }

    private fun toRecord(json: Json, npcId: String, event: SocialEvent) = SocialEventRecord(
        npcId = npcId,
        eventType = event.eventType,
        dispositionDelta = event.dispositionDelta,
        description = event.description,
        timestamp = System.currentTimeMillis(),
        metadata = event.metadata.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) }
    )

    fun getRecentEvents(
        eventRepo: SocialEventRepository,
        npcId: String,
        limit: Int
    ): List<SocialEventRecord> {
        return eventRepo.findRecentByNpcId(npcId, limit).getOrElse { emptyList() }
    }
}
