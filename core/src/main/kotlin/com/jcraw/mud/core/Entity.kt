package com.jcraw.mud.core

import kotlinx.serialization.Serializable

@Serializable
sealed class Entity {
    abstract val id: String
    abstract val name: String
    abstract val description: String

    @Serializable
    data class Item(
        override val id: String,
        override val name: String,
        override val description: String,
        val isPickupable: Boolean = true,
        val isUsable: Boolean = false,
        val properties: Map<String, String> = emptyMap()
    ) : Entity()

    @Serializable
    data class NPC(
        override val id: String,
        override val name: String,
        override val description: String,
        val isHostile: Boolean = false,
        val health: Int = 100,
        val maxHealth: Int = 100,
        val properties: Map<String, String> = emptyMap()
    ) : Entity()

    @Serializable
    data class Feature(
        override val id: String,
        override val name: String,
        override val description: String,
        val isInteractable: Boolean = false,
        val properties: Map<String, String> = emptyMap()
    ) : Entity()
}