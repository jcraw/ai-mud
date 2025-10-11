package com.jcraw.mud.testbot

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

/**
 * Logs gameplay sessions to files in both JSON and human-readable formats.
 * Supports atomic writes for thread safety in future multi-bot scenarios.
 */
class GameplayLogger(
    private val outputDir: String = "test-logs",
    private val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
) {
    init {
        // Ensure output directory exists
        File(outputDir).mkdirs()
    }

    /**
     * Log a single step to both JSON and text files.
     */
    fun logStep(sessionId: String, step: TestStep) {
        val jsonFile = getJsonFile(sessionId)
        val textFile = getTextFile(sessionId)

        // Append to JSON log (array of steps)
        appendJsonStep(jsonFile, step)

        // Append to human-readable log
        appendTextStep(textFile, step)
    }

    /**
     * Log a complete test report at the end of a session.
     */
    fun logReport(sessionId: String, report: TestReport) {
        val reportFile = getReportFile(sessionId)
        val jsonText = json.encodeToString(report)
        reportFile.writeText(jsonText)

        // Also create a summary text file
        val summaryFile = getSummaryFile(sessionId)
        val summaryText = buildSummary(report)
        summaryFile.writeText(summaryText)
    }

    /**
     * Get the session ID for a test run (timestamp-based).
     */
    fun generateSessionId(scenario: TestScenario): String {
        val timestamp = System.currentTimeMillis()
        return "${scenario.name}_$timestamp"
    }

    private fun getJsonFile(sessionId: String): File {
        return Paths.get(outputDir, "$sessionId.json").toFile()
    }

    private fun getTextFile(sessionId: String): File {
        return Paths.get(outputDir, "$sessionId.txt").toFile()
    }

    private fun getReportFile(sessionId: String): File {
        return Paths.get(outputDir, "${sessionId}_report.json").toFile()
    }

    private fun getSummaryFile(sessionId: String): File {
        return Paths.get(outputDir, "${sessionId}_summary.txt").toFile()
    }

    private fun appendJsonStep(file: File, step: TestStep) {
        synchronized(this) {
            val jsonText = json.encodeToString(step)
            file.appendText(jsonText + "\n")
        }
    }

    private fun appendTextStep(file: File, step: TestStep) {
        synchronized(this) {
            val textBlock = buildTextBlock(step)
            file.appendText(textBlock + "\n")
        }
    }

    private fun buildTextBlock(step: TestStep): String {
        val validation = step.validationResult?.let { result ->
            """
            Validation: ${if (result.pass) "PASS" else "FAIL"}
            Reason: ${result.reason}
            """.trimIndent()
        } ?: ""

        return """
        ================================================================================
        [${step.timestamp}]

        Player: ${step.playerInput}

        Game Master:
        ${step.gmResponse}

        $validation
        """.trimIndent()
    }

    private fun buildSummary(report: TestReport): String {
        val passRate = if (report.totalSteps > 0) {
            (report.passedSteps.toDouble() / report.totalSteps * 100).toInt()
        } else {
            0
        }

        val durationSeconds = report.duration / 1000.0

        return """
        ================================================================================
        TEST REPORT SUMMARY
        ================================================================================

        Scenario: ${report.scenario.name}
        Description: ${report.scenario.description}

        Status: ${report.finalStatus}

        Results:
        - Total Steps: ${report.totalSteps}
        - Passed: ${report.passedSteps}
        - Failed: ${report.failedSteps}
        - Pass Rate: $passRate%

        Timing:
        - Start: ${report.startTime}
        - End: ${report.endTime}
        - Duration: ${"%.2f".format(durationSeconds)}s

        ================================================================================
        STEP DETAILS
        ================================================================================

        ${report.results.joinToString("\n\n") { result ->
            val status = if (result.passed) "✓ PASS" else "✗ FAIL"
            """
            Step ${result.stepNumber}: $status
            Input: ${result.step.playerInput}
            Reason: ${result.reason}
            """.trimIndent()
        }}

        ================================================================================
        END OF REPORT
        ================================================================================
        """.trimIndent()
    }
}
