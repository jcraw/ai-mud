@file:Suppress(
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "ReturnCount",
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
    "NestedBlockDepth",
    "TooManyFunctions"
)

package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.repository.SkillRepository
import com.jcraw.mud.memory.MemoryManager
import kotlin.random.Random

/**
 * Explicit deps for SkillManager apply objects (MUD-034j pure-move).
 */
internal data class SkillManagerCtx(
    val skillRepo: SkillRepository,
    val getComponent: (String) -> SkillComponent,
    val updateComponent: (String, SkillComponent) -> Result<Unit>,
    val memoryManager: MemoryManager?,
    val rng: Random
)
