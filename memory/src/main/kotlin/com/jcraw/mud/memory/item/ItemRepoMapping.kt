@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList",
    "TooGenericExceptionCaught",
    "SwallowedException",
    "ThrowsCount",
    "UnusedParameter",
    "WildcardImport"
)

package com.jcraw.mud.memory.item

import com.jcraw.mud.core.EquipSlot
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.Rarity
import kotlinx.serialization.json.Json
import java.sql.ResultSet

/**
 * Shared row decode for [SQLiteItemRepository] (MUD-034m pure-move).
 */
internal object ItemRepoMapping {

    fun templateFrom(rs: ResultSet, json: Json): ItemTemplate = ItemTemplate(
        id = rs.getString("id"),
        name = rs.getString("name"),
        type = ItemType.valueOf(rs.getString("type")),
        tags = json.decodeFromString(rs.getString("tags")),
        properties = json.decodeFromString(rs.getString("properties")),
        rarity = Rarity.valueOf(rs.getString("rarity")),
        description = rs.getString("description"),
        equipSlot = rs.getString("equip_slot")?.let { EquipSlot.valueOf(it) }
    )

    fun instanceFrom(rs: ResultSet): ItemInstance = ItemInstance(
        id = rs.getString("id"),
        templateId = rs.getString("template_id"),
        quality = rs.getInt("quality"),
        charges = rs.getObject("charges") as? Int,
        quantity = rs.getInt("quantity")
    )
}
