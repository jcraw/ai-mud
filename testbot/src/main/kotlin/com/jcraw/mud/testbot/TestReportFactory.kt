@file:Suppress("ReturnCount", "MagicNumber", "MaxLineLength", "TooManyFunctions", "LongMethod", "ComplexCondition", "CyclomaticComplexMethod", "NestedBlockDepth", "LongParameterList", "UnusedParameter", "TooGenericExceptionCaught")

package com.jcraw.mud.testbot

import java.time.Instant

/**
 * Builds [TestReport] from [TestState].
 * Pure-moved from TestReport companion (MUD-034f).
 */
internal object TestReportFactory {

    fun fromTestState(state: TestState, startTime: Instant, endTime: Instant): TestReport {
        val metrics = collectMetrics(state.steps)
        return buildReport(state, startTime, endTime, metrics)
    }

    private fun buildReport(
        state: TestState,
        startTime: Instant,
        endTime: Instant,
        metrics: Metrics
    ): TestReport = TestReport(
        scenario = state.scenario,
        totalSteps = state.steps.size,
        passedSteps = state.results.count { it.passed },
        failedSteps = state.results.count { !it.passed },
        finalStatus = state.finalStatus,
        startTime = startTime.toString(),
        endTime = endTime.toString(),
        duration = endTime.toEpochMilli() - startTime.toEpochMilli(),
        steps = state.steps,
        results = state.results,
        uniqueRoomsVisited = metrics.roomNames.size,
        roomNames = metrics.roomNames.toList(),
        damageTaken = metrics.damageTaken,
        npcsKilled = metrics.npcsKilled,
        combatRounds = metrics.combatRounds,
        skillChecksPassed = metrics.skillChecksPassed,
        socialChecksPassed = metrics.socialChecksPassed,
        playerDied = metrics.playerDied
    )

    private data class Metrics(
        val roomNames: Set<String>,
        val damageTaken: Int,
        val npcsKilled: Int,
        val combatRounds: Int,
        val skillChecksPassed: Int,
        val socialChecksPassed: Int,
        val playerDied: Boolean
    )

    private fun collectMetrics(steps: List<TestStep>): Metrics = Metrics(
        roomNames = TestReportMetrics.extractRoomsFromSteps(steps),
        damageTaken = TestReportMetrics.calculateDamageTaken(steps),
        npcsKilled = TestReportMetrics.countNPCsKilled(steps),
        combatRounds = TestReportMetrics.countCombatRounds(steps),
        skillChecksPassed = TestReportMetrics.countSkillChecksPassed(steps),
        socialChecksPassed = TestReportMetrics.countSocialChecksPassed(steps),
        playerDied = TestReportMetrics.checkPlayerDied(steps)
    )
}
