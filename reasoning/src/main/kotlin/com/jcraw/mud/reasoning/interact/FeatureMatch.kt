package com.jcraw.mud.reasoning.interact

import com.jcraw.mud.core.Entity

/**
 * Feature name/id matching for interact + skill-check. Shared by console and GUI (MUD-039).
 */
object FeatureMatch {

    fun find(entities: List<Entity>, target: String): Entity.Feature? {
        val normalizedTarget = target.lowercase().replace("_", " ")
        return entities.filterIsInstance<Entity.Feature>()
            .find { matches(it, normalizedTarget) }
    }

    fun isFountain(feature: Entity.Feature): Boolean =
        feature.properties["interaction_type"] == "fountain" &&
            feature.properties["heals_hp"] == "true"

    fun matches(entity: Entity.Feature, normalizedTarget: String): Boolean {
        val normalizedName = entity.name.lowercase()
        val normalizedId = entity.id.lowercase().replace("_", " ")
        return normalizedName.contains(normalizedTarget) ||
            normalizedId.contains(normalizedTarget) ||
            normalizedTarget.contains(normalizedName) ||
            normalizedTarget.contains(normalizedId) ||
            normalizedTarget.split(" ").all { word ->
                normalizedName.contains(word) || normalizedId.contains(word)
            }
    }
}
