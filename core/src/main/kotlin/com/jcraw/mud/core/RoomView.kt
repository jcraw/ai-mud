package com.jcraw.mud.core

/**
 * Lightweight view over a V3 space that mimics legacy Room APIs for tests/tooling.
 * Backed entirely by SpacePropertiesComponent + GraphNodeComponent data.
 */
data class RoomView(
    val id: SpaceId,
    val name: String,
    val traits: List<String>,
    val exits: Map<Direction, SpaceId>,
    val entities: List<Entity>,
    val properties: Map<String, String> = emptyMap()
) {
    fun hasExit(direction: Direction): Boolean = exits.containsKey(direction)

    fun getExit(direction: Direction): RoomId? = exits[direction]

    fun getAvailableDirections(): List<Direction> = exits.keys.toList()

    fun addEntity(entity: Entity): RoomView = copy(entities = entities + entity)

    fun removeEntity(entityId: String): RoomView = copy(entities = entities.filter { it.id != entityId })
}

fun WorldState.getCurrentRoomView(playerId: PlayerId = player.id): RoomView? {
    val playerState = players[playerId] ?: return null
    return getRoomView(playerState.currentRoomId)
}

fun WorldState.getRoomView(spaceId: SpaceId): RoomView? {
    val space = getSpace(spaceId) ?: return null
    val node = getGraphNode(spaceId)
    val exits = node?.neighbors?.mapNotNull { edge ->
        val direction = Direction.fromString(edge.direction) ?: return@mapNotNull null
        direction to edge.targetId
    }?.toMap() ?: emptyMap()

    val entitiesInSpace = getEntitiesInSpace(spaceId)
    val traits = deriveTraits(space)

    return RoomView(
        id = spaceId,
        name = space.name,
        traits = traits,
        exits = exits,
        entities = entitiesInSpace
    )
}

fun WorldState.getRoomViews(): Map<SpaceId, RoomView> =
    spaces.keys.associateWithNotNull { getRoomView(it) }

private fun deriveTraits(space: SpacePropertiesComponent): List<String> {
    if (space.description.isBlank()) return emptyList()
    return space.description.split(".")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private inline fun <K, V> Iterable<K>.associateWithNotNull(transform: (K) -> V?): Map<K, V> {
    val result = mutableMapOf<K, V>()
    for (key in this) {
        val value = transform(key)
        if (value != null) {
            result[key] = value
        }
    }
    return result
}
