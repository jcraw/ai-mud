package com.jcraw.mud.reasoning.items

import com.jcraw.mud.core.*
import com.jcraw.mud.core.repository.ItemRepository

/**
 * Handles multipurpose item uses beyond simple consumption
 * Supports tag-based rules (blunt → improvised weapon) and emergent creative uses
 */
class ItemUseHandler(
    private val itemRepository: ItemRepository
) {

    /**
     * Result of a multipurpose item use attempt
     */
    sealed class ItemUseResult {
        /**
         * Item used as improvised weapon
         * @param damageBonus Calculated damage bonus from item weight
         * @param itemName Name of the item used
         * @param itemTags Tags that enable this use
         */
        data class ImprovisedWeapon(
            val damageBonus: Int,
            val itemName: String,
            val itemTags: List<String>
        ) : ItemUseResult()

        /**
         * Item used for explosive/timed detonation
         * @param aoeDamage Area-of-effect damage amount
         * @param timer Turns until detonation
         * @param itemName Name of the item used
         */
        data class ExplosiveUse(
            val aoeDamage: Int,
            val timer: Int,
            val itemName: String
        ) : ItemUseResult()

        /**
         * Item used as container/storage
         * @param capacityBonus Weight capacity increase
         * @param itemName Name of the item used
         */
        data class ContainerUse(
            val capacityBonus: Double,
            val itemName: String
        ) : ItemUseResult()

        /**
         * Item used for environmental interaction
         * @param effect Description of the effect
         * @param itemName Name of the item used
         */
        data class EnvironmentalUse(
            val effect: String,
            val itemName: String
        ) : ItemUseResult()

        /**
         * Item use not possible
         * @param reason Human-readable error message
         */
        data class Failure(val reason: String) : ItemUseResult()
    }

    /**
     * Determine if item can be used as improvised weapon
     * Items with "blunt" or "sharp" tags can be weapons
     * Damage scales with weight: weight * 0.5
     *
     * @param template Item template
     * @param instance Item instance
     * @return ItemUseResult with damage calculation or failure
     */
    fun useAsImprovisedWeapon(template: ItemTemplate, instance: ItemInstance): ItemUseResult {
        // Check for weapon-enabling tags
        val isBlunt = "blunt" in template.tags
        val isSharp = "sharp" in template.tags

        if (!isBlunt && !isSharp) {
            return ItemUseResult.Failure("This item isn't suitable as a weapon")
        }

        // Calculate damage based on weight
        val weight = template.getWeight()
        val damageBonus = (weight * 0.5).toInt().coerceAtLeast(1)

        return ItemUseResult.ImprovisedWeapon(
            damageBonus = damageBonus,
            itemName = template.name,
            itemTags = template.tags
        )
    }

    /**
     * Determine if item can be used as explosive
     * Items with "explosive" tag can detonate
     *
     * @param template Item template
     * @param instance Item instance
     * @return ItemUseResult with explosion parameters or failure
     */
    fun useAsExplosive(template: ItemTemplate, instance: ItemInstance): ItemUseResult {
        if ("explosive" !in template.tags) {
            return ItemUseResult.Failure("This item isn't explosive")
        }

        // Calculate AoE damage from properties or use default
        val aoeDamage = template.properties["explosion_damage"]?.toIntOrNull() ?: 20
        val timer = template.properties["explosion_timer"]?.toIntOrNull() ?: 3

        return ItemUseResult.ExplosiveUse(
            aoeDamage = aoeDamage,
            timer = timer,
            itemName = template.name
        )
    }

    /**
     * Determine if item can be used as container
     * Items with "container" tag provide capacity bonus
     *
     * @param template Item template
     * @param instance Item instance
     * @return ItemUseResult with capacity bonus or failure
     */
    fun useAsContainer(template: ItemTemplate, instance: ItemInstance): ItemUseResult {
        if ("container" !in template.tags) {
            return ItemUseResult.Failure("This item isn't a container")
        }

        // Get capacity bonus from properties
        val capacityBonus = template.properties["capacity_bonus"]?.toDoubleOrNull() ?: 10.0

        return ItemUseResult.ContainerUse(
            capacityBonus = capacityBonus,
            itemName = template.name
        )
    }

    /**
     * Determine multipurpose use based on action description and item tags
     * This is the main entry point for flexible item usage
     *
     * @param instanceId Item instance ID
     * @param action Action description (e.g., "bash", "throw", "use as shield")
     * @param inventory Player inventory containing the item
     * @return ItemUseResult appropriate for the action/tags combination
     */
    fun determineUse(
        instanceId: String,
        action: String,
        inventory: InventoryComponent
    ): ItemUseResult {
        // Get item instance
        val instance = inventory.getItem(instanceId)
            ?: return ItemUseResult.Failure("You don't have that item")

        // Get item template
        val templateResult = itemRepository.findById(instance.templateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) {
            return ItemUseResult.Failure("Item template not found")
        }
        val template = templateResult.getOrNull()!!

        // Determine use based on action keywords and tags
        return when {
            // Weapon uses
            action.contains("bash", ignoreCase = true) ||
            action.contains("hit", ignoreCase = true) ||
            action.contains("attack", ignoreCase = true) ||
            action.contains("weapon", ignoreCase = true) -> {
                useAsImprovisedWeapon(template, instance)
            }

            // Explosive uses
            action.contains("explode", ignoreCase = true) ||
            action.contains("detonate", ignoreCase = true) ||
            action.contains("throw", ignoreCase = true) && "explosive" in template.tags -> {
                useAsExplosive(template, instance)
            }

            // Container uses
            action.contains("store", ignoreCase = true) ||
            action.contains("container", ignoreCase = true) ||
            action.contains("hold", ignoreCase = true) && "container" in template.tags -> {
                useAsContainer(template, instance)
            }

            // Environmental/creative uses (tag-based flexibility)
            "flammable" in template.tags && action.contains("burn", ignoreCase = true) -> {
                ItemUseResult.EnvironmentalUse(
                    effect = "Set ${template.name} on fire - creates light and smoke",
                    itemName = template.name
                )
            }

            "fragile" in template.tags && action.contains("break", ignoreCase = true) -> {
                ItemUseResult.EnvironmentalUse(
                    effect = "Shatter ${template.name} - creates shards and noise",
                    itemName = template.name
                )
            }

            "liquid" in template.tags && action.contains("pour", ignoreCase = true) -> {
                ItemUseResult.EnvironmentalUse(
                    effect = "Pour ${template.name} - creates puddle or wet surface",
                    itemName = template.name
                )
            }

            else -> {
                ItemUseResult.Failure("You can't use ${template.name} that way (no matching tags or action)")
            }
        }
    }

    /**
     * Get all possible uses for an item based on its tags
     * Useful for showing player what they can do with an item
     *
     * @param template Item template
     * @return List of possible use descriptions
     */
    fun getPossibleUses(template: ItemTemplate): List<String> {
        val uses = mutableListOf<String>()

        if ("blunt" in template.tags || "sharp" in template.tags) {
            uses.add("Improvised weapon (bash, hit, attack)")
        }

        if ("explosive" in template.tags) {
            uses.add("Explosive (throw, detonate)")
        }

        if ("container" in template.tags) {
            uses.add("Container (store items)")
        }

        if ("flammable" in template.tags) {
            uses.add("Burn for light/distraction")
        }

        if ("fragile" in template.tags) {
            uses.add("Break to create noise/shards")
        }

        if ("liquid" in template.tags) {
            uses.add("Pour to create puddle/wet surface")
        }

        if ("throwable" in template.tags) {
            uses.add("Throw at target")
        }

        if ("climbable" in template.tags) {
            uses.add("Use for climbing")
        }

        return uses
    }
}
