package com.jcraw.mud.reasoning.death

import com.jcraw.mud.core.CorpseData
import com.jcraw.mud.core.PlayerId
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.repository.CorpseRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerRespawnServiceTest {

    private val player = PlayerState(
        id = "player",
        name = "Ada",
        currentRoomId = "start",
        inventory = emptyList(),
        equippedWeapon = null,
        equippedArmor = null
    )

    private fun baseWorld(gameTime: Long = 10L): WorldState {
        return WorldState(
            graphNodes = emptyMap(),
            spaces = emptyMap(),
            chunks = emptyMap(),
            entities = emptyMap(),
            players = mapOf(player.id to player),
            gameTime = gameTime,
            gameProperties = mapOf("starting_space" to "start")
        )
    }

    @Test
    fun `createPendingRespawn stores corpse and returns pending token`() {
        val repo = FakeCorpseRepository()
        val service = PlayerRespawnService(repo)

        val pending = service.createPendingRespawn(baseWorld(), player.id).getOrThrow()

        assertEquals(player.id, pending.playerId)
        assertEquals(1, repo.corpses.size, "Corpse should be persisted")
        assertNotNull(repo.corpses[pending.deathResult.corpseId])
    }

    @Test
    fun `completeRespawn replaces player with new avatar`() {
        val repo = FakeCorpseRepository()
        val service = PlayerRespawnService(repo)
        val world = baseWorld()
        val pending = service.createPendingRespawn(world, player.id).getOrThrow()

        val outcome = service.completeRespawn(world, pending, "Nova").getOrThrow()

        assertEquals("Nova", outcome.player.name)
        assertEquals("start", outcome.player.currentRoomId)
        assertTrue(outcome.respawnMessage.contains("Corpse"), "Message should mention corpse retrieval")
    }

    private class FakeCorpseRepository : CorpseRepository {
        val corpses = mutableMapOf<String, CorpseData>()

        override fun save(corpse: CorpseData): Result<Unit> {
            corpses[corpse.id] = corpse
            return Result.success(Unit)
        }

        override fun findById(id: String): Result<CorpseData?> = Result.success(corpses[id])

        override fun findByPlayerId(playerId: PlayerId): Result<List<CorpseData>> =
            Result.success(corpses.values.filter { it.playerId == playerId })

        override fun findBySpaceId(spaceId: String): Result<List<CorpseData>> =
            Result.success(corpses.values.filter { it.spaceId == spaceId })

        override fun findDecayed(currentTime: Long): Result<List<CorpseData>> =
            Result.success(emptyList())

        override fun markLooted(corpseId: String): Result<Unit> {
            corpses[corpseId]?.let { corpses[corpseId] = it.markLooted() }
            return Result.success(Unit)
        }

        override fun delete(id: String): Result<Unit> {
            corpses.remove(id)
            return Result.success(Unit)
        }

        override fun deleteBySpaceId(spaceId: String): Result<Unit> {
            corpses.entries.removeIf { it.value.spaceId == spaceId }
            return Result.success(Unit)
        }

        override fun getAll(): Result<Map<String, CorpseData>> = Result.success(corpses)
    }
}
