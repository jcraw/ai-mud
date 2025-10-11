package com.jcraw.mud.testbot

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GameplayLoggerTest {

    @TempDir
    lateinit var tempDir: File

    private fun createLogger(): GameplayLogger {
        return GameplayLogger(outputDir = tempDir.absolutePath)
    }

    @AfterEach
    fun cleanup() {
        // Clean up temp files
        tempDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun `Logger generates unique session IDs`() {
        val logger = createLogger()
        val scenario = TestScenario.Exploration()

        val id1 = logger.generateSessionId(scenario)
        Thread.sleep(10) // Ensure different timestamp
        val id2 = logger.generateSessionId(scenario)

        assertTrue(id1.contains("exploration"))
        assertTrue(id2.contains("exploration"))
        assertTrue(id1 != id2)
    }

    @Test
    fun `Logger creates log files on first write`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val step = TestStep(
            playerInput = "look",
            gmResponse = "You see a room."
        )

        logger.logStep(sessionId, step)

        val jsonFile = File(tempDir, "$sessionId.json")
        val textFile = File(tempDir, "$sessionId.txt")

        assertTrue(jsonFile.exists())
        assertTrue(textFile.exists())
    }

    @Test
    fun `Logger appends steps to existing files`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val step1 = TestStep(playerInput = "look", gmResponse = "Room 1")
        val step2 = TestStep(playerInput = "north", gmResponse = "Room 2")

        logger.logStep(sessionId, step1)
        logger.logStep(sessionId, step2)

        val jsonFile = File(tempDir, "$sessionId.json")
        val lines = jsonFile.readLines()

        assertEquals(2, lines.size)
    }

    @Test
    fun `Logger creates report files`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val report = TestReport(
            scenario = TestScenario.Exploration(),
            totalSteps = 5,
            passedSteps = 4,
            failedSteps = 1,
            finalStatus = TestStatus.PASSED,
            startTime = Instant.now().toString(),
            endTime = Instant.now().plusMillis(5000).toString(),
            duration = 5000,
            steps = emptyList(),
            results = emptyList()
        )

        logger.logReport(sessionId, report)

        val reportFile = File(tempDir, "${sessionId}_report.json")
        val summaryFile = File(tempDir, "${sessionId}_summary.txt")

        assertTrue(reportFile.exists())
        assertTrue(summaryFile.exists())
    }

    @Test
    fun `Logger includes validation in text logs`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val step = TestStep(
            playerInput = "look",
            gmResponse = "You see a room.",
            validationResult = ValidationResult(
                pass = true,
                reason = "Output is coherent"
            )
        )

        logger.logStep(sessionId, step)

        val textFile = File(tempDir, "$sessionId.txt")
        val content = textFile.readText()

        assertTrue(content.contains("Validation"))
        assertTrue(content.contains("PASS"))
        assertTrue(content.contains("Output is coherent"))
    }

    @Test
    fun `Logger summary includes pass rate`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val report = TestReport(
            scenario = TestScenario.Combat(),
            totalSteps = 10,
            passedSteps = 8,
            failedSteps = 2,
            finalStatus = TestStatus.PASSED,
            startTime = Instant.now().toString(),
            endTime = Instant.now().plusMillis(10000).toString(),
            duration = 10000,
            steps = emptyList(),
            results = emptyList()
        )

        logger.logReport(sessionId, report)

        val summaryFile = File(tempDir, "${sessionId}_summary.txt")
        val content = summaryFile.readText()

        assertTrue(content.contains("Pass Rate: 80%"))
        assertTrue(content.contains("Passed: 8"))
        assertTrue(content.contains("Failed: 2"))
    }

    @Test
    fun `Logger handles special characters in responses`() {
        val logger = createLogger()
        val sessionId = "test_session"

        val step = TestStep(
            playerInput = "look",
            gmResponse = "You see \"quotes\" and 'apostrophes' and \n newlines."
        )

        logger.logStep(sessionId, step)

        val textFile = File(tempDir, "$sessionId.txt")
        assertTrue(textFile.exists())

        val content = textFile.readText()
        assertTrue(content.contains("quotes"))
    }
}
