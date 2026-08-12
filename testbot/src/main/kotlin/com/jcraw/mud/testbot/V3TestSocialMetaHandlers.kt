@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.Entity

/**
 * Talk / search / quests / rest / help / quit for V3 test engine (MUD-034f).
 */
internal object V3TestSocialMetaHandlers {

    fun handleTalk(state: V3TestEngineState, target: String): String {
        val entities = state.worldState.getEntitiesInSpace(state.worldState.player.currentRoomId)
        val npc = entities.filterIsInstance<Entity.NPC>()
            .find { it.name.contains(target, ignoreCase = true) }
            ?: return "You don't see '$target' here."
        return "${npc.name} says: \"Hello, adventurer.\""
    }

    fun handleSearch(state: V3TestEngineState): String {
        val player = state.worldState.player
        val currentNode = state.worldState.getCurrentGraphNode() ?: return "Nothing to search here."
        val hiddenEdges = currentNode.neighbors.filter { edge ->
            edge.hidden && !player.hasRevealedExit("${currentNode.id}:${edge.targetId}")
        }
        if (hiddenEdges.isEmpty()) {
            return "You search the area but find nothing unusual."
        }
        return tryRevealHidden(state, player, currentNode.id, hiddenEdges)
    }

    private fun tryRevealHidden(
        state: V3TestEngineState,
        player: com.jcraw.mud.core.PlayerState,
        nodeId: String,
        hiddenEdges: List<com.jcraw.mud.core.world.EdgeData>
    ): String {
        val perceptionLevel = state.skillManager.getSkillComponent(player.id)
            ?.getEffectiveLevel("Perception") ?: 0
        val total = (1..20).random() + perceptionLevel
        if (total < 15) {
            return "You search carefully but find nothing unusual."
        }
        val revealed = hiddenEdges.first()
        val exitKey = "$nodeId:${revealed.targetId}"
        state.worldState = state.worldState.updatePlayer(player.revealExit(exitKey))
        val targetName = state.worldState.getSpace(revealed.targetId)?.name ?: "an unknown area"
        return "You discover a hidden passage leading ${revealed.direction} to $targetName!"
    }

    fun handleViewQuests(state: V3TestEngineState): String {
        val player = state.worldState.player
        val sb = StringBuilder()
        sb.appendLine("Active Quests:")
        if (player.activeQuests.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            player.activeQuests.forEach { quest ->
                val status = if (quest.isComplete()) " [COMPLETE]" else ""
                sb.appendLine("  - ${quest.title}$status")
            }
        }
        sb.appendLine("\nAvailable Quests:")
        if (state.worldState.availableQuests.isEmpty()) {
            sb.appendLine("  (none)")
        } else {
            state.worldState.availableQuests.forEach { quest ->
                sb.appendLine("  - ${quest.title} (${quest.id})")
            }
        }
        return sb.toString().trim()
    }

    fun handleRest(state: V3TestEngineState): String {
        val space = state.worldState.getCurrentSpace()
        if (space?.isSafeZone != true) {
            return "You can't rest here - it's not safe!"
        }
        val player = state.worldState.player
        val healAmount = (player.maxHealth * 0.25).toInt().coerceAtLeast(5)
        val newHealth = (player.health + healAmount).coerceAtMost(player.maxHealth)
        val actualHeal = newHealth - player.health
        state.worldState = state.worldState.updatePlayer(player.copy(health = newHealth))
        return if (actualHeal > 0) {
            "You rest and recover $actualHeal health. (${newHealth}/${player.maxHealth})"
        } else {
            "You rest but are already at full health."
        }
    }

    fun handleHelp(): String = """
Available commands:
  Movement: n/s/e/w, look
  Items: take, drop, inventory
  Combat: attack <target>
  Social: talk <npc>
  Skills: search
  Quests: quests
  Other: rest, help, quit
        """.trimIndent()

    fun handleQuit(state: V3TestEngineState): String {
        state.running = false
        return "Thanks for playing!"
    }
}
