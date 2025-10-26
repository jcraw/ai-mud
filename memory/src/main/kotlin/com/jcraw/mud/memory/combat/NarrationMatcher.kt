package com.jcraw.mud.memory.combat

import com.jcraw.mud.memory.MemoryManager

/**
 * Context for combat narration matching.
 * Used to semantically search for appropriate pre-generated narration variants.
 */
data class CombatContext(
    val scenario: String,           // "melee_hit", "ranged_miss", "spell_cast", etc.
    val weapon: String,              // "sword", "bow", "fire spell", "bare fists"
    val damageTier: String,          // "low", "medium", "high", "critical", "none"
    val outcome: String,             // "hit", "miss", "critical", "death", "effect_applied"
    val targetType: String = "npc",  // "npc", "player", "object"
    val specialModifiers: List<String> = emptyList()  // "stealth_bonus", "counter_attack", etc.
)

/**
 * Matches combat contexts to pre-generated narration variants using semantic search.
 * Searches the vector database for the most appropriate cached narration based on
 * similarity to the current combat situation.
 *
 * If no suitable match is found (similarity < threshold), returns null to trigger
 * live LLM generation.
 */
class NarrationMatcher(
    private val memoryManager: MemoryManager,
    private val similarityThreshold: Double = 0.85
) {

    /**
     * Finds an appropriate narration variant for the given combat context.
     *
     * @param context The combat situation to narrate
     * @return A cached narration variant, or null if no good match is found
     */
    suspend fun findNarration(context: CombatContext): String? {
        // Build search query from context
        val searchQuery = buildSearchQuery(context)

        // Search vector DB with semantic matching
        val matches = memoryManager.recall(searchQuery, k = 5)

        // Filter matches by metadata tags
        val filteredMatches = matches.filter { narrative ->
            // Check if this narrative matches our scenario type
            // In a full implementation, we'd query metadata from the vector store
            // For now, we do simple string matching
            matchesContext(narrative, context)
        }

        // Return best match if it meets threshold
        // In a real implementation, we'd have access to similarity scores
        // For this simplified version, we return the first filtered match
        return filteredMatches.firstOrNull()
    }

    /**
     * Builds a search query string from combat context.
     * This query will be embedded and used for semantic search.
     */
    private fun buildSearchQuery(context: CombatContext): String {
        return buildString {
            append(context.scenario.replace("_", " "))
            append(" with ")
            append(context.weapon)
            append(" for ")
            append(context.damageTier)
            append(" damage")
            append(" outcome: ")
            append(context.outcome)

            if (context.specialModifiers.isNotEmpty()) {
                append(" (")
                append(context.specialModifiers.joinToString(", "))
                append(")")
            }
        }
    }

    /**
     * Checks if a narration matches the combat context.
     * This is a simplified version - in production, we'd use vector DB metadata queries.
     */
    private fun matchesContext(narrative: String, context: CombatContext): Boolean {
        val narrativeLower = narrative.lowercase()
        val weaponLower = context.weapon.lowercase()

        // Check if weapon is mentioned (or weapon category)
        val weaponMentioned = narrativeLower.contains(weaponLower) ||
                narrativeLower.contains(getWeaponCategory(weaponLower))

        // Check if outcome matches the narrative tone
        val outcomeMatches = when (context.outcome) {
            "hit" -> !narrativeLower.contains("miss") && !narrativeLower.contains("dodge")
            "miss" -> narrativeLower.contains("miss") || narrativeLower.contains("dodge") || narrativeLower.contains("deflect")
            "critical" -> narrativeLower.contains("devastating") || narrativeLower.contains("crushing") || narrativeLower.contains("brutal")
            "death" -> narrativeLower.contains("fall") || narrativeLower.contains("defeat") || narrativeLower.contains("killing")
            else -> true
        }

        return weaponMentioned && outcomeMatches
    }

    /**
     * Maps specific weapons to broader categories for matching.
     */
    private fun getWeaponCategory(weapon: String): String {
        return when {
            weapon.contains("sword") || weapon.contains("blade") -> "blade"
            weapon.contains("axe") -> "axe"
            weapon.contains("bow") || weapon.contains("arrow") -> "arrow"
            weapon.contains("dagger") || weapon.contains("knife") -> "blade"
            weapon.contains("mace") || weapon.contains("hammer") || weapon.contains("club") -> "crushing"
            weapon.contains("spear") || weapon.contains("lance") -> "piercing"
            weapon.contains("fire") -> "flame"
            weapon.contains("ice") || weapon.contains("frost") -> "frost"
            weapon.contains("lightning") || weapon.contains("thunder") -> "lightning"
            else -> weapon
        }
    }

    /**
     * Determines damage tier from actual damage amount and context.
     */
    fun determineDamageTier(damage: Int, maxHp: Int): String {
        val percentage = (damage.toDouble() / maxHp.toDouble()) * 100
        return when {
            damage == 0 -> "none"
            percentage >= 50 -> "critical"
            percentage >= 25 -> "high"
            percentage >= 10 -> "medium"
            else -> "low"
        }
    }

    /**
     * Determines scenario type from action type and weapon.
     */
    fun determineScenario(weaponType: String, isHit: Boolean, isSpell: Boolean): String {
        return when {
            isSpell -> "spell_cast"
            weaponType in listOf("bow", "crossbow", "throwing knife", "javelin") -> {
                if (isHit) "ranged_hit" else "ranged_miss"
            }
            else -> if (isHit) "melee_hit" else "melee_miss"
        }
    }
}
