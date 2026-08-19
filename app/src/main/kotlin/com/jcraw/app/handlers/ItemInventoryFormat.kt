package com.jcraw.app.handlers

import com.jcraw.mud.action.ItemInfoFormatter
import com.jcraw.mud.core.ItemInstance
import com.jcraw.mud.core.ItemTemplate

/** Thin console wrapper around [ItemInfoFormatter] (MUD-039). */
internal fun formatItemInfo(instance: ItemInstance, template: ItemTemplate): String =
    ItemInfoFormatter.format(instance, template)
