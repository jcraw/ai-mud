package com.jcraw.mud.reasoning.inventory

import com.jcraw.mud.core.Entity

/**
 * Name/id contains-match for floor items and NPCs. Shared by console and GUI (MUD-039).
 */
object EntityNameMatch {

    fun matches(entity: Entity, query: String): Boolean {
        val q = query.lowercase()
        return entity.name.lowercase().contains(q) || entity.id.lowercase().contains(q)
    }

    fun findItem(entities: List<Entity>, target: String): Entity.Item? =
        entities.filterIsInstance<Entity.Item>().find { matches(it, target) }

    fun findNpc(entities: List<Entity>, target: String): Entity.NPC? =
        entities.filterIsInstance<Entity.NPC>().find { matches(it, target) }

    fun anyNameContains(entities: List<Entity>, target: String): Boolean {
        val q = target.lowercase()
        return entities.any { it.name.lowercase().contains(q) }
    }
}
