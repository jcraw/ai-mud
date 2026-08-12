@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.WorldState

/** Prompt text for combat round narration (MUD-034k). */
internal object CombatNarratorRoundPrompt {

    fun system(): String = """
        You are a dungeon master narrating a turn-based combat encounter.
        Create vivid, atmospheric combat descriptions that bring the action to life.
        Keep descriptions brief (1-2 SHORT sentences per attack) but evocative.
        Focus on the visceral details of combat - the clash of steel, the grunt of effort, the spray of blood.
        If past combat history is provided, build on it to show progression of the fight.

        IMPORTANT: Put each attack on its own line. First line: player's attack. Second line: enemy's counter (if any).
    """.trimIndent()

    fun build(
        worldState: WorldState,
        spaceName: String,
        terrainType: String,
        npc: Entity.NPC,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean,
        playerDied: Boolean,
        memories: List<String>
    ): String = buildString {
        appendHeader(spaceName, terrainType)
        appendCombatants(worldState, npc)
        appendMemories(memories)
        appendActions(npc.name, playerDamage, npcDamage, npcDied)
        appendResult(worldState, npc.name, npcDied, playerDied)
        appendLine()
        appendLine("Narrate this combat round in 1-2 SHORT sentences per attack. Put player attack on one line, enemy counter on next line. Use the player's actual weapon (or fists if unarmed). Do not include damage numbers.")
    }

    private fun StringBuilder.appendHeader(spaceName: String, terrainType: String) {
        appendLine("Combat Round:")
        appendLine("Location: $spaceName")
        appendLine("Room atmosphere: $terrainType")
        appendLine()
    }

    private fun StringBuilder.appendCombatants(worldState: WorldState, npc: Entity.NPC) {
        val player = worldState.player
        appendLine("Combatants:")
        appendLine("- Player (${player.name}): Health ${player.health}/${player.maxHealth}")
        val weapon = player.inventoryComponent.getEquipped(EquipSlot.HANDS_MAIN)
            ?: player.inventoryComponent.getEquipped(EquipSlot.HANDS_BOTH)
        val armor = player.inventoryComponent.getEquipped(EquipSlot.CHEST)
        appendLine("  - Weapon: ${weapon?.templateId ?: "bare fists"}")
        appendLine("  - Armor: ${armor?.templateId ?: "no armor"}")
        appendLine("  - STR: ${player.stats.strength}, DEX: ${player.stats.dexterity}")
        appendLine("- Enemy (${npc.name}): ${npc.description}")
        appendLine()
    }

    private fun StringBuilder.appendMemories(memories: List<String>) {
        if (memories.isEmpty()) return
        appendLine("Previous combat rounds:")
        memories.forEach { appendLine("- $it") }
        appendLine()
    }

    private fun StringBuilder.appendActions(
        npcName: String,
        playerDamage: Int,
        npcDamage: Int,
        npcDied: Boolean
    ) {
        appendLine("Actions this round:")
        appendLine("1. Player attacks $npcName for $playerDamage damage")
        if (!npcDied) {
            appendLine("2. $npcName counter-attacks for $npcDamage damage")
        }
        appendLine()
    }

    private fun StringBuilder.appendResult(
        worldState: WorldState,
        npcName: String,
        npcDied: Boolean,
        playerDied: Boolean
    ) {
        when {
            npcDied -> appendLine("Result: $npcName is defeated!")
            playerDied -> appendLine("Result: ${worldState.player.name} is defeated!")
            else -> appendLine("Result: Combat continues")
        }
    }
}
