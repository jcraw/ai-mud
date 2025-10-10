package com.jcraw.mud.core

import kotlinx.serialization.Serializable

typealias RoomId = String
typealias PlayerId = String

@Serializable
data class Room(
    val id: RoomId,
    val name: String,
    val traits: List<String>,
    val exits: Map<Direction, RoomId> = emptyMap(),
    val entities: List<Entity> = emptyList(),
    val properties: Map<String, String> = emptyMap()
) {
    fun hasExit(direction: Direction): Boolean = exits.containsKey(direction)

    fun getExit(direction: Direction): RoomId? = exits[direction]

    fun getAvailableDirections(): List<Direction> = exits.keys.toList()

    fun addEntity(entity: Entity): Room = copy(entities = entities + entity)

    fun removeEntity(entityId: String): Room = copy(entities = entities.filter { it.id != entityId })

    fun getEntity(entityId: String): Entity? = entities.find { it.id == entityId }

    fun getEntitiesByType(type: Class<out Entity>): List<Entity> = entities.filter { type.isInstance(it) }
}