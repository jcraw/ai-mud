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
import com.jcraw.mud.core.SkillEvent
import com.jcraw.mud.core.SkillState

/**
 * Lucky progression path for attemptSkillProgress (MUD-034j).
 * Returns events on lucky success; null if lucky roll failed.
 */
internal object SkillManagerAttemptLucky {
    fun tryLucky(
        ctx: SkillManagerCtx,
        entityId: String,
        skillName: String,
        component: SkillComponent,
        current: SkillState,
        luckyChance: Int,
        roll: Int
    ): List<SkillEvent>? {
        if (roll > luckyChance) return null
        val events = mutableListOf<SkillEvent>()
        val updated = SkillManagerAttemptLuckyPersist.apply(
            ctx, entityId, skillName, component, current
        )
        SkillManagerAttemptLuckyEvents.unlockIfNeeded(
            events, ctx, entityId, skillName, current
        )
        SkillManagerAttemptLuckyEvents.levelUp(
            events, ctx, entityId, skillName, current, updated, luckyChance
        )
        return events
    }
}
