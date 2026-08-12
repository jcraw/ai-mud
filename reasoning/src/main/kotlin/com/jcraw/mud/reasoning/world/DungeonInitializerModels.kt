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

/**
 * Result data for Ancient Abyss initialization (MUD-034g pure move).
 */
data class AncientAbyssData(
    val worldId: String,
    val townSpaceId: String,
    val regions: Map<String, String> // region name -> region ID
)

data class CombatSubzoneResult(
    val entranceSpaceId: String,
    val subzoneId: String
)

interface DungeonInitializerContract {
    suspend fun initializeDeepDungeon(seed: String): Result<String>
}

/**
 * Region generation spec for deep dungeon / Ancient Abyss (MUD-034g).
 */
internal data class RegionSpec(
    val name: String,
    val description: String,
    val difficulty: Int,
    val theme: String? = null // Optional biome theme override
)
