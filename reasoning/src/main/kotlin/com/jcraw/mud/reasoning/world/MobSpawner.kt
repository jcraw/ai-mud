package com.jcraw.mud.reasoning.world

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.Stats
import com.jcraw.sophia.llm.LLMClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * JSON structure for LLM-generated mob data
 */
@Serializable
private data class MobData(
    val name: String,
    val description: String,
    val health: Int,
    val lootTableId: String,
    val goldDrop: Int,
    val isHostile: Boolean = true,
    val strength: Int = 10,
    val dexterity: Int = 10,
    val constitution: Int = 10,
    val intelligence: Int = 10,
    val wisdom: Int = 10,
    val charisma: Int = 10
)

/**
 * Spawns entities based on theme, mob density, and difficulty.
 * Uses LLM for dynamic mob variety or fallback rules when LLM unavailable.
 */
class MobSpawner(
    private val llmClient: LLMClient? = null,
    private val lootTableGenerator: LootTableGenerator? = null
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Spawn entities for a space.
     * Count determined by mobDensity * spaceSize.
     * Uses LLM for variety or deterministic fallback.
     */
    suspend fun spawnEntities(
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceSize: Int = 10
    ): List<Entity.NPC> {
        val mobCount = (spaceSize * mobDensity).toInt().coerceAtLeast(0)
        if (mobCount == 0) return emptyList()

        return if (llmClient != null) {
            spawnEntitiesWithLLM(theme, mobCount, difficulty)
        } else {
            spawnEntitiesFallback(theme, mobCount, difficulty)
        }
    }

    /**
     * Spawn entities using LLM for variety and theme-appropriate content.
     */
    private suspend fun spawnEntitiesWithLLM(
        theme: String,
        count: Int,
        difficulty: Int
    ): List<Entity.NPC> {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()

        val systemPrompt = """
            You are a game master generating NPCs/mobs for a fantasy dungeon.
            Output valid JSON array only, no additional text.
        """.trimIndent()

        val userContext = """
            Generate $count NPC/mob entries for a $theme setting at difficulty level $difficulty (scale 1-20).
            Use mob archetypes: ${profile.mobArchetypes.joinToString(", ")}

            For each mob, provide:
            - name: Unique name (include type, e.g., "Skeleton Warrior #1")
            - description: 1-2 sentence description
            - health: Scale with difficulty (difficulty * 10 + variance)
            - lootTableId: Use "${theme.lowercase().replace(" ", "_")}_$difficulty"
            - goldDrop: Scale with difficulty (difficulty * 5 + variance)
            - isHostile: true (default)
            - strength, dexterity, constitution, intelligence, wisdom, charisma: D&D stats (8-18, scale with difficulty)

            Output as JSON array of objects. Example:
            [
                {
                    "name": "Wolf Alpha",
                    "description": "A large gray wolf with piercing yellow eyes.",
                    "health": 150,
                    "lootTableId": "dark_forest_5",
                    "goldDrop": 25,
                    "isHostile": true,
                    "strength": 14,
                    "dexterity": 16,
                    "constitution": 12,
                    "intelligence": 6,
                    "wisdom": 12,
                    "charisma": 8
                }
            ]
        """.trimIndent()

        return try {
            val response = llmClient!!.chatCompletion(
                modelId = "gpt-4o-mini",
                systemPrompt = systemPrompt,
                userContext = userContext,
                maxTokens = 2000,
                temperature = 0.8
            )

            // Extract content from response
            val content = response.choices.firstOrNull()?.message?.content?.trim()
                ?: throw Exception("LLM returned empty response")

            // Parse JSON response
            val mobDataList = json.decodeFromString<List<MobData>>(content)

            // Convert to Entity.NPC
            mobDataList.map { mobData ->
                val lootTableId = mobData.lootTableId.ifBlank {
                    "${theme.lowercase().replace(" ", "_")}_$difficulty"
                }

                Entity.NPC(
                    id = "npc_${UUID.randomUUID()}",
                    name = mobData.name,
                    description = mobData.description,
                    isHostile = mobData.isHostile,
                    health = mobData.health.coerceAtLeast(1),
                    maxHealth = mobData.health.coerceAtLeast(1),
                    stats = Stats(
                        strength = mobData.strength.coerceIn(3, 20),
                        dexterity = mobData.dexterity.coerceIn(3, 20),
                        constitution = mobData.constitution.coerceIn(3, 20),
                        intelligence = mobData.intelligence.coerceIn(3, 20),
                        wisdom = mobData.wisdom.coerceIn(3, 20),
                        charisma = mobData.charisma.coerceIn(3, 20)
                    ),
                    lootTableId = lootTableId,
                    goldDrop = mobData.goldDrop.coerceAtLeast(0)
                )
            }
        } catch (e: Exception) {
            // Fallback on LLM failure or parse error
            spawnEntitiesFallback(theme, count, difficulty)
        }
    }

    /**
     * Fallback mob generation using deterministic rules.
     * Used when LLM unavailable or fails.
     */
    private fun spawnEntitiesFallback(
        theme: String,
        count: Int,
        difficulty: Int
    ): List<Entity.NPC> {
        val profile = ThemeRegistry.getProfileSemantic(theme)
            ?: ThemeRegistry.getDefaultProfile()

        return (1..count).map { index ->
            val archetype = profile.mobArchetypes.random()
            val lootTableId = "${theme.lowercase().replace(" ", "_")}_$difficulty"

            // Scale stats with difficulty
            val statBase = 8 + (difficulty / 2).coerceAtMost(6)

            Entity.NPC(
                id = "npc_${UUID.randomUUID()}",
                name = "$archetype #$index",
                description = "A $archetype from the $theme.",
                isHostile = true,
                health = difficulty * 10 + kotlin.random.Random.nextInt(-10, 20),
                maxHealth = difficulty * 10 + kotlin.random.Random.nextInt(-10, 20),
                stats = Stats(
                    strength = statBase + kotlin.random.Random.nextInt(-2, 3),
                    dexterity = statBase + kotlin.random.Random.nextInt(-2, 3),
                    constitution = statBase + kotlin.random.Random.nextInt(-2, 3),
                    intelligence = statBase + kotlin.random.Random.nextInt(-2, 3),
                    wisdom = statBase + kotlin.random.Random.nextInt(-2, 3),
                    charisma = statBase + kotlin.random.Random.nextInt(-2, 3)
                ),
                lootTableId = lootTableId,
                goldDrop = difficulty * 5 + kotlin.random.Random.nextInt(-5, 10)
            )
        }
    }

    /**
     * Respawn entities for a space.
     * Clears existing entities and generates fresh list.
     * Used on game restart for murder-hobo viable gameplay.
     */
    suspend fun respawn(
        theme: String,
        mobDensity: Double,
        difficulty: Int,
        spaceSize: Int = 10
    ): List<Entity.NPC> {
        return spawnEntities(theme, mobDensity, difficulty, spaceSize)
    }
}
