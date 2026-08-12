@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.Direction

/**
 * Movement + look handlers for V3 test engine (MUD-034f).
 */
internal object V3TestMoveHandlers {

    fun handleMove(state: V3TestEngineState, direction: Direction): String {
        val player = state.worldState.player
        val currentNode = state.worldState.getCurrentGraphNode()
            ?: return "You can't move - no navigation data."

        val playerSkills = state.skillManager.getSkillComponent(player.id)
        val edge = currentNode.neighbors.find { it.direction.equals(direction.name, ignoreCase = true) }

        if (edge == null || (edge.hidden && !player.hasRevealedExit("${currentNode.id}:${edge.targetId}"))) {
            return "You can't go $direction from here."
        }

        val newState = state.worldState.movePlayerV3(direction, playerSkills) ?: state.worldState
        if (newState == state.worldState) {
            return "You can't go $direction from here."
        }

        state.worldState = newState
        return buildLocationDescription(state)
    }

    fun handleLook(state: V3TestEngineState): String {
        return buildLocationDescription(state)
    }

    fun buildLocationDescription(state: V3TestEngineState): String {
        val space = state.worldState.getCurrentSpace() ?: return "You are in an unknown location."
        val node = state.worldState.getCurrentGraphNode()
        val player = state.worldState.player

        val sb = StringBuilder()
        sb.appendLine(space.name)
        sb.appendLine("-".repeat(space.name.length))
        sb.appendLine(space.description.ifBlank { "An unexplored area..." })

        if (node != null) {
            appendVisibleExits(sb, state, node, player)
        }

        appendEntities(sb, state, player.currentRoomId)

        return sb.toString().trim()
    }

    private fun appendVisibleExits(
        sb: StringBuilder,
        state: V3TestEngineState,
        node: com.jcraw.mud.core.GraphNodeComponent,
        player: com.jcraw.mud.core.PlayerState
    ) {
        val visibleExits = node.neighbors.filter { e ->
            !e.hidden || player.hasRevealedExit("${node.id}:${e.targetId}")
        }
        if (visibleExits.isNotEmpty()) {
            val exitText = visibleExits.joinToString(", ") { e ->
                val targetName = state.worldState.getSpace(e.targetId)?.name ?: e.targetId
                "${e.direction} ($targetName)"
            }
            sb.appendLine("\nExits: $exitText")
        }
    }

    private fun appendEntities(sb: StringBuilder, state: V3TestEngineState, spaceId: String) {
        val entities = state.worldState.getEntitiesInSpace(spaceId)
        if (entities.isNotEmpty()) {
            sb.appendLine("\nYou see:")
            entities.forEach { entity ->
                sb.appendLine("  - ${entity.name}")
            }
        }
    }
}
