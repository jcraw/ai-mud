@file:Suppress("ReturnCount", "FunctionOnlyReturningConstant")

package com.jcraw.mud.reasoning.interact

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SkillCheckResult

/**
 * Harvest tool check + skill-check outcome. Shared by console and GUI (MUD-039).
 * Loot add / weight / IO stay in handler wrappers.
 */
object HarvestSupport {

    sealed class CheckOutcome {
        data object NoChallenge : CheckOutcome()
        data object Failed : CheckOutcome()
        data class Passed(val result: SkillCheckResult) : CheckOutcome()
    }

    fun hasRequiredTool(
        items: List<ItemInstance>?,
        requiredToolTag: String?,
        lookup: (String) -> ItemTemplate?
    ): Boolean {
        if (requiredToolTag == null) return true
        return items?.any { instance ->
            lookup(instance.templateId)?.tags?.contains(requiredToolTag) == true
        } ?: false
    }

    fun missingToolMessage(requiredToolTag: String): String =
        "You need a ${requiredToolTag.replace("_", " ")} to harvest this."

    fun nothingToHarvestMessage(): String = "There's nothing to harvest from that."

    fun alreadyHarvestedMessage(): String = "This resource has already been harvested."

    fun validateHarvestTarget(feature: Entity.Feature): String? {
        if (feature.lootTableId == null) return nothingToHarvestMessage()
        if (feature.isCompleted) return alreadyHarvestedMessage()
        return null
    }

    fun successXp(): Long = 50L

    fun failXp(): Long = 25L

    fun xpGainedLine(skillName: String, xpAmount: Long, currentXp: Long, currentLevel: Int): String =
        "+$xpAmount XP to $skillName ($currentXp total, level $currentLevel)"
}
