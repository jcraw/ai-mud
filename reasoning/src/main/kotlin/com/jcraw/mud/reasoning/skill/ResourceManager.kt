package com.jcraw.mud.reasoning.skill

import com.jcraw.mud.core.repository.SkillComponentRepository

/**
 * Resource pool data (current and max values)
 */
data class ResourcePool(
    val resourceType: String,
    val current: Int,
    val max: Int
) {
    /**
     * Check if enough resources are available
     */
    fun hasEnough(amount: Int): Boolean = current >= amount

    /**
     * Consume resources
     * Returns new pool with reduced current, or null if insufficient
     */
    fun consume(amount: Int): ResourcePool? {
        if (!hasEnough(amount)) return null
        return copy(current = (current - amount).coerceAtLeast(0))
    }

    /**
     * Regenerate resources by amount
     * Cannot exceed max
     */
    fun regenerate(amount: Int): ResourcePool {
        return copy(current = (current + amount).coerceAtMost(max))
    }

    /**
     * Restore to full
     */
    fun restoreFull(): ResourcePool {
        return copy(current = max)
    }

    /**
     * Get percentage remaining (0.0 to 1.0)
     */
    fun percentageRemaining(): Float {
        if (max == 0) return 0f
        return current.toFloat() / max.toFloat()
    }
}

/**
 * Manages consumable resources (mana, chi, etc.)
 * Resource pools are based on resource-providing skills (Mana Reserve, Chi Reserve)
 * Regeneration is based on flow skills (Mana Flow, Chi Flow)
 *
 * Note: Current resource values are stored in-memory (not persisted to DB).
 * Max values are calculated from skill levels.
 */
class ResourceManager(
    private val componentRepo: SkillComponentRepository
) {
    // In-memory tracking of current resource values
    // Key: entityId_resourceType (e.g., "player_mana")
    private val currentResources = mutableMapOf<String, Int>()

    /**
     * Get resource pool for an entity
     * - max = skill level * 10 (e.g., Mana Reserve L20 = 200 mana)
     * - current = tracked value (defaults to max if not yet tracked)
     */
    fun getResourcePool(entityId: String, resourceType: String): Result<ResourcePool> {
        return runCatching {
            // Get component
            val component = componentRepo.load(entityId).getOrNull()

            // Calculate max from resource skill (e.g., "Mana Reserve" skill for "mana" resource)
            val max = component?.getResourcePoolMax(resourceType) ?: 0

            // Get current (default to max if not tracked)
            val key = "${entityId}_$resourceType"
            val current = currentResources.getOrPut(key) { max }

            // Ensure current doesn't exceed max (in case skill level decreased)
            val actualCurrent = current.coerceAtMost(max)
            if (actualCurrent != current) {
                currentResources[key] = actualCurrent
            }

            ResourcePool(
                resourceType = resourceType,
                current = actualCurrent,
                max = max
            )
        }
    }

    /**
     * Consume resources
     * Returns true if successful, false if insufficient
     */
    fun consumeResource(entityId: String, resourceType: String, amount: Int): Result<Boolean> {
        return runCatching {
            require(amount >= 0) { "Consume amount must be non-negative" }

            val pool = getResourcePool(entityId, resourceType).getOrThrow()

            if (!pool.hasEnough(amount)) {
                return Result.success(false)
            }

            // Consume
            val newPool = pool.consume(amount) ?: return Result.success(false)
            updateCurrentResource(entityId, resourceType, newPool.current)

            true
        }
    }

    /**
     * Regenerate resources based on flow skill
     * - Regen rate = flow skill level * 2 per turn (e.g., Mana Flow L10 = 20 mana/turn)
     * - Returns amount actually regenerated
     */
    fun regenerateResource(entityId: String, resourceType: String): Result<Int> {
        return runCatching {
            // Get component
            val component = componentRepo.load(entityId).getOrNull()

            // Get flow skill (e.g., "Mana Flow" for "mana" resource)
            val flowSkillName = "${resourceType.replaceFirstChar { it.uppercaseChar() }} Flow"
            val flowSkillLevel = component?.getEffectiveLevel(flowSkillName) ?: 0

            // Calculate regen amount
            val regenAmount = flowSkillLevel * 2

            if (regenAmount <= 0) {
                return Result.success(0)
            }

            // Get current pool
            val pool = getResourcePool(entityId, resourceType).getOrThrow()

            // Regenerate
            val newPool = pool.regenerate(regenAmount)
            val actualRegen = newPool.current - pool.current
            updateCurrentResource(entityId, resourceType, newPool.current)

            actualRegen
        }
    }

    /**
     * Restore resource to full
     * Useful for rest mechanics
     */
    fun restoreResourceFull(entityId: String, resourceType: String): Result<Unit> {
        return runCatching {
            val pool = getResourcePool(entityId, resourceType).getOrThrow()
            updateCurrentResource(entityId, resourceType, pool.max)
        }
    }

    /**
     * Update current resource value
     */
    private fun updateCurrentResource(entityId: String, resourceType: String, value: Int) {
        val key = "${entityId}_$resourceType"
        currentResources[key] = value
    }

    /**
     * Clear all resource tracking for an entity
     * Useful for testing or cleanup
     */
    fun clearResourcesForEntity(entityId: String) {
        currentResources.keys.removeIf { it.startsWith("${entityId}_") }
    }

    /**
     * Get all tracked resource pools for an entity
     */
    fun getAllResourcePools(entityId: String): Result<List<ResourcePool>> {
        return runCatching {
            val component = componentRepo.load(entityId).getOrNull()

            // Find all resource-type skills
            val resourceSkills = component?.skills?.values
                ?.filter { it.resourceType != null && it.unlocked }
                ?: emptyList()

            resourceSkills.mapNotNull { skill ->
                skill.resourceType?.let { resourceType ->
                    getResourcePool(entityId, resourceType).getOrNull()
                }
            }
        }
    }
}
