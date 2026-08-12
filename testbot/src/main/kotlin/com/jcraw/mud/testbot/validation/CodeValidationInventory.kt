@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot.validation

import com.jcraw.mud.testbot.TestStep

/**
 * Inventory tracking from test history (MUD-034f).
 */
internal object CodeValidationInventory {

    fun trackInventoryFromHistory(history: List<TestStep>): Set<String> {
        val inventory = mutableSetOf<String>()
        for (step in history) {
            applyTake(step, inventory)
            applyDrop(step, inventory)
        }
        return inventory
    }

    private fun applyTake(step: TestStep, inventory: MutableSet<String>) {
        val takeMatch = Regex("(?:take|get|pickup)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(step.playerInput) ?: return
        if (step.gmResponse.contains("You take", ignoreCase = true) ||
            step.gmResponse.contains("You pick up", ignoreCase = true)
        ) {
            inventory.add(takeMatch.groupValues[1].trim().lowercase())
        }
    }

    private fun applyDrop(step: TestStep, inventory: MutableSet<String>) {
        val dropMatch = Regex("(?:drop)\\s+(.+)", RegexOption.IGNORE_CASE)
            .find(step.playerInput) ?: return
        if (step.gmResponse.contains("You drop", ignoreCase = true)) {
            inventory.remove(dropMatch.groupValues[1].trim().lowercase())
        }
    }
}
