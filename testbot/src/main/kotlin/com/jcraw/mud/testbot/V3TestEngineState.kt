@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import com.jcraw.mud.core.WorldState
import com.jcraw.mud.memory.item.SQLiteItemRepository
import com.jcraw.mud.reasoning.skill.SkillManager

/**
 * Mutable runtime state for [V3TestGameEngine] handlers (MUD-034f).
 */
internal class V3TestEngineState(
    var worldState: WorldState,
    var running: Boolean,
    val itemRepository: SQLiteItemRepository,
    val skillManager: SkillManager
)
