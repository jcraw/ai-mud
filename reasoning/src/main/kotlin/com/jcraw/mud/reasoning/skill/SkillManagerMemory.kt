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

import com.jcraw.mud.memory.MemoryManager
import kotlinx.coroutines.runBlocking

/**
 * Shared memory write helper for SkillManager paths (MUD-034j).
 * Preserves runBlocking memory write order after event log.
 */
internal object SkillManagerMemory {
    fun remember(mm: MemoryManager?, text: String, meta: Map<String, String>) {
        mm?.let { manager ->
            runBlocking {
                manager.remember(text, metadata = meta)
            }
        }
    }
}
