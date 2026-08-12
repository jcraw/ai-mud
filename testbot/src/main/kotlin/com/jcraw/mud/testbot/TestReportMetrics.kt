@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Playthrough metric extraction from test steps.
 * Pure-moved from TestReport companion (MUD-034f).
 */
internal object TestReportMetrics {

    fun extractRoomsFromSteps(steps: List<TestStep>): Set<String> {
        val roomNames = mutableSetOf<String>()
        val roomPattern = Regex("^([A-Z][a-zA-Z\\s]+)\\n", RegexOption.MULTILINE)

        for (step in steps) {
            val match = roomPattern.find(step.gmResponse)
            if (match != null) {
                val roomName = match.groupValues[1].trim()
                // Filter out common non-room patterns
                if (roomName.length > 3 && !roomName.startsWith("You ") && !roomName.startsWith("The ")) {
                    roomNames.add(roomName)
                }
            }
        }

        return roomNames
    }

    fun calculateDamageTaken(steps: List<TestStep>): Int {
        var totalDamage = 0
        val damagePattern = Regex("(?:retaliates|strikes|hits|deals).+?(\\d+)\\s+damage", RegexOption.IGNORE_CASE)

        for (step in steps) {
            // Look for NPC damage to player (retaliation)
            if (step.gmResponse.contains("retaliate", ignoreCase = true)) {
                val match = damagePattern.find(step.gmResponse)
                if (match != null) {
                    totalDamage += match.groupValues[1].toIntOrNull() ?: 0
                }
            }
        }

        return totalDamage
    }

    fun countNPCsKilled(steps: List<TestStep>): Int {
        var killCount = 0

        for (step in steps) {
            // Look for kill/defeat messages
            if (step.gmResponse.contains("has been defeated", ignoreCase = true) ||
                step.gmResponse.contains("slain", ignoreCase = true) ||
                step.gmResponse.contains("falls dead", ignoreCase = true)
            ) {
                killCount++
            }
        }

        return killCount
    }

    fun countCombatRounds(steps: List<TestStep>): Int {
        var roundCount = 0
        for (step in steps) {
            if (isAttackCommand(step.playerInput) && isCombatResponse(step.gmResponse)) {
                roundCount++
            }
        }
        return roundCount
    }

    private fun isAttackCommand(input: String): Boolean {
        return input.contains("attack", ignoreCase = true) ||
            input.contains("fight", ignoreCase = true) ||
            input.contains("hit", ignoreCase = true) ||
            input.contains("kill", ignoreCase = true)
    }

    private fun isCombatResponse(response: String): Boolean {
        return response.contains("damage", ignoreCase = true) ||
            response.contains("hit", ignoreCase = true) ||
            response.contains("strike", ignoreCase = true) ||
            response.contains("attack", ignoreCase = true) ||
            response.contains("combat", ignoreCase = true) ||
            response.contains("retaliate", ignoreCase = true)
    }

    fun countSkillChecksPassed(steps: List<TestStep>): Int {
        var checkCount = 0

        for (step in steps) {
            // Check if this was a "check" command that succeeded
            if (step.playerInput.startsWith("check", ignoreCase = true) &&
                (step.gmResponse.contains("Success!", ignoreCase = true) ||
                    step.gmResponse.contains("succeed", ignoreCase = true))
            ) {
                checkCount++
            }
        }

        return checkCount
    }

    fun countSocialChecksPassed(steps: List<TestStep>): Int {
        var socialCount = 0

        for (step in steps) {
            // Check if this was a social command that succeeded
            if ((step.playerInput.contains("persuade", ignoreCase = true) ||
                    step.playerInput.contains("intimidate", ignoreCase = true)) &&
                (step.gmResponse.contains("Success!", ignoreCase = true) ||
                    step.gmResponse.contains("succeed", ignoreCase = true))
            ) {
                socialCount++
            }
        }

        return socialCount
    }

    fun checkPlayerDied(steps: List<TestStep>): Boolean {
        for (step in steps) {
            if (step.gmResponse.contains("You have died", ignoreCase = true) ||
                step.gmResponse.contains("You fall", ignoreCase = true) ||
                step.gmResponse.contains("You are dead", ignoreCase = true) ||
                step.gmResponse.contains("death", ignoreCase = true) &&
                step.gmResponse.contains("you", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }
}
