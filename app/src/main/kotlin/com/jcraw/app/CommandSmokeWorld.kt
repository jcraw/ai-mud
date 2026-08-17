package com.jcraw.app

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.GraphNodeComponent
import com.jcraw.mud.core.ItemType
import com.jcraw.mud.core.PlayerState
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SkillState
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.WorldState
import com.jcraw.mud.core.world.NodeType

/**
 * In-memory V3 fixture for headless command smoke (MUD-038). No LLM.
 */
object CommandSmokeWorld {
    const val SPACE_ID = "smoke_cell"
    const val SPACE_NAME = "Smoke Cell"
    const val PLAYER_ID = "smoke_player"
    const val ITEM_ID = "smoke_iron_sword"
    const val NPC_ID = "rat"
    const val TEMPLATE_ID = "iron_sword"
    const val ITEM_NAME = "Iron Sword"

    fun build(): WorldState {
        val sword = floorSword()
        val rat = hostileRat()
        return WorldState(
            graphNodes = mapOf(SPACE_ID to smokeNode()),
            spaces = mapOf(SPACE_ID to smokeSpace()),
            entities = mapOf(ITEM_ID to sword, NPC_ID to rat),
            players = mapOf(PLAYER_ID to smokePlayer())
        )
    }

    private fun smokePlayer(): PlayerState =
        PlayerState(id = PLAYER_ID, name = "Smoker", currentRoomId = SPACE_ID)

    private fun floorSword(): Entity.Item = Entity.Item(
        id = ITEM_ID,
        name = ITEM_NAME,
        description = "A sturdy iron blade.",
        isPickupable = true,
        itemType = ItemType.WEAPON,
        properties = mapOf("templateId" to TEMPLATE_ID)
    )

    private fun hostileRat(): Entity.NPC = Entity.NPC(
        id = NPC_ID,
        name = "Rat",
        description = "A hostile rat.",
        isHostile = true,
        components = mapOf(ComponentType.COMBAT to CombatComponent.create())
    )

    private fun smokeNode(): GraphNodeComponent =
        GraphNodeComponent(id = SPACE_ID, type = NodeType.Linear, chunkId = "smoke_chunk")

    private fun smokeSpace(): SpacePropertiesComponent = SpacePropertiesComponent(
        name = SPACE_NAME,
        description = "A bare stone cell used for command smoke.",
        entities = listOf(ITEM_ID, NPC_ID)
    )

    /** Fallback attack needs unlocked Unarmed Combat + Strength. */
    fun seedPlayerSkills(game: MudGame) {
        val unlocked = SkillState(level = 1, unlocked = true)
        val skills = SkillComponent(
            skills = mapOf(
                "Unarmed Combat" to unlocked,
                "Strength" to unlocked
            )
        )
        game.skillManager.updateSkillComponent(PLAYER_ID, skills)
    }
}
