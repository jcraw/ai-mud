package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.DamageType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract: Hit apply writes defender combat currentHp exactly; missing defender
 * (or AttackResolver Failure) leaves world unchanged. MUD-037 — no live LLM; no RNG.
 */
class CombatHitContractTest {

    private val spaceId = "room_1"
    private val npcId = "goblin_1"
    private val targetHp = 7

    private fun npcWithHp(hp: Int): Entity.NPC = Entity.NPC(
        id = npcId,
        name = "Goblin",
        description = "A test goblin",
        components = mapOf(
            ComponentType.COMBAT to CombatComponent(currentHp = hp, maxHp = 50)
        )
    )

    private fun worldWith(npc: Entity.NPC): WorldState {
        val player = PlayerState(id = "player_1", name = "Hero", currentRoomId = spaceId)
        return WorldState(
            players = mapOf(player.id to player),
            spaces = mapOf(
                spaceId to SpacePropertiesComponent(
                    name = "Arena",
                    description = "A test arena",
                    entities = listOf(npc.id)
                )
            ),
            entities = mapOf(npc.id to npc)
        )
    }

    private fun hit(defenderCombat: CombatComponent): AttackResult.Hit = AttackResult.hit(
        attackerId = "player_1",
        defenderId = npcId,
        damage = 10,
        damageType = DamageType.PHYSICAL,
        attackRoll = 15,
        defenseRoll = 8,
        attackerSkillsUsed = emptyList(),
        defenderSkillsUsed = emptyList(),
        defenseOutcome = DefenseOutcome.OVERWHELMED,
        updatedDefenderCombat = defenderCombat
    )

    private fun combatHp(world: WorldState, id: String): Int? {
        val npc = world.getEntity(id) as? Entity.NPC ?: return null
        return npc.getComponent<CombatComponent>(ComponentType.COMBAT)?.currentHp
    }

    @Test
    fun `hit apply writes defender combat currentHp exactly`() {
        val npc = npcWithHp(40)
        val world = worldWith(npc)
        val updated = CombatComponent(currentHp = targetHp, maxHp = 50)

        val result = CombatHitApply.apply(world, spaceId, npc, hit(updated))

        assertTrue(result is CombatHitApply.Result.Success, "expected Success, got $result")
        val success = result as CombatHitApply.Result.Success
        assertEquals(targetHp, combatHp(success.world, npcId))
        val spaceNpc = success.world.getEntitiesInSpace(spaceId).filterIsInstance<Entity.NPC>().single()
        assertEquals(targetHp, spaceNpc.getComponent<CombatComponent>(ComponentType.COMBAT)?.currentHp)
    }

    @Test
    fun `missing defender Failure leaves world unchanged`() {
        val npc = npcWithHp(40)
        val world = worldWith(npc)
        val entitiesBefore = world.entities
        val ghost = npc.copy(id = "ghost_1")
        val updated = CombatComponent(currentHp = targetHp, maxHp = 50)

        val result = CombatHitApply.apply(world, spaceId, ghost, hit(updated))

        assertTrue(result is CombatHitApply.Result.Failure)
        assertEquals("Invalid attacker or defender", (result as CombatHitApply.Result.Failure).message)
        assertEquals(40, combatHp(world, npcId))
        assertEquals(setOf(npcId), world.entities.keys)
        assertSame(entitiesBefore, world.entities)
    }

    @Test
    fun `AttackResolver Failure leaves world structurally equal`() {
        val npc = npcWithHp(40)
        val world = worldWith(npc)
        val entitiesBefore = world.entities

        val result = CombatHitApply.apply(AttackResult.failure("Invalid attacker or defender"))

        assertTrue(result is CombatHitApply.Result.Failure)
        assertEquals("Invalid attacker or defender", (result as CombatHitApply.Result.Failure).message)
        assertEquals(40, combatHp(world, npcId))
        assertSame(entitiesBefore, world.entities)
        assertTrue(world.getEntitiesInSpace(spaceId).any { it.id == npcId })
    }
}
