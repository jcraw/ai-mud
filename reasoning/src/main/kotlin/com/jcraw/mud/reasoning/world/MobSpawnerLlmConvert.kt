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

import com.jcraw.mud.core.*
import java.util.UUID

/**
 * Convert LLM [MobData] rows to [Entity.NPC] (MUD-034g pure move).
 */
internal object MobSpawnerLlmConvert {

    fun coerceStats(mobData: MobData): Stats = Stats(
        strength = mobData.strength.coerceIn(3, 20),
        dexterity = mobData.dexterity.coerceIn(3, 20),
        constitution = mobData.constitution.coerceIn(3, 20),
        intelligence = mobData.intelligence.coerceIn(3, 20),
        wisdom = mobData.wisdom.coerceIn(3, 20),
        charisma = mobData.charisma.coerceIn(3, 20)
    )

    fun lootTableIdFor(mobData: MobData, theme: String, difficulty: Int): String {
        return mobData.lootTableId.ifBlank {
            "${theme.lowercase().replace(" ", "_")}_$difficulty"
        }
    }

    fun mobDataToNpc(mobData: MobData, theme: String, difficulty: Int): Entity.NPC {
        val health = mobData.health.coerceAtLeast(1)
        val components = MobSpawnerCombat.createCombatComponents(health, health, difficulty)
        val npc = Entity.NPC(
            id = "npc_${UUID.randomUUID()}",
            name = mobData.name,
            description = mobData.description,
            isHostile = mobData.isHostile,
            health = health,
            maxHealth = health,
            stats = coerceStats(mobData),
            lootTableId = lootTableIdFor(mobData, theme, difficulty),
            goldDrop = mobData.goldDrop.coerceAtLeast(0),
            components = components
        )
        println("[MOB SPAWN DEBUG] Created NPC: ${npc.name} (health=${npc.health}/${npc.maxHealth}, components=${npc.components.keys})")
        return npc
    }
}
