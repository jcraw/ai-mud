@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

/**
 * Console summary printing for test runs (MUD-034f).
 */
internal object TestBotSummary {

    fun printSummary(report: TestReport) {
        val passRate = if (report.totalSteps > 0) {
            (report.passedSteps.toDouble() / report.totalSteps * 100).toInt()
        } else {
            0
        }
        val statusEmoji = when (report.finalStatus) {
            TestStatus.PASSED -> "✅"
            TestStatus.FAILED -> "❌"
            TestStatus.ERROR -> "⚠️"
            TestStatus.RUNNING -> "🔄"
        }
        println("\n" + "=".repeat(60))
        println("$statusEmoji TEST COMPLETE: ${report.scenario.name}")
        println("=".repeat(60))
        println("Status: ${report.finalStatus}")
        println("Steps: ${report.totalSteps} (${report.passedSteps} passed, ${report.failedSteps} failed)")
        println("Pass Rate: $passRate%")
        printExplorationMetrics(report)
        printPlaythroughMetrics(report)
        println("\nDuration: ${report.duration / 1000.0}s")
        println("=".repeat(60))
    }

    private fun printExplorationMetrics(report: TestReport) {
        if (report.scenario is TestScenario.Exploration) {
            println("Rooms Visited: ${report.uniqueRoomsVisited} / ${report.scenario.targetRoomsToVisit}")
            println("Rooms: ${report.roomNames.joinToString(", ")}")
        }
    }

    private fun printPlaythroughMetrics(report: TestReport) {
        when (report.scenario) {
            is TestScenario.BadPlaythrough -> printBadMetrics(report)
            is TestScenario.BruteForcePlaythrough -> printBruteMetrics(report)
            is TestScenario.SmartPlaythrough -> printSmartMetrics(report)
            else -> printGenericMetrics(report)
        }
    }

    private fun printBadMetrics(report: TestReport) {
        println("\n📊 Playthrough Metrics:")
        println("  Damage Taken: ${report.damageTaken}")
        println("  NPCs Killed: ${report.npcsKilled}")
        println("  Player Died: ${if (report.playerDied) "✅ YES (as expected)" else "❌ NO (game too easy!)"}")
        println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
        if (!report.playerDied) {
            println("\n⚠️  WARNING: Player should die without gear! Difficulty may be too low.")
        }
    }

    private fun printBruteMetrics(report: TestReport) {
        println("\n📊 Playthrough Metrics:")
        println("  Damage Taken: ${report.damageTaken}")
        println("  NPCs Killed: ${report.npcsKilled} ${if (report.npcsKilled > 0) "✅" else "❌"}")
        println("  Skill Checks Passed: ${report.skillChecksPassed}")
        println("  Player Died: ${if (!report.playerDied) "✅ NO (victory!)" else "❌ YES (should win with gear!)"}")
        println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
        if (report.playerDied) {
            println("\n⚠️  WARNING: Player should win with proper gear! Difficulty may be too high.")
        }
    }

    private fun printSmartMetrics(report: TestReport) {
        println("\n📊 Playthrough Metrics:")
        println("  Damage Taken: ${report.damageTaken} ${if (report.damageTaken < 20) "✅ (minimal)" else "⚠️ (high)"}")
        println("  NPCs Killed: ${report.npcsKilled} ${if (report.npcsKilled == 0) "✅ (non-lethal!)" else "⚠️"}")
        println("  Skill Checks Passed: ${report.skillChecksPassed}")
        println("  Social Checks Passed: ${report.socialChecksPassed} ${if (report.socialChecksPassed > 0) "✅" else "❌"}")
        println("  Player Died: ${if (!report.playerDied) "✅ NO" else "❌ YES"}")
        println("  Rooms Visited: ${report.uniqueRoomsVisited} (${report.roomNames.joinToString(", ")})")
        if (report.socialChecksPassed == 0) {
            println("\n⚠️  WARNING: No social checks passed! Multiple solution paths may not be working.")
        }
    }

    private fun printGenericMetrics(report: TestReport) {
        if (!hasMetrics(report)) return
        println("\n📊 Metrics:")
        if (report.damageTaken > 0) println("  Damage Taken: ${report.damageTaken}")
        if (report.npcsKilled > 0) println("  NPCs Killed: ${report.npcsKilled}")
        if (report.skillChecksPassed > 0) println("  Skill Checks Passed: ${report.skillChecksPassed}")
        if (report.socialChecksPassed > 0) println("  Social Checks Passed: ${report.socialChecksPassed}")
        if (report.playerDied) println("  Player Died: YES")
    }

    private fun hasMetrics(report: TestReport): Boolean =
        report.damageTaken > 0 || report.npcsKilled > 0 ||
            report.skillChecksPassed > 0 || report.socialChecksPassed > 0
}
