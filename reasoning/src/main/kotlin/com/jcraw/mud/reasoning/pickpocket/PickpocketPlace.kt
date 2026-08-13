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

import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.InventoryComponent
import com.jcraw.mud.core.ItemTemplate
import com.jcraw.mud.core.SkillComponent
import com.jcraw.mud.core.repository.ItemRepository
import kotlin.random.Random

/**
 * Place item on NPC for [PickpocketHandler] (MUD-034n).
 */
internal object PickpocketPlace {

    fun placeItemOnNPC(call: PickpocketCall, instanceId: String): PickpocketHandler.PickpocketResult {
        val item = call.playerInventory.getItem(instanceId)
            ?: return PickpocketHandler.PickpocketResult.Failure("You don't have that item")
        val template = resolveTemplate(call.itemRepository, item.templateId)
            ?: return PickpocketHandler.PickpocketResult.Failure("Item template not found")
        return placeResolved(call, instanceId, item, template)
    }

    private fun placeResolved(
        call: PickpocketCall,
        instanceId: String,
        item: com.jcraw.mud.core.ItemInstance,
        template: ItemTemplate
    ): PickpocketHandler.PickpocketResult {
        val inv = call.targetNpc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?: return PickpocketHandler.PickpocketResult.Failure("Target has no inventory")
        if (!inv.canAdd(template, 1, call.templates)) {
            return PickpocketHandler.PickpocketResult.Failure("Target can't carry that much")
        }
        return rollPlace(call, instanceId, item, template.name, inv)
    }

    private fun rollPlace(
        call: PickpocketCall,
        instanceId: String,
        item: com.jcraw.mud.core.ItemInstance,
        itemName: String,
        inv: InventoryComponent
    ): PickpocketHandler.PickpocketResult {
        val check = PickpocketCheck.perform(call.playerSkills, call.targetNpc, call.random)
        val social = call.targetNpc.getSocialComponent()
        return if (check.success) {
            placedSuccess(call, inv, social, instanceId, item, itemName, check)
        } else {
            PickpocketCaught.handle(call.targetNpc, check, social)
        }
    }

    private fun resolveTemplate(itemRepository: ItemRepository, templateId: String): ItemTemplate? {
        val templateResult = itemRepository.findTemplateById(templateId)
        if (templateResult.isFailure || templateResult.getOrNull() == null) return null
        return templateResult.getOrNull()
    }

    private fun placedSuccess(
        call: PickpocketCall,
        inv: InventoryComponent,
        social: com.jcraw.mud.core.SocialComponent?,
        instanceId: String,
        item: com.jcraw.mud.core.ItemInstance,
        itemName: String,
        check: com.jcraw.mud.core.SkillCheckResult
    ): PickpocketHandler.PickpocketResult {
        val nextInv = inv.addItem(item)
        return PickpocketHandler.PickpocketResult.Success(
            playerInventory = call.playerInventory.removeItem(instanceId)!!,
            targetNpc = call.targetNpc.withComponent(nextInv),
            targetSocial = social,
            targetInventory = nextInv,
            itemName = itemName,
            action = "placed",
            roll = check.roll,
            total = check.total,
            dc = check.dc
        )
    }
}
