package com.jcraw.mud.core

import kotlinx.serialization.Serializable

/**
 * Marker interface for all entity components
 * Components add behavior/data to entities without bloating entity classes
 */
@Serializable
sealed interface Component {
    val componentType: ComponentType
}

/**
 * Component type discriminator for storage and lookup
 */
@Serializable
enum class ComponentType {
    SOCIAL,
    COMBAT,
    TRADING,
    SKILL,
    STORY,
    REPUTATION,
    // Future component types can be added here
}

/**
 * Component attachment support for entities
 */
interface ComponentHost {
    val id: String
    val components: Map<ComponentType, Component>

    /**
     * Get component of specific type, or null if not present
     */
    fun <T : Component> getComponent(type: ComponentType): T? {
        @Suppress("UNCHECKED_CAST")
        return components[type] as? T
    }

    /**
     * Check if entity has component of type
     */
    fun hasComponent(type: ComponentType): Boolean {
        return components.containsKey(type)
    }

    /**
     * Create copy of entity with component added/replaced
     * Immutable operation - returns new entity
     */
    fun withComponent(component: Component): ComponentHost

    /**
     * Create copy of entity with component removed
     */
    fun withoutComponent(type: ComponentType): ComponentHost
}
