package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Represents an ongoing combat encounter between the player and an NPC.
 * Combat is turn-based with simple mechanics.
 */
@Serializable
data class CombatState(
    val combatantNpcId: String,
    val playerHealth: Int,
    val npcHealth: Int,
    val isPlayerTurn: Boolean = true,
    val turnCount: Int = 0,
    val combatLog: List<String> = emptyList()
) {
    fun isActive(): Boolean = playerHealth > 0 && npcHealth > 0

    fun addLogEntry(entry: String): CombatState = copy(combatLog = combatLog + entry)

    fun nextTurn(): CombatState = copy(
        isPlayerTurn = !isPlayerTurn,
        turnCount = turnCount + 1
    )

    fun applyPlayerDamage(damage: Int): CombatState = copy(
        npcHealth = (npcHealth - damage).coerceAtLeast(0)
    )

    fun applyNpcDamage(damage: Int): CombatState = copy(
        playerHealth = (playerHealth - damage).coerceAtLeast(0)
    )
}

/**
 * Represents a combat action that can be taken during combat.
 */
@Serializable
sealed class CombatAction {
    @Serializable
    data object Attack : CombatAction()

    @Serializable
    data object Defend : CombatAction()

    @Serializable
    data object Flee : CombatAction()

    @Serializable
    data class UseItem(val itemId: String) : CombatAction()
}

/**
 * Result of a combat round, including narrative and state changes.
 */
data class CombatResult(
    val narrative: String,
    val newCombatState: CombatState?,  // null if combat ended
    val playerDied: Boolean = false,
    val npcDied: Boolean = false,
    val playerFled: Boolean = false,
    val playerDamage: Int = 0,
    val npcDamage: Int = 0
)
