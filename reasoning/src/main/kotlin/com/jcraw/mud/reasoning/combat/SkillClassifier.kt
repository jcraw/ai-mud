package com.jcraw.mud.reasoning.combat

import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.reasoning.skill.SkillDefinitions
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Classifies which skills are relevant to a combat action with weights
 * Uses LLM for flexible, creative action interpretation
 * Falls back to hardcoded mappings if LLM fails
 */
class SkillClassifier(private val llmClient: LLMClient?) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Classify which skills apply to an action and their weights
     * Returns empty list if no skills apply
     */
    suspend fun classifySkills(
        action: String,
        entitySkills: SkillComponent
    ): List<SkillWeight> {
        // Try LLM classification first if available
        if (llmClient != null) {
            val result = tryLLMClassification(action, entitySkills)
            if (result.isNotEmpty()) {
                return normalizeWeights(result)
            }
        }

        // Fallback to hardcoded mappings
        return fallbackClassification(action, entitySkills)
    }

    /**
     * Use LLM to determine relevant skills
     */
    private suspend fun tryLLMClassification(
        action: String,
        entitySkills: SkillComponent
    ): List<SkillWeight> {
        val unlockedSkills = entitySkills.getUnlockedSkills().keys.toList()
        if (unlockedSkills.isEmpty()) {
            return emptyList()
        }

        val systemPrompt = buildSystemPrompt()
        val userContext = buildUserContext(action, unlockedSkills)

        return try {
            val response = llmClient?.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 300,
                temperature = 0.3  // Lower temperature for consistent skill classification
            ) ?: return emptyList()

            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: return emptyList()

            parseSkillWeights(content, entitySkills)
        } catch (e: Exception) {
            println("⚠️ LLM skill classification failed: ${e.message}")
            emptyList()
        }
    }

    private fun buildSystemPrompt(): String = """
        You are a skill classifier for a combat system in a text-based RPG.

        Your task is to determine which skills are relevant to a combat action and assign weights (0.0-1.0) based on relevance.

        Guidelines:
        - Only return skills that are actually relevant to the action
        - Weights should sum to approximately 1.0
        - Higher weights = more important for this action
        - Consider the specific nature of the action (e.g., "aim for eyes" uses Accuracy more than "wild swing")
        - Use 2-4 skills typically (don't include every possible skill)
        - Only use skills the entity actually has unlocked

        Return ONLY a JSON array of objects with "skill" and "weight" fields.
        Example: [{"skill": "Sword Fighting", "weight": 0.6}, {"skill": "Strength", "weight": 0.4}]

        Do NOT include any explanatory text, only the JSON array.
    """.trimIndent()

    private fun buildUserContext(action: String, unlockedSkills: List<String>): String = """
        Action: "$action"

        Available skills (entity has these unlocked):
        ${unlockedSkills.joinToString("\n") { "- $it" }}

        Which skills apply to this action and with what weights?
        Return JSON array only.
    """.trimIndent()

    /**
     * Parse LLM response into SkillWeight objects
     * Handles various JSON formats and validates skills exist
     */
    private fun parseSkillWeights(content: String, entitySkills: SkillComponent): List<SkillWeight> {
        return try {
            // Extract JSON array from response (handle markdown code blocks)
            val jsonContent = content
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val parsed = json.decodeFromString<List<SkillWeightDto>>(jsonContent)

            // Filter to skills entity actually has and normalize
            parsed
                .filter { entitySkills.hasSkill(it.skill) }
                .map { SkillWeight(it.skill, it.weight.coerceIn(0.0, 1.0)) }
        } catch (e: Exception) {
            println("⚠️ Failed to parse skill weights: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fallback classification using hardcoded weapon/action patterns
     */
    private fun fallbackClassification(
        action: String,
        entitySkills: SkillComponent
    ): List<SkillWeight> {
        val actionLower = action.lowercase()
        val weights = mutableListOf<SkillWeight>()

        // Weapon type detection
        when {
            actionLower.contains("sword") || actionLower.contains("blade") -> {
                addIfHasSkill(weights, "Sword Fighting", 0.7, entitySkills)
                addIfHasSkill(weights, "Strength", 0.3, entitySkills)
            }
            actionLower.contains("axe") -> {
                addIfHasSkill(weights, "Axe Mastery", 0.7, entitySkills)
                addIfHasSkill(weights, "Strength", 0.3, entitySkills)
            }
            actionLower.contains("bow") || actionLower.contains("arrow") -> {
                addIfHasSkill(weights, "Bow Accuracy", 0.7, entitySkills)
                addIfHasSkill(weights, "Agility", 0.3, entitySkills)
            }
            actionLower.contains("fire") -> {
                addIfHasSkill(weights, "Fire Magic", 0.8, entitySkills)
                addIfHasSkill(weights, "Intelligence", 0.2, entitySkills)
            }
            actionLower.contains("water") || actionLower.contains("ice") -> {
                addIfHasSkill(weights, "Water Magic", 0.8, entitySkills)
                addIfHasSkill(weights, "Intelligence", 0.2, entitySkills)
            }
            actionLower.contains("earth") || actionLower.contains("stone") -> {
                addIfHasSkill(weights, "Earth Magic", 0.8, entitySkills)
                addIfHasSkill(weights, "Intelligence", 0.2, entitySkills)
            }
            actionLower.contains("air") || actionLower.contains("wind") || actionLower.contains("lightning") -> {
                addIfHasSkill(weights, "Air Magic", 0.8, entitySkills)
                addIfHasSkill(weights, "Intelligence", 0.2, entitySkills)
            }
            actionLower.contains("hide") || actionLower.contains("stealth") -> {
                addIfHasSkill(weights, "Stealth", 0.8, entitySkills)
                addIfHasSkill(weights, "Agility", 0.2, entitySkills)
            }
            else -> {
                // Generic melee attack - use Strength as default
                addIfHasSkill(weights, "Strength", 1.0, entitySkills)
            }
        }

        return if (weights.isEmpty()) {
            // Last resort: use Strength if available
            if (entitySkills.hasSkill("Strength")) {
                listOf(SkillWeight("Strength", 1.0))
            } else {
                emptyList()
            }
        } else {
            normalizeWeights(weights)
        }
    }

    private fun addIfHasSkill(
        weights: MutableList<SkillWeight>,
        skillName: String,
        weight: Double,
        entitySkills: SkillComponent
    ) {
        if (entitySkills.hasSkill(skillName)) {
            weights.add(SkillWeight(skillName, weight))
        }
    }

    /**
     * Normalize weights to sum to 1.0
     */
    private fun normalizeWeights(weights: List<SkillWeight>): List<SkillWeight> {
        if (weights.isEmpty()) return emptyList()

        val sum = weights.sumOf { it.weight }
        if (sum == 0.0) return weights

        return weights.map { it.copy(weight = it.weight / sum) }
    }
}

/**
 * Result of skill classification: skill name and its weight (0.0-1.0)
 * Weights represent how much each skill contributes to the action
 */
data class SkillWeight(
    val skill: String,
    val weight: Double
)

/**
 * DTO for JSON deserialization
 */
@Serializable
private data class SkillWeightDto(
    val skill: String,
    val weight: Double
)
