package com.jcraw.mud.memory.item

import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.TradingComponent
import com.jcraw.mud.core.repository.TradingRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * SQLite implementation of TradingRepository
 * Manages trading component persistence
 */
class SQLiteTradingRepository(
    private val database: ItemDatabase
) : TradingRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun findByEntityId(entityId: String): Result<TradingComponent?> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM trading_stocks WHERE entity_id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                val rs = stmt.executeQuery()

                if (rs.next()) {
                    val merchantGold = rs.getInt("merchant_gold")
                    val stock = json.decodeFromString<List<ItemInstance>>(rs.getString("stock"))
                    val buyAnything = rs.getInt("buy_anything") == 1
                    val priceModBase = rs.getDouble("price_mod_base")

                    val trading = TradingComponent(
                        merchantGold = merchantGold,
                        stock = stock,
                        buyAnything = buyAnything,
                        priceModBase = priceModBase
                    )
                    Result.success(trading)
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun save(entityId: String, trading: TradingComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                INSERT OR REPLACE INTO trading_stocks
                (entity_id, merchant_gold, stock, buy_anything, price_mod_base)
                VALUES (?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, entityId)
                stmt.setInt(2, trading.merchantGold)
                stmt.setString(3, json.encodeToString(trading.stock))
                stmt.setInt(4, if (trading.buyAnything) 1 else 0)
                stmt.setDouble(5, trading.priceModBase)
                stmt.executeUpdate()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun delete(entityId: String): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("DELETE FROM trading_stocks WHERE entity_id = ?").use { stmt ->
                stmt.setString(1, entityId)
                stmt.executeUpdate()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateGold(entityId: String, newGold: Int): Result<Unit> {
        return try {
            val conn = database.getConnection()
            conn.prepareStatement("UPDATE trading_stocks SET merchant_gold = ? WHERE entity_id = ?").use { stmt ->
                stmt.setInt(1, newGold)
                stmt.setString(2, entityId)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    return Result.failure(IllegalStateException("Entity $entityId has no trading component"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun updateStock(entityId: String, trading: TradingComponent): Result<Unit> {
        return try {
            val conn = database.getConnection()
            val sql = """
                UPDATE trading_stocks
                SET stock = ?, merchant_gold = ?
                WHERE entity_id = ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, json.encodeToString(trading.stock))
                stmt.setInt(2, trading.merchantGold)
                stmt.setString(3, entityId)
                val updated = stmt.executeUpdate()
                if (updated == 0) {
                    return Result.failure(IllegalStateException("Entity $entityId has no trading component"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun findAll(): Result<Map<String, TradingComponent>> {
        return try {
            val conn = database.getConnection()
            val sql = "SELECT * FROM trading_stocks"

            conn.prepareStatement(sql).use { stmt ->
                val rs = stmt.executeQuery()
                val tradingComponents = mutableMapOf<String, TradingComponent>()

                while (rs.next()) {
                    val entityId = rs.getString("entity_id")
                    val merchantGold = rs.getInt("merchant_gold")
                    val stock = json.decodeFromString<List<ItemInstance>>(rs.getString("stock"))
                    val buyAnything = rs.getInt("buy_anything") == 1
                    val priceModBase = rs.getDouble("price_mod_base")

                    val trading = TradingComponent(
                        merchantGold = merchantGold,
                        stock = stock,
                        buyAnything = buyAnything,
                        priceModBase = priceModBase
                    )
                    tradingComponents[entityId] = trading
                }
                Result.success(tradingComponents)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
