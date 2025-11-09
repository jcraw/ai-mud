package com.jcraw.app.handlers

import com.jcraw.app.MudGame
import com.jcraw.app.times
import com.jcraw.mud.core.Direction
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.reasoning.QuestAction
import kotlinx.coroutines.runBlocking

/**
 * Handlers for movement, exploration, and searching actions.
 */
object MovementHandlers {

    fun handleMove(game: MudGame, direction: Direction) {
        // V2 combat is emergent - no modal combat state, so movement is always allowed
        // Hostile NPCs in turn queue will get their attacks when their timer expires

        // V3: Graph-based navigation
        val newState = game.worldState.movePlayerV3(direction)

        if (newState == null) {
            println("You can't go that way.")
            return
        }

        game.worldState = newState
        postMove(game, direction.displayName)
    }

    fun handleLook(game: MudGame, target: String?) {
        if (target == null) {
            // Look at room - describeCurrentRoom already shows all entities including items
            game.describeCurrentRoom()
        } else {
            // V3: Use space-based entity system
            val spaceId = game.worldState.player.currentRoomId
            val entities = game.worldState.getEntitiesInSpace(spaceId)
            val entity = entities.find { e ->
                e.name.lowercase().contains(target.lowercase()) ||
                e.id.lowercase().contains(target.lowercase())
            }

            if (entity != null) {
                println(entity.description)
            } else {
                // Try to describe scenery (non-entity objects like walls, floor, etc.)
                val space = game.worldState.getCurrentSpace()
                if (space != null) {
                    val roomDescription = game.generateRoomDescription(space, spaceId)
                    val sceneryDescription = runBlocking {
                        game.sceneryGenerator.describeScenery(target, space, roomDescription)
                    }

                    if (sceneryDescription != null) {
                        println(sceneryDescription)
                    } else {
                        println("You don't see that here.")
                    }
                } else {
                    println("You don't see that here.")
                }
            }
        }
    }

    fun handleSearch(game: MudGame, target: String?) {
        // V3: Check if using graph-based world with hidden exits
        val currentNode = game.worldState.getCurrentGraphNode()
        val player = game.worldState.player
        val hasV3Graph = currentNode != null

        println("\nYou search the area carefully${if (target != null) ", focusing on the $target" else ""}...")

        // Perform a Wisdom (Perception) skill check
        val result = game.skillCheckResolver.checkPlayer(
            player,
            com.jcraw.mud.core.StatType.WISDOM,
            com.jcraw.mud.core.Difficulty.MEDIUM  // DC 15 for finding hidden items/exits
        )

        // Display roll details
        println("\nRolling Perception check...")
        println("d20 roll: ${result.roll} + WIS modifier: ${result.modifier} = ${result.total} vs DC ${result.dc}")

        if (result.isCriticalSuccess) {
            println("\n🎲 CRITICAL SUCCESS! (Natural 20)")
        } else if (result.isCriticalFailure) {
            println("\n💀 CRITICAL FAILURE! (Natural 1)")
        }

        if (result.success) {
            println("\n✅ Success!")

            var foundSomething = false

            // V3: Check for hidden exits
            if (hasV3Graph && currentNode != null) {
                // Find hidden exits that haven't been revealed yet
                val hiddenExits = currentNode.neighbors.filter { edge ->
                    val edgeId = edge.edgeId(currentNode.id)
                    edge.hidden && !player.hasRevealedExit(edgeId)
                }

                if (hiddenExits.isNotEmpty()) {
                    // Reveal the first hidden exit
                    val revealedExit = hiddenExits.first()
                    val edgeId = revealedExit.edgeId(currentNode.id)
                    game.worldState = game.worldState.updatePlayer(player.revealExit(edgeId))

                    println("\n🔍 You discover a hidden exit: ${revealedExit.direction}!")
                    foundSomething = true
                }
            }

            // V3: Check for hidden items
            val spaceId = game.worldState.player.currentRoomId
            val entities = game.worldState.getEntitiesInSpace(spaceId)
            val hiddenItems = entities.filterIsInstance<Entity.Item>().filter { !it.isPickupable }
            val pickupableItems = entities.filterIsInstance<Entity.Item>().filter { it.isPickupable }

            if (pickupableItems.isNotEmpty()) {
                println("\nYou find the following items:")
                pickupableItems.forEach { item ->
                    println("  - ${item.name}: ${item.description}")
                }
                foundSomething = true
            }
            if (hiddenItems.isNotEmpty()) {
                println("\nYou also notice some interesting features:")
                hiddenItems.forEach { item ->
                    println("  - ${item.name}: ${item.description}")
                }
                foundSomething = true
            }

            if (!foundSomething) {
                println("You don't find anything hidden here.")
            }
        } else {
            println("\n❌ Failure!")
            println("You don't find anything of interest.")
        }
    }

