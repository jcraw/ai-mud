@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "LongParameterList",
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random

/** Shared deps for pickpocket steal/place extracts (MUD-034n). */
internal data class PickpocketCall(
    val itemRepository: ItemRepository,
    val random: Random,
    val playerInventory: InventoryComponent,
    val playerSkills: SkillComponent,
    val targetNpc: com.jcraw.mud.core.Entity.NPC,
    val templates: Map<String, ItemTemplate>
)
