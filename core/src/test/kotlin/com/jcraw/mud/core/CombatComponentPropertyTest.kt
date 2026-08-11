@file:OptIn(io.kotest.common.ExperimentalKotest::class)

package com.jcraw.mud.core

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.flatMap
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * MUD-015 property tests for [CombatComponent] pure HP / maxHp clamp laws.
 *
 * Laws (hard fail): C1–C3.
 * Soft: negative damage/heal amounts excluded (undefined contract); no stats (S1/S3).
 * See docs/PBT.md. Does not weaken example suite [CombatComponentTest].
 */
class CombatComponentPropertyTest {

    @Test
    fun `C1 law - non-negative applyDamage clamps HP to 0 and never raises HP`() {
        runBlocking {
            checkAll(config, arbCombatHp(), nonNegativeAmount()) { combat, amount ->
                val before = combat.currentHp
                val after = combat.applyDamage(amount)

                assertTrue(after.currentHp >= 0, "HP must never be negative, got ${after.currentHp}")
                assertTrue(
                    after.currentHp in 0..before,
                    "applyDamage($amount) HP ${after.currentHp} not in [0, $before]"
                )
                assertTrue(after.maxHp == combat.maxHp, "applyDamage must not change maxHp")
            }
        }
    }

    @Test
    fun `C2 law - non-negative heal stays in current through maxHp`() {
        runBlocking {
            checkAll(config, arbCombatHp(), nonNegativeAmount()) { combat, amount ->
                val before = combat.currentHp
                val after = combat.heal(amount)

                assertTrue(
                    after.currentHp in before..combat.maxHp,
                    "heal($amount) HP ${after.currentHp} not in [$before, ${combat.maxHp}]"
                )
                assertTrue(after.maxHp == combat.maxHp, "heal must not change maxHp")
            }
        }
    }

    @Test
    fun `C3 law - calculateMaxHp is at least 10 for null or non-neg skill stubs`() {
        runBlocking {
            // skills = null path + item bonus (can be negative — still floor 10)
            checkAll(config, Arb.int(-1_000..10_000)) { itemHpBonus ->
                val maxHp = CombatComponent.calculateMaxHp(skills = null, itemHpBonus = itemHpBonus)
                assertTrue(maxHp >= 10, "null skills maxHp=$maxHp < 10 with bonus=$itemHpBonus")
            }

            // non-negative skill stubs + item bonus
            checkAll(
                config,
                arbNonNegSkillLevels(),
                Arb.int(-1_000..10_000)
            ) { skills, itemHpBonus ->
                val maxHp = CombatComponent.calculateMaxHp(skills = skills, itemHpBonus = itemHpBonus)
                assertTrue(maxHp >= 10, "skills maxHp=$maxHp < 10 with bonus=$itemHpBonus skills=$skills")
            }
        }
    }

    companion object {
        /** Fixed seed for MUD-015 — reproducible CI (see docs/PBT.md). */
        const val PBT_SEED = 15_015L
        const val PBT_ITERATIONS = 100

        private val config = PropTestConfig(seed = PBT_SEED, iterations = PBT_ITERATIONS)

        /** Non-negative damage/heal amounts only (S3: negative is undefined). */
        private fun nonNegativeAmount(): Arb<Int> = Arb.int(0..50_000)

        /** maxHp ∈ [1, 10_000], currentHp ∈ [0, maxHp]. */
        private fun arbCombatHp(): Arb<CombatComponent> =
            Arb.int(1..10_000).flatMap { maxHp ->
                Arb.int(0..maxHp).map { currentHp ->
                    CombatComponent(currentHp = currentHp, maxHp = maxHp)
                }
            }

        /**
         * Non-negative skill levels for Vitality / Endurance / Constitution.
         * Unlocked so [SkillComponent.getEffectiveLevel] returns the level.
         */
        private fun arbNonNegSkillLevels(): Arb<SkillComponent> =
            Arb.bind(
                Arb.int(0..200),
                Arb.int(0..200),
                Arb.int(0..200)
            ) { vit, end, con ->
                SkillComponent(
                    skills = mapOf(
                        "Vitality" to SkillState(level = vit, xp = 0L, unlocked = true),
                        "Endurance" to SkillState(level = end, xp = 0L, unlocked = true),
                        "Constitution" to SkillState(level = con, xp = 0L, unlocked = true)
                    )
                )
            }
    }
}
