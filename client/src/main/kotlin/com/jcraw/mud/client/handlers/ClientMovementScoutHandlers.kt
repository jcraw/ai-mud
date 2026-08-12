@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.core.GameEvent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Scout for [ClientMovementHandlers] facade.
 */
object ClientMovementScoutHandlers {

    fun handleScout(game: EngineGameClient, rawDirection: String?) {
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            game.emitEvent(GameEvent.System("You are not in a known space.", GameEvent.MessageLevel.ERROR))
            return
        }
        val player = game.worldState.player
        val playerSkills = game.skillManager.getSkillComponent(player.id)
        if (rawDirection.isNullOrBlank()) {
            listVisibleExits(game, space, player, playerSkills)
            return
        }
        examineExit(game, space, player, playerSkills, rawDirection)
    }

    private fun listVisibleExits(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        player: PlayerState,
        playerSkills: SkillComponent
    ) {
        val visible = space.getVisibleExits(player, playerSkills)
        if (visible.isEmpty()) {
            game.emitEvent(GameEvent.Narrative("You don't notice any obvious exits."))
        } else {
            val text = buildString {
                appendLine("Visible exits:")
                visible.forEach { exit ->
                    appendLine("  - ${exit.direction}: ${exit.describeWithConditions(player, playerSkills)}")
                }
            }
            game.emitEvent(GameEvent.Narrative(text))
        }
    }

    private fun examineExit(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        player: PlayerState,
        playerSkills: SkillComponent,
        rawDirection: String
    ) {
        val resolved = space.resolveExit(rawDirection, player, playerSkills)
        if (resolved == null) {
            game.emitEvent(
                GameEvent.System(
                    "You can't find any exit matching \"$rawDirection\".",
                    GameEvent.MessageLevel.INFO
                )
            )
            return
        }
        val description = buildExitDescription(game, resolved, player, playerSkills)
        game.emitEvent(GameEvent.Narrative(description))
    }

    private fun buildExitDescription(
        game: EngineGameClient,
        resolved: com.jcraw.mud.core.world.ExitData,
        player: PlayerState,
        playerSkills: SkillComponent
    ): String = buildString {
        appendLine("You examine the ${resolved.direction}:")
        appendLine("  ${resolved.description}")
        if (resolved.conditions.isNotEmpty()) {
            val unmet = resolved.conditions.filterNot { it.meetsCondition(player, playerSkills) }
            if (unmet.isNotEmpty()) {
                appendLine("  Requirements: ${unmet.joinToString(", ") { it.describe() }}")
            }
        }
        val destSpace = game.loadSpace(resolved.targetId) ?: game.worldState.getSpace(resolved.targetId)
        if (destSpace != null && destSpace.description.isNotBlank()) {
            appendLine()
            appendLine("Ahead you sense: ${destSpace.description.lines().first()}")
        }
    }
}
