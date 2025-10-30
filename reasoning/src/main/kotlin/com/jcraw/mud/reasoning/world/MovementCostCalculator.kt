package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.world.TerrainType
import kotlin.random.Random

/**
 * Result of movement cost calculation
 *
 * @param ticks Number of game ticks consumed by movement
 * @param damageRisk Amount of damage player might take (0 if no risk)
 * @param success True if movement is possible, false if terrain is impassable
 * @param skillCheckRequired True if player must pass a skill check to avoid damage
 */
data class MovementCost(
    val ticks: Int,
    val damageRisk: Int,
    val success: Boolean,
    val skillCheckRequired: Boolean = false
)

/**
 * Calculates movement costs and risks based on terrain type and player skills.
 * Integrates with combat turn queue system for tick-based movement.
 */
class MovementCostCalculator {
    /**
     * Calculates the cost of moving through a specific terrain type.
     *
     * Terrain mechanics:
     * - NORMAL: 1 tick, no risk
     * - DIFFICULT: 2 ticks base, Agility check (DC 10) to avoid 1d6 damage
     * - IMPASSABLE: Movement fails, 0 ticks consumed
     *
     * @param terrain The terrain type to traverse
     * @param playerState Current player state (for skill modifiers)
     * @return MovementCost with ticks, damage risk, and success status
     */
    fun calculateCost(terrain: TerrainType, playerState: PlayerState): MovementCost {
        return when (terrain) {
            TerrainType.NORMAL -> {
                MovementCost(
                    ticks = 1,
                    damageRisk = 0,
                    success = true,
                    skillCheckRequired = false
                )
            }

            TerrainType.DIFFICULT -> {
                val baseTicks = 2
                val modifiedTicks = applySkillModifiers(baseTicks, playerState)

                // Dexterity check to avoid 1d6 damage
                val dexterityModifier = playerState.stats.dexterity / 2 - 5
                val skillCheckDC = 10
                val roll = Random.nextInt(1, 21)
                val skillCheckResult = roll + dexterityModifier

                val damageRisk = if (skillCheckResult < skillCheckDC) {
                    Random.nextInt(1, 7) // 1d6
                } else {
                    0
                }

                MovementCost(
                    ticks = modifiedTicks,
                    damageRisk = damageRisk,
                    success = true,
                    skillCheckRequired = true
                )
            }

            TerrainType.IMPASSABLE -> {
                MovementCost(
                    ticks = 0,
                    damageRisk = 0,
                    success = false,
                    skillCheckRequired = false
                )
            }
        }
    }

    /**
     * Applies skill modifiers to base movement cost.
     *
     * Skill effects:
     * - Athletics level 10+: Reduces difficult terrain cost by 1 tick (minimum 1)
     * - Athletics level 20+: Reduces difficult terrain cost by 2 ticks (minimum 1)
     *
     * @param baseCost The base tick cost before modifiers
     * @param player Current player state with skills
     * @return Modified tick cost (never less than 1)
     */
    private fun applySkillModifiers(baseCost: Int, player: PlayerState): Int {
        val athleticsLevel = player.getSkillLevel("Athletics")

        val reduction = when {
            athleticsLevel >= 20 -> 2
            athleticsLevel >= 10 -> 1
            else -> 0
        }

        return (baseCost - reduction).coerceAtLeast(1)
    }

    /**
     * Calculates damage from a difficult terrain skill check.
     * This should be called after calculateCost() when skillCheckRequired is true.
     *
     * @param playerState Current player state
     * @param difficulty DC for the Dexterity check (default 10)
     * @return Damage dealt (0 if check passed, 1d6 if failed)
     */
    fun calculateTerrainDamage(playerState: PlayerState, difficulty: Int = 10): Int {
        val dexterityModifier = playerState.stats.dexterity / 2 - 5
        val roll = Random.nextInt(1, 21)
        val skillCheckResult = roll + dexterityModifier

        return if (skillCheckResult < difficulty) {
            Random.nextInt(1, 7) // 1d6
        } else {
            0
        }
    }
}
