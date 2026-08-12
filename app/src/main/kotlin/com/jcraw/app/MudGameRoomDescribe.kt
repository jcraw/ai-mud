@file:Suppress("ReturnCount", "MagicNumber", "TooManyFunctions")

package com.jcraw.app

import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SpacePropertiesComponent
import kotlinx.coroutines.runBlocking

/**
 * Room/space description helpers for [MudGame]. Pure extract.
 */
object MudGameRoomDescribe {

    fun printWelcome(game: MudGame) {
        println("\nWelcome, ${game.worldState.player.name}!")
        val spaceCount =
            game.worldState.graphNodes.size.takeIf { it > 0 } ?: game.worldState.spaces.size
        println("You have entered a dungeon with $spaceCount spaces to explore.")
        println("Type 'help' for available commands.\n")
    }

    fun describeCurrentRoom(game: MudGame) {
        val space = game.worldState.getCurrentSpace()
        val node = game.worldState.getCurrentGraphNode()
        val player = game.worldState.player
        if (space == null || node == null) {
            println("\n[No space data loaded - unable to describe surroundings]")
            return
        }
        println("\n${space.name}")
        println("-" * space.name.length)
        val description = generateRoomDescription(game, space, player.currentRoomId)
        println(description.ifBlank { "An unexplored area..." })
        if (space.isTreasureRoom) describeTreasureRoomState(game, player.currentRoomId)
        printVisibleExits(game, node, player)
        printEntities(game, player)
    }

    private fun printVisibleExits(
        game: MudGame,
        node: GraphNodeComponent,
        player: PlayerState
    ) {
        val visible = node.neighbors.filter { edge ->
            !edge.hidden || player.hasRevealedExit("${node.id}:${edge.targetId}")
        }
        if (visible.isEmpty()) return
        val exitText = visible.joinToString(", ") { edge ->
            val name = game.worldState.getSpace(edge.targetId)?.name ?: edge.targetId
            "${edge.direction} ($name)"
        }
        println("\nExits: $exitText")
    }

    private fun printEntities(game: MudGame, player: PlayerState) {
        val entities = game.worldState.getEntitiesInSpace(player.currentRoomId)
        if (game.lastConversationNpcId != null &&
            entities.none { it.id == game.lastConversationNpcId }
        ) {
            game.lastConversationNpcId = null
        }
        if (entities.isEmpty()) return
        println("\nYou see:")
        entities.forEach { println("  - ${entityLine(it)}") }
    }

    private fun entityLine(entity: Entity): String {
        if (entity !is Entity.NPC) return entity.name
        val d = entity.getDisposition()
        val status = when {
            d < -75 -> " ⚔️  (hostile - glares at you!)"
            d < -50 -> " ⚠️  (unfriendly - watches you warily)"
            d < -25 -> " (neutral)"
            d < 25 -> " (neutral)"
            d < 75 -> " ✓ (friendly)"
            else -> " ★ (allied)"
        }
        return "${entity.name}$status"
    }

    fun describeTreasureRoomState(game: MudGame, spaceId: String) {
        val room = game.worldState.getTreasureRoom(spaceId)
        if (room == null) {
            println("\n(An eerie hush lingers—this treasure room's state couldn't be loaded.)")
            return
        }
        println()
        println(treasureStateLine(game, room))
    }

    private fun treasureStateLine(
        game: MudGame,
        room: com.jcraw.mud.core.TreasureRoomComponent
    ): String {
        val taken = room.currentlyTakenItem
        return when {
            room.hasBeenLooted ->
                "Only bare pedestals remain; the room's magic has faded."
            taken == null ->
                "Five pedestals glow softly. Claim a single treasure with " +
                    "'examine pedestals' before the others seal away."
            else -> {
                val name = game.itemRepository.findTemplateById(taken)
                    .getOrNull()?.name ?: taken
                "The other pedestals are sealed while you hold the $name. " +
                    "Return it if you wish to choose again."
            }
        }
    }

    fun generateRoomDescription(
        game: MudGame,
        space: SpacePropertiesComponent,
        spaceId: String? = null
    ): String {
        if (space.description.isNotBlank() && !space.descriptionStale) {
            return space.description
        }
        val gen = game.descriptionGenerator
            ?: return "You are in ${space.name}. " +
                "The ${space.terrainType.name.lowercase()} terrain reveals little else."
        val generated = runBlocking { gen.generateDescription(space) }
        if (spaceId != null) {
            game.worldState = game.worldState.updateSpace(spaceId, space.withDescription(generated))
        }
        return generated
    }

    fun buildExitsWithNames(game: MudGame, node: GraphNodeComponent): Map<Direction, String> {
        return node.neighbors.mapNotNull { edge ->
            val dir = Direction.fromString(edge.direction) ?: return@mapNotNull null
            val name = game.worldState.getSpace(edge.targetId)?.name ?: edge.targetId
            dir to name
        }.toMap()
    }
}
