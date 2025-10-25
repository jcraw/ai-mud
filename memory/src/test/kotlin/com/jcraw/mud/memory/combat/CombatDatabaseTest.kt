package com.jcraw.mud.memory.combat

import com.jcraw.mud.core.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CombatDatabaseTest {

    private lateinit var database: CombatDatabase
    private lateinit var repository: SQLiteCombatRepository

    @BeforeAll
    fun setup() {
        database = CombatDatabase(":memory:")
        repository = SQLiteCombatRepository(database)
    }

    @BeforeEach
    fun clearData() {
        database.clearAll()
    }

    @AfterAll
    fun teardown() {
        database.close()
    }

    // ===== CombatComponent Save/Load Tests =====

    @Test
    fun `save and load combat component`() {
        val component = CombatComponent(
            currentHp = 50,
            maxHp = 100,
            actionTimerEnd = 1000L,
            statusEffects = listOf(
                StatusEffect(StatusEffectType.POISON_DOT, magnitude = 5, duration = 3, source = "trap")
            ),
            position = CombatPosition.FRONT
        )

        repository.save("player1", component)

        val result = repository.findByEntityId("player1")
        assertTrue(result.isSuccess)
        val loaded = result.getOrNull()
        assertNotNull(loaded)
        assertEquals(50, loaded.currentHp)
        assertEquals(100, loaded.maxHp)
        assertEquals(1000L, loaded.actionTimerEnd)
        assertEquals(1, loaded.statusEffects.size)
        assertEquals(StatusEffectType.POISON_DOT, loaded.statusEffects[0].type)
        assertEquals(CombatPosition.FRONT, loaded.position)
    }

    @Test
    fun `load non-existent component returns null`() {
        val result = repository.findByEntityId("non-existent")
        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `save component with multiple status effects`() {
        val component = CombatComponent(
            currentHp = 75,
            maxHp = 100,
            statusEffects = listOf(
                StatusEffect(StatusEffectType.POISON_DOT, magnitude = 5, duration = 3, source = "goblin"),
                StatusEffect(StatusEffectType.STRENGTH_BOOST, magnitude = 10, duration = 5, source = "potion"),
                StatusEffect(StatusEffectType.SHIELD, magnitude = 20, duration = 4, source = "spell")
            )
        )

        repository.save("player1", component)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(3, loaded.statusEffects.size)
        assertEquals(StatusEffectType.POISON_DOT, loaded.statusEffects[0].type)
        assertEquals(StatusEffectType.STRENGTH_BOOST, loaded.statusEffects[1].type)
        assertEquals(StatusEffectType.SHIELD, loaded.statusEffects[2].type)
    }

    @Test
    fun `save component with no status effects`() {
        val component = CombatComponent(
            currentHp = 100,
            maxHp = 100,
            statusEffects = emptyList()
        )

        repository.save("player1", component)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(0, loaded.statusEffects.size)
    }

    @Test
    fun `save overwrites existing component`() {
        val component1 = CombatComponent(currentHp = 50, maxHp = 100)
        val component2 = CombatComponent(currentHp = 75, maxHp = 100)

        repository.save("player1", component1)
        repository.save("player1", component2)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(75, loaded.currentHp)
    }

    @Test
    fun `delete component removes it from database`() {
        val component = CombatComponent(currentHp = 50, maxHp = 100)
        repository.save("player1", component)

        repository.delete("player1")

        val result = repository.findByEntityId("player1")
        assertNull(result.getOrNull())
    }

    @Test
    fun `delete also removes status effects`() {
        val component = CombatComponent(
            currentHp = 50,
            maxHp = 100,
            statusEffects = listOf(
                StatusEffect(StatusEffectType.POISON_DOT, magnitude = 5, duration = 3, source = "trap")
            )
        )
        repository.save("player1", component)

        repository.delete("player1")

        // Verify component is gone
        val result = repository.findByEntityId("player1")
        assertNull(result.getOrNull())
    }

    @Test
    fun `find all returns all components`() {
        val component1 = CombatComponent(currentHp = 50, maxHp = 100)
        val component2 = CombatComponent(currentHp = 75, maxHp = 150)
        val component3 = CombatComponent(currentHp = 100, maxHp = 100)

        repository.save("player1", component1)
        repository.save("goblin1", component2)
        repository.save("guard1", component3)

        val result = repository.findAll()
        assertTrue(result.isSuccess)
        val components = result.getOrNull()!!
        assertEquals(3, components.size)
        assertTrue(components.containsKey("player1"))
        assertTrue(components.containsKey("goblin1"))
        assertTrue(components.containsKey("guard1"))
        assertEquals(50, components["player1"]?.currentHp)
        assertEquals(75, components["goblin1"]?.currentHp)
        assertEquals(100, components["guard1"]?.currentHp)
    }

    // ===== HP Update Tests =====

    @Test
    fun `update HP changes only HP value`() {
        val component = CombatComponent(
            currentHp = 100,
            maxHp = 100,
            actionTimerEnd = 500L
        )
        repository.save("player1", component)

        repository.updateHp("player1", 50)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(50, loaded.currentHp)
        assertEquals(100, loaded.maxHp) // Unchanged
        assertEquals(500L, loaded.actionTimerEnd) // Unchanged
    }

    // ===== Status Effect Tests =====

    @Test
    fun `apply effect adds new effect`() {
        val component = CombatComponent(currentHp = 100, maxHp = 100)
        repository.save("player1", component)

        val effect = StatusEffect(StatusEffectType.POISON_DOT, magnitude = 5, duration = 3, source = "trap")
        repository.applyEffect("player1", effect)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(1, loaded.statusEffects.size)
        assertEquals(StatusEffectType.POISON_DOT, loaded.statusEffects[0].type)
    }

    @Test
    fun `apply multiple effects stacks them`() {
        val component = CombatComponent(currentHp = 100, maxHp = 100)
        repository.save("player1", component)

        repository.applyEffect("player1", StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "trap"))
        repository.applyEffect("player1", StatusEffect(StatusEffectType.STRENGTH_BOOST, 10, 5, "potion"))

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(2, loaded.statusEffects.size)
    }

    // ===== Combat Event Logging Tests =====

    @Test
    fun `log damage event stores event in database`() {
        val event = CombatEvent.DamageDealt(
            gameTime = 1000L,
            sourceEntityId = "goblin1",
            targetEntityId = "player1",
            damageAmount = 15,
            damageType = DamageType.PHYSICAL,
            finalHp = 85,
            wasKilled = false,
            skillsUsed = listOf("Sword Fighting", "Strength")
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("player1", limit = 10)
        assertTrue(history.isSuccess)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.DamageDealt)
        val damageEvent = events[0] as CombatEvent.DamageDealt
        assertEquals("goblin1", damageEvent.sourceEntityId)
        assertEquals("player1", damageEvent.targetEntityId)
        assertEquals(15, damageEvent.damageAmount)
    }

    @Test
    fun `log healing event stores event in database`() {
        val event = CombatEvent.HealingApplied(
            gameTime = 2000L,
            sourceEntityId = "player1",
            targetEntityId = "player1",
            healAmount = 25,
            finalHp = 100,
            wasFromEffect = false
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.HealingApplied)
    }

    @Test
    fun `log status effect applied event`() {
        val effect = StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "trap")
        val event = CombatEvent.StatusEffectApplied(
            gameTime = 1500L,
            sourceEntityId = "trap",
            targetEntityId = "player1",
            effect = effect,
            wasStacked = false,
            wasReplaced = false
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.StatusEffectApplied)
    }

    @Test
    fun `get event history returns events in reverse chronological order`() {
        val event1 = CombatEvent.DamageDealt(100L, "goblin1", "player1", 10, DamageType.PHYSICAL, 90, false)
        val event2 = CombatEvent.HealingApplied(200L, "player1", "player1", 20, 100, false)
        val event3 = CombatEvent.DamageDealt(300L, "goblin1", "player1", 15, DamageType.PHYSICAL, 85, false)

        repository.logEvent(event1)
        repository.logEvent(event2)
        repository.logEvent(event3)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(3, events.size)
        assertEquals(300L, events[0].gameTime) // Most recent first
        assertEquals(200L, events[1].gameTime)
        assertEquals(100L, events[2].gameTime)
    }

    @Test
    fun `get event history limits results`() {
        repeat(10) { i ->
            val event = CombatEvent.DamageDealt(
                gameTime = i.toLong() * 100,
                sourceEntityId = "goblin1",
                targetEntityId = "player1",
                damageAmount = 10,
                damageType = DamageType.PHYSICAL,
                finalHp = 100 - (i * 10),
                wasKilled = false
            )
            repository.logEvent(event)
        }

        val history = repository.getEventHistory("player1", limit = 5)
        val events = history.getOrNull()!!
        assertEquals(5, events.size)
    }

    @Test
    fun `get event history includes source and target events`() {
        val event1 = CombatEvent.DamageDealt(100L, "player1", "goblin1", 20, DamageType.PHYSICAL, 30, false)
        val event2 = CombatEvent.DamageDealt(200L, "goblin1", "player1", 10, DamageType.PHYSICAL, 90, false)

        repository.logEvent(event1)
        repository.logEvent(event2)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(2, events.size) // Player1 is both attacker and defender
    }

    @Test
    fun `log combat started event`() {
        val event = CombatEvent.CombatStarted(
            gameTime = 1000L,
            sourceEntityId = "player1",
            targetEntityId = "goblin1",
            roomId = "room_throne",
            wasPlayerInitiated = true
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.CombatStarted)
    }

    @Test
    fun `log combat ended event`() {
        val event = CombatEvent.CombatEnded(
            gameTime = 5000L,
            sourceEntityId = "player1",
            targetEntityId = "goblin1",
            roomId = "room_throne",
            reason = CombatEndReason.DEATH
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("player1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.CombatEnded)
    }

    @Test
    fun `log entity died event`() {
        val event = CombatEvent.EntityDied(
            gameTime = 3000L,
            sourceEntityId = "goblin1",
            killerEntityId = "player1",
            wasPlayer = false
        )

        repository.logEvent(event)

        val history = repository.getEventHistory("goblin1", limit = 10)
        val events = history.getOrNull()!!
        assertEquals(1, events.size)
        assertTrue(events[0] is CombatEvent.EntityDied)
    }

    // ===== Complex Integration Tests =====

    @Test
    fun `save component with complex status effects and retrieve`() {
        val component = CombatComponent(
            currentHp = 45,
            maxHp = 120,
            actionTimerEnd = 2500L,
            statusEffects = listOf(
                StatusEffect(StatusEffectType.POISON_DOT, magnitude = 8, duration = 5, source = "spider"),
                StatusEffect(StatusEffectType.STRENGTH_BOOST, magnitude = 15, duration = 10, source = "potion"),
                StatusEffect(StatusEffectType.REGENERATION, magnitude = 5, duration = 8, source = "ring"),
                StatusEffect(StatusEffectType.SLOW, magnitude = 30, duration = 3, source = "spell")
            ),
            position = CombatPosition.BACK
        )

        repository.save("player1", component)

        val result = repository.findByEntityId("player1")
        val loaded = result.getOrNull()!!
        assertEquals(45, loaded.currentHp)
        assertEquals(120, loaded.maxHp)
        assertEquals(2500L, loaded.actionTimerEnd)
        assertEquals(4, loaded.statusEffects.size)
        assertEquals(CombatPosition.BACK, loaded.position)

        // Verify each effect
        assertEquals(StatusEffectType.POISON_DOT, loaded.statusEffects[0].type)
        assertEquals(8, loaded.statusEffects[0].magnitude)
        assertEquals(5, loaded.statusEffects[0].duration)
        assertEquals("spider", loaded.statusEffects[0].source)
    }

    @Test
    fun `full combat flow - save component, update HP, log events, retrieve history`() {
        // Setup: Create combat component
        val component = CombatComponent(currentHp = 100, maxHp = 100)
        repository.save("player1", component)

        // Combat starts
        repository.logEvent(
            CombatEvent.CombatStarted(
                gameTime = 0L,
                sourceEntityId = "player1",
                targetEntityId = "goblin1",
                roomId = "room_corridor",
                wasPlayerInitiated = true
            )
        )

        // Player takes damage
        repository.updateHp("player1", 85)
        repository.logEvent(
            CombatEvent.DamageDealt(
                gameTime = 100L,
                sourceEntityId = "goblin1",
                targetEntityId = "player1",
                damageAmount = 15,
                damageType = DamageType.PHYSICAL,
                finalHp = 85,
                wasKilled = false
            )
        )

        // Player applies poison
        val poison = StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "player1")
        repository.applyEffect("player1", poison)
        repository.logEvent(
            CombatEvent.StatusEffectApplied(
                gameTime = 200L,
                sourceEntityId = "player1",
                targetEntityId = "player1",
                effect = poison,
                wasStacked = false,
                wasReplaced = false
            )
        )

        // Verify final state
        val finalComponent = repository.findByEntityId("player1").getOrNull()!!
        assertEquals(85, finalComponent.currentHp)
        assertEquals(1, finalComponent.statusEffects.size)

        // Verify event history
        val history = repository.getEventHistory("player1", limit = 10).getOrNull()!!
        assertEquals(3, history.size)
        assertTrue(history[0] is CombatEvent.StatusEffectApplied)
        assertTrue(history[1] is CombatEvent.DamageDealt)
        assertTrue(history[2] is CombatEvent.CombatStarted)
    }
}
