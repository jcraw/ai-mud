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
import kotlinx.serialization.Serializable

/**
 * JSON structure for LLM-generated mob data (MUD-034g pure move).
 */
@Serializable
internal data class MobData(
    val name: String,
    val description: String,
    val health: Int,
    val lootTableId: String = "",
    val goldDrop: Int = 0,
    val isHostile: Boolean = true,
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10
)

/**
 * Combat/skill component factory for spawned NPCs (MUD-034g pure move).
 */
internal object MobSpawnerCombat {

    fun combatSkills(skillLevel: Int): Map<String, SkillState> {
        return mapOf(
            "Melee Combat" to SkillState(
                level = skillLevel,
                xp = 0L,
                unlocked = true,
                tags = listOf("combat", "weapon", "melee")
            ),
            "Dodge" to SkillState(
                level = (skillLevel * 0.8).toInt().coerceAtLeast(1),
                xp = 0L,
                unlocked = true,
                tags = listOf("combat", "defense")
            ),
            "Parry" to SkillState(
                level = (skillLevel * 0.6).toInt().coerceAtLeast(1),
                xp = 0L,
                unlocked = true,
                tags = listOf("combat", "defense")
            )
        )
    }

    /**
     * Create V2 combat components for NPC
     * Adds CombatComponent and SkillComponent required for V2 combat system
     */
    fun createCombatComponents(
        health: Int,
        maxHealth: Int,
        difficulty: Int
    ): Map<ComponentType, Component> {
        // Create CombatComponent
        val combatComponent = CombatComponent(
            currentHp = health,
            maxHp = maxHealth
        )

        // Create SkillComponent with basic combat skills scaled to difficulty
        // Difficulty 1-20 maps to skill levels 1-20
        val skillLevel = difficulty.coerceIn(1, 20)
        val skillComponent = SkillComponent(skills = combatSkills(skillLevel))

        return mapOf(
            ComponentType.COMBAT to combatComponent,
            ComponentType.SKILL to skillComponent
        )
    }
}
