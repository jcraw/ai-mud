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
    "UnusedParameter"
)

package com.jcraw.mud.reasoning.pickpocket

import com.jcraw.mud.core.CombatComponent
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random

/**
 * Handles pickpocketing logic with stealth/agility skill checks vs perception
 * Includes disposition consequences and wariness status application
 *
 * Thin facade — bodies in Pickpocket* extracts (MUD-034n).
 */
class PickpocketHandler(
    private val itemRepository: ItemRepository,
    private val random: Random = Random.Default
) {

    /**
     * Result of a pickpocket attempt
     */
    sealed class PickpocketResult {
        /**
         * Pickpocket succeeded
         * @param playerInventory Updated player inventory
         * @param targetNpc Updated target NPC (with item removed/added, or wariness status)
         * @param targetSocial Updated social component for target
         * @param targetInventory Updated inventory for target NPC
         * @param itemName Name of item stolen/placed
         * @param action "stole" or "placed"
         * @param roll The d20 roll value
         * @param total Total skill check (roll + modifier)
         * @param dc Difficulty class (target's perception)
         */
        data class Success(
            val playerInventory: InventoryComponent,
            val targetNpc: Entity.NPC,
            val targetSocial: SocialComponent?,
            val targetInventory: InventoryComponent?,
            val itemName: String,
            val action: String,
            val roll: Int,
            val total: Int,
            val dc: Int
        ) : PickpocketResult()

        /**
         * Pickpocket failed - caught by target
         * @param targetNpc Updated NPC with wariness status
         * @param targetSocial Updated social component with disposition penalty
         * @param targetCombat Updated combat component with wariness status
         * @param dispositionDelta How much disposition decreased
         * @param roll The d20 roll value
         * @param total Total skill check (roll + modifier)
         * @param dc Difficulty class (target's perception)
         * @param margin How badly they failed (negative number)
         */
        data class Caught(
            val targetNpc: Entity.NPC,
            val targetSocial: SocialComponent,
            val targetCombat: CombatComponent?,
            val dispositionDelta: Int,
            val roll: Int,
            val total: Int,
            val dc: Int,
            val margin: Int
        ) : PickpocketResult()

        /**
         * Pickpocket attempt invalid
         * @param reason Human-readable error message
         */
        data class Failure(val reason: String) : PickpocketResult()
    }

    /**
     * Attempt to steal an item or gold from an NPC
     *
     * @param playerInventory Current player inventory
     * @param playerSkills Player's skill component
     * @param targetNpc Target NPC to pickpocket
     * @param itemTarget Optional item name to steal (null = steal gold)
     * @param templates All item templates for lookups
     * @return PickpocketResult with updated states or failure reason
     */
    fun stealFromNPC(
        playerInventory: InventoryComponent,
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC,
        itemTarget: String? = null,
        templates: Map<String, ItemTemplate>
    ): PickpocketResult = PickpocketSteal.stealFromNPC(
        PickpocketCall(itemRepository, random, playerInventory, playerSkills, targetNpc, templates),
        itemTarget
    )

    /**
     * Attempt to place an item in an NPC's inventory (sneaky tactics)
     *
     * @param playerInventory Current player inventory
     * @param playerSkills Player's skill component
     * @param targetNpc Target NPC to place item on
     * @param instanceId Item instance ID to place
     * @param templates All item templates for lookups
     * @return PickpocketResult with updated states or failure reason
     */
    fun placeItemOnNPC(
        playerInventory: InventoryComponent,
        playerSkills: SkillComponent,
        targetNpc: Entity.NPC,
        instanceId: String,
        templates: Map<String, ItemTemplate>
    ): PickpocketResult = PickpocketPlace.placeItemOnNPC(
        PickpocketCall(itemRepository, random, playerInventory, playerSkills, targetNpc, templates),
        instanceId
    )
}
