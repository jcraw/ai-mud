package com.jcraw.mud.memory.world

import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.repository.GraphNodeRepository
import com.jcraw.mud.core.world.EdgeData
import com.jcraw.mud.core.world.NodeType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of GraphNodeRepository
 * Manages graph node persistence with JSON serialization for neighbors
 */
class SQLiteGraphNodeRepository(
    private val database: WorldDatabase
) : GraphNodeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(node: GraphNodeComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO graph_nodes
                (id, chunk_id, position_x, position_y, type, neighbors)
                VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, node.id)
                stmt.setString(2, node.chunkId)

                // Handle nullable position
                val pos = node.position
                if (pos != null) {
                    stmt.setInt(3, pos.first)
                    stmt.setInt(4, pos.second)
                } else {
                    stmt.setNull(3, java.sql.Types.INTEGER)
                    stmt.setNull(4, java.sql.Types.INTEGER)
                }

                stmt.setString(5, node.type.toString())
                stmt.setString(6, json.encodeToString(node.neighbors))
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findById(id: String): Result<GraphNodeComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM graph_nodes WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val position = if (rs.getObject("position_x") != null) {
                        Pair(rs.getInt("position_x"), rs.getInt("position_y"))
                    } else {
                        null
                    }

                    val node = GraphNodeComponent(
                        id = rs.getString("id"),
                        chunkId = rs.getString("chunk_id"),
                        position = position,
                        type = parseNodeType(rs.getString("type")),
                        neighbors = json.decodeFromString<List<EdgeData>>(rs.getString("neighbors"))
                    )
                    Result.success(node)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findByChunk(chunkId: String): Result<List<GraphNodeComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM graph_nodes WHERE chunk_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, chunkId)
                val rs = stmt.executeQuery()
                val nodes = mutableListOf<GraphNodeComponent>()

                while (rs.next()) {
                    val position = if (rs.getObject("position_x") != null) {
                        Pair(rs.getInt("position_x"), rs.getInt("position_y"))
                    } else {
                        null
                    }

                    val node = GraphNodeComponent(
                        id = rs.getString("id"),
                        chunkId = rs.getString("chunk_id"),
                        position = position,
                        type = parseNodeType(rs.getString("type")),
                        neighbors = json.decodeFromString<List<EdgeData>>(rs.getString("neighbors"))
                    )
                    nodes.add(node)
                }
                Result.success(nodes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun update(node: GraphNodeComponent): Result<Unit> {
        return try {
            // Check if node exists first
            val exists = findById(node.id).getOrNull() != null
            if (!exists) {
                return Result.failure(IllegalStateException("Node ${node.id} does not exist"))
            }

            // Use save for update (INSERT OR REPLACE)
            save(node)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(id: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM graph_nodes WHERE id = ?").use { stmt ->
                stmt.setString(1, id)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun addEdge(fromId: String, edge: EdgeData): Result<Unit> {
        return try {
            val node = findById(fromId).getOrThrow()
                ?: return Result.failure(IllegalStateException("Node $fromId not found"))

            val updated = node.addEdge(edge)
            update(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun removeEdge(fromId: String, targetId: String): Result<Unit> {
        return try {
            val node = findById(fromId).getOrThrow()
                ?: return Result.failure(IllegalStateException("Node $fromId not found"))

            val updated = node.removeEdge(targetId)
            update(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getAll(): Result<Map<String, GraphNodeComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM graph_nodes"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val nodes = mutableMapOf<String, GraphNodeComponent>()

                while (rs.next()) {
                    val id = rs.getString("id")
                    val position = if (rs.getObject("position_x") != null) {
                        Pair(rs.getInt("position_x"), rs.getInt("position_y"))
                    } else {
                        null
                    }

                    val node = GraphNodeComponent(
                        id = id,
                        chunkId = rs.getString("chunk_id"),
                        position = position,
                        type = parseNodeType(rs.getString("type")),
                        neighbors = json.decodeFromString<List<EdgeData>>(rs.getString("neighbors"))
                    )
                    nodes[id] = node
                }
                Result.success(nodes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse NodeType from string representation
     * Handles sealed class deserialization
     */
    private fun parseNodeType(typeString: String): NodeType {
        return when (typeString) {
            "Hub" -> NodeType.Hub
            "Linear" -> NodeType.Linear
            "Branching" -> NodeType.Branching
            "DeadEnd" -> NodeType.DeadEnd
            "TreasureRoom" -> NodeType.TreasureRoom
            "Boss" -> NodeType.Boss
            "Frontier" -> NodeType.Frontier
            "Questable" -> NodeType.Questable
            else -> throw IllegalArgumentException("Unknown NodeType: $typeString")
        }
    }
}
