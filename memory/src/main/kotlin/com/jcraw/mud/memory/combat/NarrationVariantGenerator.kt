package com.jcraw.mud.memory.combat

import com.jcraw.mud.memory.MemoryManager
import com.jcraw.sophia.llm.LLMClient

/**
 * Pre-generates combat narration variants for common scenarios.
 * This is typically run offline to populate the vector database with diverse
 * narration options that can be retrieved quickly during gameplay.
 *
 * Generated variants are tagged with metadata (weapon type, damage tier, outcome)
 * to enable semantic search via vector DB.
 */
class NarrationVariantGenerator(
    private val llmClient: LLMClient,
    private val memoryManager: MemoryManager
) {

    /**
     * Generates and stores narration variants for all common combat scenarios.
     */
    suspend fun generateAllVariants() {
        generateMeleeHitVariants()
        generateMeleeMissVariants()
        generateRangedHitVariants()
        generateRangedMissVariants()
        generateSpellVariants()
        generateCriticalHitVariants()
        generateStatusEffectVariants()
        generateDeathBlowVariants()
    }

    /**
     * Generates 10 variants for melee weapon hits.
     * Covers sword, axe, mace, dagger attacks with low/medium/high damage tiers.
     */
    private suspend fun generateMeleeHitVariants() {
        val weapons = listOf("sword", "axe", "mace", "dagger", "spear")
        val damageTiers = listOf("low", "medium", "high")

        for (weapon in weapons) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    val narrative = generateSingleNarrative(
                        scenario = "melee hit",
                        weapon = weapon,
                        damageTier = tier,
                        variantNumber = variantNum + 1
                    )

                    val tags = mapOf(
                        "type" to "combat_narration",
                        "scenario" to "melee_hit",
                        "weapon" to weapon,
                        "damage_tier" to tier,
                        "outcome" to "hit"
                    )

                    memoryManager.remember(narrative, tags)
                }
            }
        }
    }

    /**
     * Generates variants for missed melee attacks.
     */
    private suspend fun generateMeleeMissVariants() {
        val weapons = listOf("sword", "axe", "mace", "dagger", "spear")

        for (weapon in weapons) {
            repeat(2) { variantNum ->
                val narrative = generateSingleNarrative(
                    scenario = "melee miss",
                    weapon = weapon,
                    damageTier = "none",
                    variantNumber = variantNum + 1
                )

                val tags = mapOf(
                    "type" to "combat_narration",
                    "scenario" to "melee_miss",
                    "weapon" to weapon,
                    "outcome" to "miss"
                )

                memoryManager.remember(narrative, tags)
            }
        }
    }

    /**
     * Generates variants for ranged weapon hits.
     */
    private suspend fun generateRangedHitVariants() {
        val weapons = listOf("bow", "crossbow", "throwing knife", "javelin")
        val damageTiers = listOf("low", "medium", "high")

        for (weapon in weapons) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    val narrative = generateSingleNarrative(
                        scenario = "ranged hit",
                        weapon = weapon,
                        damageTier = tier,
                        variantNumber = variantNum + 1
                    )

                    val tags = mapOf(
                        "type" to "combat_narration",
                        "scenario" to "ranged_hit",
                        "weapon" to weapon,
                        "damage_tier" to tier,
                        "outcome" to "hit"
                    )

                    memoryManager.remember(narrative, tags)
                }
            }
        }
    }

    /**
     * Generates variants for ranged misses.
     */
    private suspend fun generateRangedMissVariants() {
        val weapons = listOf("bow", "crossbow", "throwing knife", "javelin")

        for (weapon in weapons) {
            repeat(2) { variantNum ->
                val narrative = generateSingleNarrative(
                    scenario = "ranged miss",
                    weapon = weapon,
                    damageTier = "none",
                    variantNumber = variantNum + 1
                )

                val tags = mapOf(
                    "type" to "combat_narration",
                    "scenario" to "ranged_miss",
                    "weapon" to weapon,
                    "outcome" to "miss"
                )

                memoryManager.remember(narrative, tags)
            }
        }
    }

    /**
     * Generates variants for spell casts.
     */
    private suspend fun generateSpellVariants() {
        val spellTypes = listOf("fire", "ice", "lightning", "healing", "poison")
        val damageTiers = listOf("low", "medium", "high")

        for (spellType in spellTypes) {
            for (tier in damageTiers) {
                repeat(2) { variantNum ->
                    val narrative = generateSingleNarrative(
                        scenario = "spell cast",
                        weapon = "$spellType spell",
                        damageTier = tier,
                        variantNumber = variantNum + 1
                    )

                    val tags = mapOf(
                        "type" to "combat_narration",
                        "scenario" to "spell_cast",
                        "spell_type" to spellType,
                        "damage_tier" to tier,
                        "outcome" to "hit"
                    )

                    memoryManager.remember(narrative, tags)
                }
            }
        }
    }

    /**
     * Generates variants for critical hits (double damage).
     */
    private suspend fun generateCriticalHitVariants() {
        val weapons = listOf("sword", "axe", "dagger", "bow")

        for (weapon in weapons) {
            repeat(3) { variantNum ->
                val narrative = generateSingleNarrative(
                    scenario = "critical hit",
                    weapon = weapon,
                    damageTier = "critical",
                    variantNumber = variantNum + 1
                )

                val tags = mapOf(
                    "type" to "combat_narration",
                    "scenario" to "critical_hit",
                    "weapon" to weapon,
                    "damage_tier" to "critical",
                    "outcome" to "critical"
                )

                memoryManager.remember(narrative, tags)
            }
        }
    }

    /**
     * Generates variants for status effect applications.
     */
    private suspend fun generateStatusEffectVariants() {
        val effects = listOf("poison", "slow", "burn", "freeze", "stun", "weaken")

        for (effect in effects) {
            repeat(2) { variantNum ->
                val narrative = generateSingleNarrative(
                    scenario = "status effect",
                    weapon = effect,
                    damageTier = "effect",
                    variantNumber = variantNum + 1
                )

                val tags = mapOf(
                    "type" to "combat_narration",
                    "scenario" to "status_effect",
                    "effect_type" to effect,
                    "outcome" to "effect_applied"
                )

                memoryManager.remember(narrative, tags)
            }
        }
    }

    /**
     * Generates variants for killing blows.
     */
    private suspend fun generateDeathBlowVariants() {
        val weapons = listOf("sword", "axe", "dagger", "bow", "spell")

        for (weapon in weapons) {
            repeat(3) { variantNum ->
                val narrative = generateSingleNarrative(
                    scenario = "death blow",
                    weapon = weapon,
                    damageTier = "lethal",
                    variantNumber = variantNum + 1
                )

                val tags = mapOf(
                    "type" to "combat_narration",
                    "scenario" to "death_blow",
                    "weapon" to weapon,
                    "outcome" to "death"
                )

                memoryManager.remember(narrative, tags)
            }
        }
    }

    /**
     * Generates a single narration variant using the LLM.
     */
    private suspend fun generateSingleNarrative(
        scenario: String,
        weapon: String,
        damageTier: String,
        variantNumber: Int
    ): String {
        val systemPrompt = """
            You are a dungeon master creating vivid, atmospheric combat descriptions.
            Generate a SINGLE SHORT sentence (under 15 words) describing the combat action.
            Be evocative and varied in your descriptions.
            Focus on visceral details - the clash, the impact, the movement.
            DO NOT include damage numbers or outcomes, just describe the action itself.
        """.trimIndent()

        val userContext = buildString {
            appendLine("Scenario: $scenario")
            appendLine("Weapon/Method: $weapon")
            appendLine("Damage tier: $damageTier")
            appendLine("Variant: #$variantNumber (make this DIFFERENT from other variants)")
            appendLine()
            when (scenario) {
                "melee hit" -> appendLine("Describe a successful melee attack with the $weapon. Focus on the strike connecting.")
                "melee miss" -> appendLine("Describe a missed melee attack with the $weapon. Show the enemy dodging or parrying.")
                "ranged hit" -> appendLine("Describe a successful ranged attack with the $weapon. Show the projectile striking true.")
                "ranged miss" -> appendLine("Describe a missed ranged attack with the $weapon. Show the projectile missing.")
                "spell cast" -> appendLine("Describe casting and landing the $weapon. Show magical energy manifesting.")
                "critical hit" -> appendLine("Describe a devastating critical hit with the $weapon. Extra dramatic!")
                "status effect" -> appendLine("Describe the $weapon effect taking hold of the target.")
                "death blow" -> appendLine("Describe the final, killing strike with the $weapon.")
            }
        }

        return try {
            val response = llmClient.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 30,
                temperature = 1.0 // High variance for diversity
            )
            response.choices.firstOrNull()?.message?.content?.trim()
                ?: getFallbackNarrative(scenario, weapon)
        } catch (e: Exception) {
            getFallbackNarrative(scenario, weapon)
        }
    }

    /**
     * Provides fallback narratives if LLM fails.
     */
    private fun getFallbackNarrative(scenario: String, weapon: String): String {
        return when (scenario) {
            "melee hit" -> "Your $weapon strikes true!"
            "melee miss" -> "Your $weapon swing goes wide!"
            "ranged hit" -> "Your $weapon finds its mark!"
            "ranged miss" -> "Your $weapon misses the target!"
            "spell cast" -> "Your $weapon erupts with power!"
            "critical hit" -> "A devastating blow with your $weapon!"
            "status effect" -> "The $weapon effect takes hold!"
            "death blow" -> "Your $weapon delivers the killing blow!"
            else -> "The attack continues."
        }
    }
}
