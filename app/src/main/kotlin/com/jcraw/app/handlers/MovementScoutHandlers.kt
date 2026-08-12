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

package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SpacePropertiesComponent

/**
 * Scout for [MovementHandlers] facade.
 */
object MovementScoutHandlers {

    fun handleScout(game: MudGame, target: String?) {
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            println("You find no clues about this area.")
            return
        }
        val player = game.worldState.player
        val playerSkills = game.skillManager.getSkillComponent(player.id)
        if (target.isNullOrBlank()) {
            listVisibleExits(space, player, playerSkills)
            return
        }
        examineExit(game, space, player, playerSkills, target)
    }

    private fun listVisibleExits(
        space: SpacePropertiesComponent,
        player: PlayerState,
        playerSkills: SkillComponent
    ) {
        val visible = space.getVisibleExits(player, playerSkills)
        if (visible.isEmpty()) {
            println("You don't notice any obvious exits.")
        } else {
            println("\nVisible exits:")
            visible.forEach { exit ->
                println("  - ${exit.direction}: ${exit.describeWithConditions(player, playerSkills)}")
            }
        }
    }

    private fun examineExit(
        game: MudGame,
        space: SpacePropertiesComponent,
        player: PlayerState,
        playerSkills: SkillComponent,
        target: String
    ) {
        val resolved = space.resolveExit(target, player, playerSkills)
        if (resolved == null) {
            println("You can't find any exit matching \"$target\".")
            return
        }
        println("\nYou examine the ${resolved.direction}:")
        println("  ${resolved.description}")
        if (resolved.conditions.isNotEmpty()) {
            val unmet = resolved.conditions.filterNot { it.meetsCondition(player, playerSkills) }
            if (unmet.isNotEmpty()) {
                println("  Requirements: ${unmet.joinToString(", ") { it.describe() }}")
            }
        }
        val destinationName = game.worldState.getSpace(resolved.targetId)?.name ?: resolved.targetId
        println("  It seems to lead toward $destinationName.")
    }
}