    fun handleTravel(game: MudGame, rawDirection: String) {
        val normalized = rawDirection.trim()
        if (normalized.isEmpty()) {
            println("Travel where?")
            return
        }

        val cardinal = Direction.fromString(normalized)
        if (cardinal != null) {
            handleMove(game, cardinal)
            return
        }

        val directMove = game.worldState.movePlayerByExit(normalized)
        if (directMove != null) {
            game.worldState = directMove
            postMove(game, normalized)
            return
        }

        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            println("You can't go that way.")
            return
        }

        val resolvedExit = space.resolveExit(normalized, game.worldState.player)
        if (resolvedExit == null) {
            println("You can't go that way.")
            return
        }

        val fallback = game.worldState.movePlayerByExit(resolvedExit.direction)
        if (fallback != null) {
            game.worldState = fallback
            postMove(game, resolvedExit.direction)
            return
        }

        val targetSpace = game.worldState.getSpace(resolvedExit.targetId)
        if (targetSpace == null) {
            println("That passage hasn't fully formed yet.")
            return
        }

        val updatedPlayer = game.worldState.player.moveToRoom(resolvedExit.targetId)
        game.worldState = game.worldState.updatePlayer(updatedPlayer)
        postMove(game, resolvedExit.direction)
    }

    fun handleScout(game: MudGame, target: String?) {
        val space = game.worldState.getCurrentSpace()
        if (space == null) {
            println("You find no clues about this area.")
            return
        }

        val player = game.worldState.player
        if (target.isNullOrBlank()) {
            val visible = space.getVisibleExits(player)
            if (visible.isEmpty()) {
                println("You don't notice any obvious exits.")
            } else {
                println("\nVisible exits:")
                visible.forEach { exit ->
                    println("  - ${exit.direction}: ${exit.describeWithConditions(player)}")
                }
            }
            return
        }

        val resolved = space.resolveExit(target, player)
        if (resolved == null) {
            println("You can't find any exit matching \"$target\".")
            return
        }

        println("\nYou examine the ${resolved.direction}:")
        println("  ${resolved.description}")

        if (resolved.conditions.isNotEmpty()) {
            val unmet = resolved.conditions.filterNot { it.meetsCondition(player) }
            if (unmet.isNotEmpty()) {
                println("  Requirements: ${unmet.joinToString(", ") { it.describe() }}")
            }
        }

        val destinationName = game.worldState.getSpace(resolved.targetId)?.name ?: resolved.targetId
        println("  It seems to lead toward $destinationName.")
    }

    private fun postMove(game: MudGame, movementLabel: String) {
        println("You move $movementLabel.")

        val currentSpace = game.worldState.getCurrentSpace()
        val currentNode = game.worldState.getCurrentGraphNode()
        val spaceId = game.worldState.player.currentRoomId

        if (currentSpace != null && currentNode != null && currentSpace.description.isEmpty()) {
            val chunk = game.worldState.getChunk(currentNode.chunkId)

            if (chunk != null && game.worldGenerator != null) {
                runBlocking {
                    val result = game.worldGenerator?.fillSpaceContent(currentSpace, currentNode, chunk)
                    result?.onSuccess { filledSpace ->
                        game.worldState = game.worldState.updateSpace(spaceId, filledSpace)
                    }?.onFailure {
                        println("(Content generation unavailable)")
                    }
                }
            }
        }

        if (currentSpace != null && currentNode != null) {
            populateSpaceIfNeeded(game, spaceId, currentSpace, currentNode)
        }

        val frontierNode = game.worldState.getCurrentGraphNode()
        if (frontierNode != null && frontierNode.type is com.jcraw.mud.core.world.NodeType.Frontier) {
            val chunk = game.worldState.getChunk(frontierNode.chunkId)

            if (chunk != null && game.worldGenerator != null && game.graphNodeRepository != null) {
                val hasGeneratedExit = frontierNode.neighbors.any { edge ->
                    game.worldState.getGraphNode(edge.targetId) != null
                }

                if (!hasGeneratedExit) {
                    runBlocking {
                        val context = com.jcraw.mud.core.world.GenerationContext(
                            seed = (frontierNode.chunkId.hashCode().toLong() + System.currentTimeMillis()).toString(),
                            globalLore = chunk.lore,
                            parentChunk = chunk,
                            parentChunkId = chunk.parentId,
                            level = com.jcraw.mud.core.world.ChunkLevel.SUBZONE,
                            direction = "frontier_expansion"
                        )

                        val result = game.worldGenerator?.generateChunk(context)
                        result?.onSuccess { genResult ->
                            game.worldChunkRepository.save(genResult.chunk, genResult.chunkId)
                            game.worldState = game.worldState.addChunk(genResult.chunkId, genResult.chunk)

                            genResult.graphNodes.forEach { node ->
                                game.graphNodeRepository.save(node)
                                game.worldState = game.worldState.updateGraphNode(node.id, node)

                                val spaceStub = game.worldGenerator?.generateSpaceStub(node, genResult.chunk)
                                spaceStub?.onSuccess { space ->
                                    val nodeSpaceId = "${genResult.chunkId}_node_${node.id}"
                                    game.spacePropertiesRepository.save(space, nodeSpaceId)
                                    game.worldState = game.worldState.updateSpace(nodeSpaceId, space)
                                }
                            }

                            val hubNode = genResult.graphNodes.find { it.type is com.jcraw.mud.core.world.NodeType.Hub }
                            if (hubNode != null) {
                                val edgeDirection = "frontier passage"
                                val newEdge = com.jcraw.mud.core.world.EdgeData(
                                    targetId = hubNode.id,
                                    direction = edgeDirection,
                                    hidden = false
                                )

                                val updatedFrontier = frontierNode.copy(
                                    neighbors = frontierNode.neighbors + newEdge
                                )
                                game.graphNodeRepository.update(updatedFrontier)
                                game.worldState = game.worldState.updateGraphNode(updatedFrontier.id, updatedFrontier)

                                println("\n🗺️  You've discovered a new frontier! A passage opens before you...")
                            }
                        }?.onFailure { error ->
                            println("(Frontier generation failed: ${error.message})")
                        }
                    }
                }
            }
        }

        val currentSpaceId = game.worldState.player.currentRoomId
        game.trackQuests(QuestAction.VisitedRoom(currentSpaceId))

        game.describeCurrentRoom()
    }

    private fun populateSpaceIfNeeded(
        game: MudGame,
        spaceId: String,
        space: SpacePropertiesComponent,
        node: GraphNodeComponent
    ) {
        val populator = game.spacePopulator ?: return
        if (space.stateFlags["populated"] == true) return
        if (space.entities.isNotEmpty()) return
        if (space.isSafeZone) return

        val chunk = game.worldState.getChunk(node.chunkId) ?: return

        runBlocking {
            val populationResult = if (game.respawnChecker != null) {
                populator.populateWithRespawn(
                    space = space,
                    spaceId = spaceId,
                    theme = chunk.biomeTheme,
                    difficulty = chunk.difficultyLevel,
                    mobDensity = chunk.mobDensity,
                    respawnChecker = game.respawnChecker
                )
            } else {
                Result.success(
                    populator.populateWithEntities(
                        space = space,
                        theme = chunk.biomeTheme,
                        difficulty = chunk.difficultyLevel,
                        mobDensity = chunk.mobDensity
                    )
                )
            }

            populationResult.onSuccess { (populatedSpace, spawnedEntities) ->
                val flaggedSpace = populatedSpace.copy(
                    stateFlags = populatedSpace.stateFlags + ("populated" to true)
                )

                var updatedWorld = game.worldState.updateSpace(spaceId, flaggedSpace)
                spawnedEntities.forEach { entity ->
                    updatedWorld = updatedWorld.updateEntity(entity)
                }
                game.spacePropertiesRepository.save(flaggedSpace, spaceId)
                    .onFailure { println("Warning: Failed to persist space $spaceId: ${it.message}") }
                game.worldState = updatedWorld
            }.onFailure { error ->
                println("(Population failed: ${error.message})")
            }
        }
    }
}
