package com.jcraw.mud.core

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CombatComponent calculations and behavior
 * Focus on boundary conditions, stacking rules, and state transitions
 */
class CombatComponentTest {

    @Nested
    inner class MaxHpCalculation {

        @Test
        fun `calculateMaxHp returns base HP with no skills`() {
            val maxHp = CombatComponent.calculateMaxHp(skills = null, itemHpBonus = 0)
            assertEquals(10, maxHp)
        }

        @Test
        fun `calculateMaxHp applies skill formula correctly`() {
            val skills = SkillComponent(
                skills = mapOf(
                    "Vitality" to SkillState(level = 10, xp = 0L, unlocked = true),
                    "Endurance" to SkillState(level = 5, xp = 0L, unlocked = true),
                    "Constitution" to SkillState(level = 3, xp = 0L, unlocked = true)
                )
            )

            // Base 10 + (Vit*5=50) + (End*3=15) + (Con*2=6) = 81
            val maxHp = CombatComponent.calculateMaxHp(skills, itemHpBonus = 0)
            assertEquals(81, maxHp)
        }

        @Test
        fun `calculateMaxHp includes item bonuses`() {
            val skills = SkillComponent(
                skills = mapOf("Vitality" to SkillState(level = 5, xp = 0L, unlocked = true))
            )

            // Base 10 + (Vit*5=25) + itemBonus 15 = 50
            val maxHp = CombatComponent.calculateMaxHp(skills, itemHpBonus = 15)
            assertEquals(50, maxHp)
        }

        @Test
        fun `calculateMaxHp enforces minimum of 10`() {
            // Even with negative item bonus, should be clamped to 10
            val maxHp = CombatComponent.calculateMaxHp(skills = null, itemHpBonus = -50)
            assertEquals(10, maxHp)
        }

        @Test
        fun `create initializes with full HP`() {
            val skills = SkillComponent(
                skills = mapOf("Vitality" to SkillState(level = 10, xp = 0L, unlocked = true))
            )

            val combat = CombatComponent.create(skills)

            assertEquals(combat.maxHp, combat.currentHp)
            assertEquals(60, combat.maxHp) // Base 10 + Vit*5 = 60
        }
    }

    @Nested
    inner class DamageAndHealing {

        @Test
        fun `applyDamage reduces current HP`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            val damaged = combat.applyDamage(20)

            assertEquals(30, damaged.currentHp)
            assertEquals(100, damaged.maxHp)
        }

        @Test
        fun `applyDamage does not reduce HP below 0`() {
            val combat = CombatComponent(currentHp = 10, maxHp = 100)

            val damaged = combat.applyDamage(50)

            assertEquals(0, damaged.currentHp)
        }

        @Test
        fun `heal increases current HP`() {
            val combat = CombatComponent(currentHp = 30, maxHp = 100)

            val healed = combat.heal(20)

            assertEquals(50, healed.currentHp)
            assertEquals(100, healed.maxHp)
        }

        @Test
        fun `heal does not exceed max HP`() {
            val combat = CombatComponent(currentHp = 90, maxHp = 100)

            val healed = combat.heal(50)

            assertEquals(100, healed.currentHp)
        }

