package com.jcraw.mud.reasoning

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.EmoteType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.social.SocialComponentRepository
import com.jcraw.mud.memory.social.SocialEventRecord
import com.jcraw.mud.memory.social.SocialEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract: bow → space NPC disposition == old + BOW.delta (5);
 * unknown keyword → Failure, world unchanged. MUD-037 — no live LLM.
 */
class EmoteApplyContractTest {

    private val spaceId = "room_1"
    private val npcId = "guard_1"
    private val startDisposition = 10

    private fun npc(): Entity.NPC = Entity.NPC(
        id = npcId,
        name = "Guard",
        description = "A test guard",
        components = mapOf(
            ComponentType.SOCIAL to SocialComponent(
                disposition = startDisposition,
                personality = "stoic"
            )
        )
    )

    private fun worldWith(npc: Entity.NPC): WorldState {
        val player = PlayerState(id = "player_1", name = "Hero", currentRoomId = spaceId)
        return WorldState(
            players = mapOf(player.id to player),
            spaces = mapOf(
                spaceId to SpacePropertiesComponent(
                    name = "Hall",
                    description = "A hall",
                    entities = listOf(npc.id)
                )
            ),
            entities = mapOf(npc.id to npc)
        )
    }

    private fun handler(): EmoteHandler = EmoteHandler(
        DispositionManager(MemSocialRepo(), MemEventRepo())
    )

    private fun dispositionOf(world: WorldState, id: String): Int? {
        val npc = world.getEntity(id) as? Entity.NPC ?: return null
        return npc.getSocialComponent()?.disposition
    }

    @Test
    fun `bow Success adds BOW disposition delta on space NPC`() {
        val target = npc()
        val world = worldWith(target)
        val expected = startDisposition + EmoteType.BOW.dispositionDelta

        val result = EmoteApply.apply(world, spaceId, target, "bow", handler())

        assertTrue(result is EmoteApply.Result.Success, "expected Success, got $result")
        val success = result as EmoteApply.Result.Success
        assertEquals(EmoteType.BOW.dispositionDelta, success.delta)
        assertEquals(npcId, success.npcId)
        assertEquals(expected, dispositionOf(success.world, npcId))
        val spaceNpc = success.world.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>().single()
        assertEquals(expected, spaceNpc.getSocialComponent()?.disposition)
    }

    @Test
    fun `unknown keyword Failure leaves world disposition unchanged`() {
        val target = npc()
        val world = worldWith(target)
        val entitiesBefore = world.entities

        val result = EmoteApply.apply(world, spaceId, target, "dance", handler())

        assertTrue(result is EmoteApply.Result.Failure)
        val failure = result as EmoteApply.Result.Failure
        assertTrue(failure.message.contains("Unknown emote", ignoreCase = true))
        assertEquals(startDisposition, dispositionOf(world, npcId))
        assertSame(entitiesBefore, world.entities)
        assertEquals(startDisposition, (world.getEntity(npcId) as Entity.NPC).getDisposition())
    }

    private class MemSocialRepo : SocialComponentRepository {
        private val store = mutableMapOf<String, SocialComponent>()
        override fun save(npcId: String, component: SocialComponent) =
            Result.success(Unit).also { store[npcId] = component }
        override fun findByNpcId(npcId: String) = Result.success(store[npcId])
        override fun delete(npcId: String) = Result.success(Unit).also { store.remove(npcId) }
        override fun findAll() = Result.success(store.toMap())
    }

    private class MemEventRepo : SocialEventRepository {
        override fun save(event: SocialEventRecord) = Result.success(1L)
        override fun findByNpcId(npcId: String) = Result.success(emptyList<SocialEventRecord>())
        override fun findRecentByNpcId(npcId: String, limit: Int) =
            Result.success(emptyList<SocialEventRecord>())
        override fun findByEventType(npcId: String, eventType: String) =
            Result.success(emptyList<SocialEventRecord>())
        override fun deleteAllForNpc(npcId: String) = Result.success(Unit)
    }
}
