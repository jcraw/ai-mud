package com.jcraw.mud.client

import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.Stats

/**
 * Utility helpers for presenting entities stored in SpacePropertiesComponent.
 * Until full persistence of NPC data is implemented, we provide lightweight
 * stubs so the UI can surface names and descriptions.
 */
object SpaceEntitySupport {

    data class SpaceEntityStub(
        val id: String,
        val displayName: String,
        val description: String
    )

    private val knownStubs: Map<String, SpaceEntityStub> = listOf(
        SpaceEntityStub(
            id = "npc_town_potions_merchant",
            displayName = "Alara the Alchemist",
            description = "A middle-aged woman with stained robes and kind eyes. Her stall overflows with colorful vials."
        ),
        SpaceEntityStub(
            id = "npc_town_armor_merchant",
            displayName = "Thoren Ironfist",
            description = "A stocky dwarf with a thick beard who examines each piece of armor with a critical eye."
        ),
        SpaceEntityStub(
            id = "npc_town_blacksmith",
            displayName = "Kell Ironbrand",
            description = "A broad-shouldered blacksmith whose hammer strikes ring through the town square."
        ),
        SpaceEntityStub(
            id = "npc_town_general_store",
            displayName = "Maris Greenfield",
            description = "A cheerful quartermaster who keeps shelves stocked with dungeon essentials."
        )
    ).associateBy { it.id }

    private fun defaultStub(id: String): SpaceEntityStub {
        val displayName = id
            .removePrefix("npc_")
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { if (it.isEmpty()) it else it.replaceFirstChar { ch -> ch.uppercase() } }
            .ifBlank { id }

        return SpaceEntityStub(
            id = id,
            displayName = displayName,
            description = "A figure stands here, awaiting full characterization."
        )
    }

    fun getStub(id: String): SpaceEntityStub = knownStubs[id] ?: defaultStub(id)

    fun findStub(space: SpacePropertiesComponent, query: String): SpaceEntityStub? {
        val lower = query.lowercase()
        return space.entities
            .map { getStub(it) }
            .firstOrNull { stub ->
                stub.displayName.lowercase().contains(lower) || stub.id.lowercase().contains(lower)
            }
    }

    fun createNpcStub(stub: SpaceEntityStub): Entity.NPC =
        Entity.NPC(
            id = stub.id,
            name = stub.displayName,
            description = stub.description,
            isHostile = false,
            health = 50,
            maxHealth = 50,
            stats = Stats(),
            components = emptyMap()
        )
}