        @Test
        fun `heal on full HP has no effect`() {
            val combat = CombatComponent(currentHp = 100, maxHp = 100)

            val healed = combat.heal(20)

            assertEquals(100, healed.currentHp)
        }
    }

    @Nested
    inner class TimerManagement {

        @Test
        fun `advanceTimer sets correct action time`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            val advanced = combat.advanceTimer(cost = 100L, currentGameTime = 1000L)

            assertEquals(1100L, advanced.actionTimerEnd)
        }

        @Test
        fun `canAct returns true when timer elapsed`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                actionTimerEnd = 1000L
            )

            assertTrue(combat.canAct(1000L))
            assertTrue(combat.canAct(1500L))
        }

        @Test
        fun `canAct returns false when timer not elapsed`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                actionTimerEnd = 1000L
            )

            assertFalse(combat.canAct(999L))
            assertFalse(combat.canAct(500L))
        }

        @Test
        fun `new CombatComponent can act immediately`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            assertTrue(combat.canAct(0L))
            assertTrue(combat.canAct(100L))
        }
    }

    @Nested
    inner class StatusEffectApplication {

        @Test
        fun `applyStatus adds effect when none exists`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)
            val poison = StatusEffect(
                type = StatusEffectType.POISON_DOT,
                magnitude = 5,
                duration = 3,
                source = "enemy-1"
            )

            val updated = combat.applyStatus(poison)

            assertEquals(1, updated.statusEffects.size)
            assertTrue(updated.hasStatusEffect(StatusEffectType.POISON_DOT))
        }

        @Test
        fun `applyStatus replaces DOT if higher magnitude`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 3, duration = 5, source = "old")
                )
            )

            val strongerPoison = StatusEffect(
                type = StatusEffectType.POISON_DOT,
                magnitude = 8,
                duration = 3,
                source = "new"
            )

            val updated = combat.applyStatus(strongerPoison)

            assertEquals(1, updated.statusEffects.size)
            assertEquals(8, updated.statusEffects[0].magnitude)
            assertEquals("new", updated.statusEffects[0].source)
        }

        @Test
        fun `applyStatus keeps DOT if new magnitude is lower`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 10, duration = 5, source = "strong")
                )
            )

            val weakerPoison = StatusEffect(
                type = StatusEffectType.POISON_DOT,
                magnitude = 3,
                duration = 10,
                source = "weak"
            )

            val updated = combat.applyStatus(weakerPoison)

            assertEquals(1, updated.statusEffects.size)
            assertEquals(10, updated.statusEffects[0].magnitude)
            assertEquals("strong", updated.statusEffects[0].source)
        }

        @Test
        fun `applyStatus stacks buffs up to cap of 3`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            val buff1 = StatusEffect(StatusEffectType.STRENGTH_BOOST, 5, 10, "s1")
            val buff2 = StatusEffect(StatusEffectType.STRENGTH_BOOST, 5, 10, "s2")
            val buff3 = StatusEffect(StatusEffectType.STRENGTH_BOOST, 5, 10, "s3")
            val buff4 = StatusEffect(StatusEffectType.STRENGTH_BOOST, 5, 10, "s4")

            val updated = combat
                .applyStatus(buff1)
                .applyStatus(buff2)
                .applyStatus(buff3)
                .applyStatus(buff4)

            // Should only have 3 buffs
            assertEquals(3, updated.statusEffects.size)
            assertEquals(15, updated.getStatusEffectMagnitude(StatusEffectType.STRENGTH_BOOST))
        }

        @Test
        fun `applyStatus replaces single-instance effects`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.SLOW, magnitude = 30, duration = 5, source = "old")
                )
            )

            val newSlow = StatusEffect(StatusEffectType.SLOW, magnitude = 50, duration = 3, source = "new")

            val updated = combat.applyStatus(newSlow)

            assertEquals(1, updated.statusEffects.size)
            assertEquals(50, updated.statusEffects[0].magnitude)
            assertEquals("new", updated.statusEffects[0].source)
        }
    }

    @Nested
    inner class StatusEffectRemoval {

        @Test
        fun `removeStatus removes all effects of type`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "e1"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, 10, 5, "e2"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, 10, 5, "e3")
                )
            )

            val updated = combat.removeStatus(StatusEffectType.STRENGTH_BOOST)

            assertEquals(1, updated.statusEffects.size)
            assertTrue(updated.hasStatusEffect(StatusEffectType.POISON_DOT))
            assertFalse(updated.hasStatusEffect(StatusEffectType.STRENGTH_BOOST))
        }

        @Test
        fun `removeStatus on absent type has no effect`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "e1"))
            )

            val updated = combat.removeStatus(StatusEffectType.SLOW)

            assertEquals(1, updated.statusEffects.size)
        }
    }

    @Nested
    inner class EffectTicking {

        @Test
        fun `tickEffects applies DOT damage`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 8, duration = 3, source = "e1")
                )
            )

            val (updated, applications) = combat.tickEffects(gameTime = 1000L)

            assertEquals(42, updated.currentHp) // 50 - 8
            assertEquals(1, applications.size)
            assertEquals(EffectResult.DAMAGE, applications[0].result)
            assertEquals(8, applications[0].magnitude)
        }

        @Test
        fun `tickEffects applies regeneration healing`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.REGENERATION, magnitude = 5, duration = 3, source = "e1")
                )
            )

            val (updated, applications) = combat.tickEffects(gameTime = 1000L)

            assertEquals(55, updated.currentHp) // 50 + 5
            assertEquals(1, applications.size)
            assertEquals(EffectResult.HEALING, applications[0].result)
        }

        @Test
        fun `tickEffects decrements duration and removes expired`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 5, duration = 1, source = "e1"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, magnitude = 10, duration = 5, source = "e2")
                )
            )

            val (updated, _) = combat.tickEffects(gameTime = 1000L)

            // Poison should be removed (duration was 1), strength should remain (duration 5 -> 4)
            assertEquals(1, updated.statusEffects.size)
            assertTrue(updated.hasStatusEffect(StatusEffectType.STRENGTH_BOOST))
            assertFalse(updated.hasStatusEffect(StatusEffectType.POISON_DOT))
            assertEquals(4, updated.statusEffects[0].duration)
        }

        @Test
        fun `tickEffects processes multiple effects`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 3, duration = 2, source = "e1"),
                    StatusEffect(StatusEffectType.REGENERATION, magnitude = 5, duration = 2, source = "e2"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, magnitude = 10, duration = 2, source = "e3")
                )
            )

            val (updated, applications) = combat.tickEffects(gameTime = 1000L)

            // HP: 50 - 3 (poison) + 5 (regen) = 52
            assertEquals(52, updated.currentHp)
            assertEquals(3, applications.size)

            // All effects should have decremented duration
            assertTrue(updated.statusEffects.all { it.duration == 1 })
        }

        @Test
        fun `tickEffects respects HP boundaries`() {
            val combat = CombatComponent(
                currentHp = 3,
                maxHp = 50,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, magnitude = 100, duration = 2, source = "e1")
                )
            )

            val (updated, _) = combat.tickEffects(gameTime = 1000L)

            assertEquals(0, updated.currentHp) // Should not go below 0
        }
    }

    @Nested
    inner class StatusEffectQueries {

        @Test
        fun `hasStatusEffect returns true when effect present`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "e1")
                )
            )

            assertTrue(combat.hasStatusEffect(StatusEffectType.POISON_DOT))
        }

        @Test
        fun `hasStatusEffect returns false when effect absent`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            assertFalse(combat.hasStatusEffect(StatusEffectType.POISON_DOT))
        }

        @Test
        fun `getStatusEffectMagnitude sums all matching effects`() {
            val combat = CombatComponent(
                currentHp = 50,
                maxHp = 100,
                statusEffects = listOf(
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, 5, 3, "s1"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, 7, 3, "s2"),
                    StatusEffect(StatusEffectType.STRENGTH_BOOST, 3, 3, "s3")
                )
            )

            assertEquals(15, combat.getStatusEffectMagnitude(StatusEffectType.STRENGTH_BOOST))
        }

        @Test
        fun `getStatusEffectMagnitude returns 0 when no effects`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            assertEquals(0, combat.getStatusEffectMagnitude(StatusEffectType.STRENGTH_BOOST))
        }
    }

    @Nested
    inner class HealthState {

        @Test
        fun `isAlive returns true when HP greater than 0`() {
            val combat = CombatComponent(currentHp = 1, maxHp = 100)
            assertTrue(combat.isAlive())
        }

        @Test
        fun `isAlive returns false when HP is 0`() {
            val combat = CombatComponent(currentHp = 0, maxHp = 100)
            assertFalse(combat.isAlive())
        }

        @Test
        fun `isDead returns true when HP is 0`() {
            val combat = CombatComponent(currentHp = 0, maxHp = 100)
            assertTrue(combat.isDead())
        }

        @Test
        fun `isDead returns false when HP greater than 0`() {
            val combat = CombatComponent(currentHp = 1, maxHp = 100)
            assertFalse(combat.isDead())
        }

        @Test
        fun `getHpPercentage calculates correctly`() {
            val combat = CombatComponent(currentHp = 75, maxHp = 100)
            assertEquals(75, combat.getHpPercentage())
        }

        @Test
        fun `getHpPercentage handles fractional percentages`() {
            val combat = CombatComponent(currentHp = 33, maxHp = 100)
            assertEquals(33, combat.getHpPercentage())
        }

        @Test
        fun `getHpPercentage returns 0 when max HP is 0`() {
            val combat = CombatComponent(currentHp = 0, maxHp = 0)
            assertEquals(0, combat.getHpPercentage())
        }
    }

    @Nested
    inner class MaxHpUpdate {

        @Test
        fun `updateMaxHp scales current HP proportionally`() {
            val combat = CombatComponent(currentHp = 50, maxHp = 100)

            val updated = combat.updateMaxHp(200)

            assertEquals(200, updated.maxHp)
            assertEquals(100, updated.currentHp) // 50% of 200
        }

        @Test
        fun `updateMaxHp maintains percentage when decreasing`() {
            val combat = CombatComponent(currentHp = 75, maxHp = 100)

            val updated = combat.updateMaxHp(50)

            assertEquals(50, updated.maxHp)
            assertEquals(37, updated.currentHp) // 75% of 50 = 37.5 -> 37
        }

        @Test
        fun `updateMaxHp enforces minimum of 1 HP`() {
            val combat = CombatComponent(currentHp = 1, maxHp = 1000)

            val updated = combat.updateMaxHp(10)

            assertEquals(10, updated.maxHp)
            assertEquals(1, updated.currentHp) // Minimum 1 HP
        }

        @Test
        fun `updateMaxHp caps current HP at new max`() {
            val combat = CombatComponent(currentHp = 100, maxHp = 100)

            val updated = combat.updateMaxHp(50)

            assertEquals(50, updated.maxHp)
            assertEquals(50, updated.currentHp) // Capped at new max
        }
    }

    @Nested
    inner class Immutability {

        @Test
        fun `applyDamage returns new instance`() {
            val original = CombatComponent(currentHp = 50, maxHp = 100)
            val damaged = original.applyDamage(10)

            assertEquals(50, original.currentHp)
            assertEquals(40, damaged.currentHp)
        }

        @Test
        fun `heal returns new instance`() {
            val original = CombatComponent(currentHp = 50, maxHp = 100)
            val healed = original.heal(10)

            assertEquals(50, original.currentHp)
            assertEquals(60, healed.currentHp)
        }

        @Test
        fun `applyStatus returns new instance`() {
            val original = CombatComponent(currentHp = 50, maxHp = 100)
            val effect = StatusEffect(StatusEffectType.POISON_DOT, 5, 3, "e1")
            val updated = original.applyStatus(effect)

            assertEquals(0, original.statusEffects.size)
            assertEquals(1, updated.statusEffects.size)
        }
    }
}
