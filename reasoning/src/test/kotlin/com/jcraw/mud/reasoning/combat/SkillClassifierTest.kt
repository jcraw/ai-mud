package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.sophia.llm.LLMClient
import com.jcraw.sophia.llm.OpenAIResponse
import com.jcraw.sophia.llm.OpenAIChoice
import com.jcraw.sophia.llm.OpenAIMessage
import com.jcraw.sophia.llm.OpenAIUsage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SkillClassifierTest {

    private fun createSkillComponent(vararg skills: String): SkillComponent {
        val skillMap = skills.associateWith {
            SkillState(level = 10, xp = 0, unlocked = true)
        }
        return SkillComponent(skills = skillMap)
    }

    @Test
    fun `fallback classification for sword attack uses Sword Fighting and Strength`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Sword Fighting", "Strength", "Agility")

        val result = classifier.classifySkills("swing sword at goblin", skills)

        assertEquals(2, result.size)
        assertTrue(result.any { it.skill == "Sword Fighting" && it.weight > 0.5 })
        assertTrue(result.any { it.skill == "Strength" })
        // Weights should sum to 1.0 (normalized)
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for axe attack uses Axe Mastery`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Axe Mastery", "Strength")

        val result = classifier.classifySkills("chop with axe", skills)

        assertTrue(result.any { it.skill == "Axe Mastery" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for bow attack uses Bow Accuracy`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Bow Accuracy", "Agility")

        val result = classifier.classifySkills("shoot arrow at enemy", skills)

        assertTrue(result.any { it.skill == "Bow Accuracy" })
        assertTrue(result.any { it.skill == "Agility" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for fire magic uses Fire Magic and Intelligence`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Fire Magic", "Intelligence")

        val result = classifier.classifySkills("cast fireball", skills)

        assertTrue(result.any { it.skill == "Fire Magic" && it.weight > 0.5 })
        assertTrue(result.any { it.skill == "Intelligence" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for water magic uses Water Magic`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Water Magic", "Intelligence")

        val result = classifier.classifySkills("summon ice spike", skills)

        assertTrue(result.any { it.skill == "Water Magic" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for earth magic uses Earth Magic`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Earth Magic", "Intelligence")

        val result = classifier.classifySkills("raise stone barrier", skills)

        assertTrue(result.any { it.skill == "Earth Magic" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for air magic uses Air Magic`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Air Magic", "Intelligence")

        val result = classifier.classifySkills("lightning bolt", skills)

        assertTrue(result.any { it.skill == "Air Magic" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for stealth uses Stealth and Agility`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Stealth", "Agility")

        val result = classifier.classifySkills("hide in shadows", skills)

        assertTrue(result.any { it.skill == "Stealth" })
        assertTrue(result.any { it.skill == "Agility" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification for generic action defaults to Strength`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = createSkillComponent("Strength", "Vitality")

        val result = classifier.classifySkills("punch the wall", skills)

        assertTrue(result.any { it.skill == "Strength" })
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    @Test
    fun `fallback classification returns only available skills`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        // Entity only has Strength, not Sword Fighting
        val skills = createSkillComponent("Strength")

        val result = classifier.classifySkills("swing sword at goblin", skills)

        // Should only return Strength since Sword Fighting isn't available
        assertEquals(1, result.size)
        assertEquals("Strength", result.first().skill)
        assertEquals(1.0, result.first().weight, 0.01)
    }

    @Test
    fun `fallback classification returns empty list when no applicable skills`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        // Entity has no combat skills
        val skills = createSkillComponent("Diplomacy", "Blacksmithing")

        val result = classifier.classifySkills("attack enemy", skills)

        // Should return empty since no combat skills available
        assertTrue(result.isEmpty())
    }

    @Test
    fun `LLM classification parses valid JSON response`() = runBlocking {
        val mockLLM = MockSkillClassifierLLM(
            jsonResponse = """[{"skill": "Sword Fighting", "weight": 0.7}, {"skill": "Strength", "weight": 0.3}]"""
        )

        val classifier = SkillClassifier(mockLLM)
        val skills = createSkillComponent("Sword Fighting", "Strength", "Agility")

        val result = classifier.classifySkills("swing sword", skills)

        assertEquals(2, result.size)
        assertEquals("Sword Fighting", result[0].skill)
        assertEquals(0.7, result[0].weight, 0.01)
        assertEquals("Strength", result[1].skill)
        assertEquals(0.3, result[1].weight, 0.01)
    }

    @Test
    fun `LLM classification handles JSON in code blocks`() = runBlocking {
        val mockLLM = MockSkillClassifierLLM(
            jsonResponse = """```json
[{"skill": "Fire Magic", "weight": 0.8}, {"skill": "Intelligence", "weight": 0.2}]
```"""
        )

        val classifier = SkillClassifier(mockLLM)
        val skills = createSkillComponent("Fire Magic", "Intelligence")

        val result = classifier.classifySkills("cast fireball", skills)

        assertEquals(2, result.size)
        assertEquals("Fire Magic", result[0].skill)
        assertEquals("Intelligence", result[1].skill)
    }

    @Test
    fun `LLM classification normalizes weights to sum to 1`() = runBlocking {
        // Weights sum to 1.5, should be normalized
        val mockLLM = MockSkillClassifierLLM(
            jsonResponse = """[{"skill": "Sword Fighting", "weight": 0.9}, {"skill": "Strength", "weight": 0.6}]"""
        )

        val classifier = SkillClassifier(mockLLM)
        val skills = createSkillComponent("Sword Fighting", "Strength")

        val result = classifier.classifySkills("swing sword", skills)

        // Weights should be normalized to sum to 1.0
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
        // Ratios should be preserved: 0.9/1.5 = 0.6, 0.6/1.5 = 0.4
        assertEquals(0.6, result.find { it.skill == "Sword Fighting" }?.weight ?: 0.0, 0.01)
        assertEquals(0.4, result.find { it.skill == "Strength" }?.weight ?: 0.0, 0.01)
    }

    @Test
    fun `LLM classification filters out skills entity doesn't have`() = runBlocking {
        // LLM suggests Sword Fighting, but entity doesn't have it
        val mockLLM = MockSkillClassifierLLM(
            jsonResponse = """[{"skill": "Sword Fighting", "weight": 0.7}, {"skill": "Strength", "weight": 0.3}]"""
        )

        val classifier = SkillClassifier(mockLLM)
        // Entity only has Strength
        val skills = createSkillComponent("Strength", "Vitality")

        val result = classifier.classifySkills("swing sword", skills)

        // Should only include Strength, not Sword Fighting
        assertEquals(1, result.size)
        assertEquals("Strength", result.first().skill)
        assertEquals(1.0, result.first().weight, 0.01)
    }

    @Test
    fun `LLM classification falls back on exception`() = runBlocking {
        val failingLLM = FailingMockLLMClient()

        val classifier = SkillClassifier(failingLLM)
        val skills = createSkillComponent("Sword Fighting", "Strength")

        val result = classifier.classifySkills("swing sword at goblin", skills)

        // Should fall back to hardcoded classification
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.skill == "Sword Fighting" })
    }

    @Test
    fun `LLM classification falls back on invalid JSON`() = runBlocking {
        val mockLLM = MockSkillClassifierLLM(jsonResponse = "This is not valid JSON")

        val classifier = SkillClassifier(mockLLM)
        val skills = createSkillComponent("Sword Fighting", "Strength")

        val result = classifier.classifySkills("swing sword", skills)

        // Should fall back to hardcoded classification
        assertTrue(result.isNotEmpty())
        assertTrue(result.any { it.skill == "Sword Fighting" })
    }

    @Test
    fun `classification with empty skill list returns empty result`() = runBlocking {
        val classifier = SkillClassifier(llmClient = null)
        val skills = SkillComponent()  // Empty skills

        val result = classifier.classifySkills("attack", skills)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `weights are coerced to 0-1 range`() = runBlocking {
        // Invalid weights outside 0-1 range
        val mockLLM = MockSkillClassifierLLM(
            jsonResponse = """[{"skill": "Strength", "weight": 1.5}, {"skill": "Agility", "weight": -0.3}]"""
        )

        val classifier = SkillClassifier(mockLLM)
        val skills = createSkillComponent("Strength", "Agility")

        val result = classifier.classifySkills("attack", skills)

        // Weights should be coerced and normalized
        result.forEach { skillWeight ->
            assertTrue(skillWeight.weight >= 0.0 && skillWeight.weight <= 1.0)
        }
        assertEquals(1.0, result.sumOf { it.weight }, 0.01)
    }

    /**
     * Mock LLM client that returns a configurable JSON response
     */
    private class MockSkillClassifierLLM(private val jsonResponse: String) : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            return OpenAIResponse(
                id = "test-id",
                `object` = "chat.completion",
                created = 0L,
                model = modelId,
                choices = listOf(
                    OpenAIChoice(
                        message = OpenAIMessage("assistant", jsonResponse),
                        finishReason = "stop"
                    )
                ),
                usage = OpenAIUsage(
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150
                )
            )
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            return List(1536) { 0.1 }
        }

        override fun close() {}
    }

    /**
     * Mock LLM client that always fails
     */
    private class FailingMockLLMClient : LLMClient {
        override suspend fun chatCompletion(
            modelId: String,
            systemPrompt: String,
            userContext: String,
            maxTokens: Int,
            temperature: Double
        ): OpenAIResponse {
            throw RuntimeException("Simulated LLM failure")
        }

        override suspend fun createEmbedding(text: String, model: String): List<Double> {
            throw RuntimeException("Simulated embedding failure")
        }

        override fun close() {}
    }
}
