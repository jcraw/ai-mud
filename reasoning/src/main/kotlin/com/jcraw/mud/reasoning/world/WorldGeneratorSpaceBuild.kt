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

import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.world.Condition
import com.jcraw.mud.core.world.ExitData
import com.jcraw.mud.core.world.TerrainType
import com.jcraw.mud.core.WorldChunkComponent
import com.jcraw.mud.core.GraphNodeComponent
import kotlin.random.Random

/**
 * Space assembly helpers for V2 generateSpace / V3 stubs (MUD-034g pure move).
 * Preserves RNG call order: traps → resources → per-exit hidden rolls (V2 path).
 */
internal object WorldGeneratorSpaceBuild {

    fun maybeHiddenExit(exitJson: ExitDataJson, difficultyLevel: Int): ExitData {
        return if (Random.nextDouble() < WorldGeneratorConsts.HIDDEN_EXIT_PROBABILITY) {
            ExitData(
                targetId = exitJson.targetId,
                direction = exitJson.direction,
                description = exitJson.description,
                conditions = listOf(
                    Condition.SkillCheck(
                        skill = "Perception",
                        difficulty = 10 + difficultyLevel
                    )
                ),
                isHidden = true
            )
        } else {
            ExitData(
                targetId = exitJson.targetId,
                direction = exitJson.direction,
                description = exitJson.description
            )
        }
    }

    fun buildExitsFromSpaceData(
        spaceData: SpaceData,
        difficultyLevel: Int
    ): List<ExitData> {
        return spaceData.exits.map { exitJson ->
            maybeHiddenExit(exitJson, difficultyLevel)
        }
    }

    fun maybeGenerateTraps(theme: String, difficultyLevel: Int): List<com.jcraw.mud.core.world.TrapData> {
        return if (Random.nextDouble() < WorldGeneratorConsts.TRAP_PROBABILITY) {
            listOf(WorldGeneratorTrapResource.generateTrap(theme, difficultyLevel))
        } else {
            emptyList()
        }
    }

    fun maybeGenerateResources(theme: String): List<com.jcraw.mud.core.world.ResourceNode> {
        return if (Random.nextDouble() < WorldGeneratorConsts.RESOURCE_PROBABILITY) {
            listOf(WorldGeneratorTrapResource.generateResource(theme))
        } else {
            emptyList()
        }
    }

    fun assembleV2Space(
        spaceData: SpaceData,
        parentSubzone: WorldChunkComponent
    ): SpacePropertiesComponent {
        val traps = maybeGenerateTraps(parentSubzone.biomeTheme, parentSubzone.difficultyLevel)
        val resources = maybeGenerateResources(parentSubzone.biomeTheme)
        val exits = buildExitsFromSpaceData(spaceData, parentSubzone.difficultyLevel)

        return SpacePropertiesComponent(
            description = spaceData.description,
            exits = exits,
            brightness = spaceData.brightness.coerceIn(0, 100),
            terrainType = WorldGeneratorNodeProps.parseTerrainType(spaceData.terrainType),
            traps = traps,
            resources = resources,
            entities = emptyList(), // Populated later via MobSpawner
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
    }

    fun graphNodeExits(graphNode: GraphNodeComponent): List<ExitData> {
        return graphNode.neighbors.map { edge ->
            ExitData(
                targetId = edge.targetId,
                direction = edge.direction,
                description = "", // Lazy-fill
                conditions = edge.conditions,
                isHidden = edge.hidden
            )
        }
    }

    fun assembleSpaceStub(
        graphNode: GraphNodeComponent,
        chunk: WorldChunkComponent
    ): SpacePropertiesComponent {
        val exits = graphNodeExits(graphNode)

        // Probabilistic trap/resource generation (same as V2)
        val traps = maybeGenerateTraps(chunk.biomeTheme, chunk.difficultyLevel)
        val resources = maybeGenerateResources(chunk.biomeTheme)

        return SpacePropertiesComponent(
            description = "", // LAZY-FILL: Will be generated on first visit
            exits = exits,
            brightness = 50, // Default brightness, can be adjusted by node type
            terrainType = TerrainType.NORMAL,
            traps = traps,
            resources = resources,
            entities = emptyList(), // Populated later via MobSpawner
            itemsDropped = emptyList(),
            stateFlags = emptyMap()
        )
    }
}
