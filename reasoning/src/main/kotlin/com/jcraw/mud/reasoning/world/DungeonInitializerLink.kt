@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "UnusedParameter",
    "TooGenericExceptionCaught",
    "TooGenericExceptionThrown",
    "SwallowedException",
    "WildcardImport",
    "MayBeConst",
    "ImplicitDefaultLocale",
    "ForbiddenComment",
    "UnusedPrivateProperty",
)

package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.NodeType

/**
 * Town ↔ combat link helpers (MUD-034g pure move).
 * Orchestrator on [DungeonInitializer] host.
 */
internal object DungeonInitializerLink {

    val TOWN_EXIT_DESC =
        "A dark stairway descends into the dungeon depths. The air grows colder as you look down."
    val DUNGEON_EXIT_DESC =
        "Stone stairs lead upward to the safety of the town above. You can see flickering torchlight."

    fun buildTownExit(combatEntranceId: String): ExitData = ExitData(
        targetId = combatEntranceId,
        direction = "down",
        description = TOWN_EXIT_DESC,
        conditions = emptyList(),
        isHidden = false
    )

    fun buildDungeonExit(townSpaceId: String): ExitData = ExitData(
        targetId = townSpaceId,
        direction = "up",
        description = DUNGEON_EXIT_DESC,
        conditions = emptyList(),
        isHidden = false
    )

    fun applySpaceExits(
        townSpace: SpacePropertiesComponent,
        combatSpace: SpacePropertiesComponent,
        townSpaceId: String,
        combatEntranceId: String
    ): Pair<SpacePropertiesComponent, SpacePropertiesComponent> {
        val updatedTown = if (townSpace.exits.any { it.targetId == combatEntranceId }) {
            townSpace
        } else {
            townSpace.addExit(buildTownExit(combatEntranceId))
        }
        val updatedCombat = if (combatSpace.exits.any { it.targetId == townSpaceId }) {
            combatSpace
        } else {
            combatSpace.addExit(buildDungeonExit(townSpaceId))
        }
        return updatedTown to updatedCombat
    }

    fun defaultTownNode(townSpaceId: String, townSubzoneId: String): GraphNodeComponent =
        GraphNodeComponent(
            id = townSpaceId, position = null, type = NodeType.Hub,
            neighbors = emptyList(), chunkId = townSubzoneId
        )

    fun withTownEdge(townNode: GraphNodeComponent, combatEntranceId: String): GraphNodeComponent {
        return if (townNode.neighbors.any { it.targetId == combatEntranceId }) {
            townNode
        } else {
            townNode.addEdge(EdgeData(combatEntranceId, "down", false))
        }
    }

    fun upsertTownGraphNode(
        graphNodeRepo: GraphNodeRepository,
        townSpaceId: String,
        townSubzoneId: String,
        combatEntranceId: String
    ): Result<Unit> {
        val existing = graphNodeRepo.findById(townSpaceId).getOrElse { return Result.failure(it) }
        val townNode = existing ?: defaultTownNode(townSpaceId, townSubzoneId)
        val updated = withTownEdge(townNode, combatEntranceId)
        if (existing == null) {
            graphNodeRepo.save(updated).getOrElse { return Result.failure(it) }
        } else if (updated != townNode) {
            graphNodeRepo.update(updated).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    fun updateCombatGraphNode(
        graphNodeRepo: GraphNodeRepository,
        townSpaceId: String,
        combatEntranceId: String
    ): Result<Unit> {
        val combatEdge = EdgeData(townSpaceId, "up", false)
        val combatNode = graphNodeRepo.findById(combatEntranceId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(Exception("Combat graph node not found: $combatEntranceId"))
        val updated = if (combatNode.neighbors.any { it.targetId == townSpaceId }) {
            combatNode
        } else {
            combatNode.addEdge(combatEdge)
        }
        if (updated != combatNode) {
            graphNodeRepo.update(updated).getOrElse { return Result.failure(it) }
        }
        return Result.success(Unit)
    }
}
