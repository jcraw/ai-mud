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
 * Steal gold 30% / item / weight-fail order for [PickpocketHandler] (MUD-034n).
 */
internal object PickpocketSteal {

    fun stealFromNPC(call: PickpocketCall, itemTarget: String?): PickpocketHandler.PickpocketResult {
        val inv = call.targetNpc.getComponent<InventoryComponent>(ComponentType.INVENTORY)
            ?: return PickpocketHandler.PickpocketResult.Failure("Target has no inventory")
        val fail = validateSteal(call.itemRepository, inv, itemTarget)
        if (fail != null) return fail
        return resolveSteal(call, inv, itemTarget)
    }

    private fun resolveSteal(
        call: PickpocketCall,
        inv: InventoryComponent,
        itemTarget: String?
    ): PickpocketHandler.PickpocketResult {
        val check = PickpocketCheck.perform(call.playerSkills, call.targetNpc, call.random)
        val social = call.targetNpc.getSocialComponent()
        val gold = itemTarget == null || itemTarget.equals("gold", ignoreCase = true)
        return when {
            !check.success -> PickpocketCaught.handle(call.targetNpc, check, social)
            gold -> stealGold(call, inv, social, check)
            else -> stealItem(call, inv, social, itemTarget, check)
        }
    }

    private fun validateSteal(
        itemRepository: ItemRepository,
        targetInventory: InventoryComponent,
        itemTarget: String?
    ): PickpocketHandler.PickpocketResult.Failure? {
        val stealGold = itemTarget == null || itemTarget.equals("gold", ignoreCase = true)
        if (stealGold) {
            return if (targetInventory.gold <= 0) {
                PickpocketHandler.PickpocketResult.Failure("Target has no gold")
            } else {
                null
            }
        }
        val item = findNamedItem(itemRepository, targetInventory, itemTarget)
        return if (item == null) {
            PickpocketHandler.PickpocketResult.Failure("Target doesn't have that item")
        } else {
            null
        }
    }

    private fun findNamedItem(
        itemRepository: ItemRepository,
        targetInventory: InventoryComponent,
        itemTarget: String?
    ) = targetInventory.items.firstOrNull { instance ->
        itemRepository.findTemplateById(instance.templateId)
            .getOrNull()?.name?.equals(itemTarget, ignoreCase = true) == true
    }

    private fun stealGold(
        call: PickpocketCall,
        inv: InventoryComponent,
        social: com.jcraw.mud.core.SocialComponent?,
        check: com.jcraw.mud.core.SkillCheckResult
    ): PickpocketHandler.PickpocketResult {
        val amount = (inv.gold * 0.3).toInt().coerceAtLeast(1)
        val nextInv = inv.removeGold(amount)!!
        return stoleSuccess(call.playerInventory.addGold(amount), call.targetNpc.withComponent(nextInv), social, nextInv, "$amount gold", check)
    }

    private fun stealItem(
        call: PickpocketCall,
        inv: InventoryComponent,
        social: com.jcraw.mud.core.SocialComponent?,
        itemTarget: String?,
        check: com.jcraw.mud.core.SkillCheckResult
    ): PickpocketHandler.PickpocketResult {
        val item = findNamedItem(call.itemRepository, inv, itemTarget)!!
        val template = call.itemRepository.findTemplateById(item.templateId).getOrNull()!!
        if (!call.playerInventory.canAdd(template, 1, call.templates)) {
            return PickpocketHandler.PickpocketResult.Failure("You can't carry that much (weight limit exceeded)")
        }
        return takeStolenItem(call, inv, social, item, template.name, check)
    }

    private fun takeStolenItem(
        call: PickpocketCall,
        inv: InventoryComponent,
        social: com.jcraw.mud.core.SocialComponent?,
        item: com.jcraw.mud.core.ItemInstance,
        itemName: String,
        check: com.jcraw.mud.core.SkillCheckResult
    ): PickpocketHandler.PickpocketResult {
        val nextInv = inv.removeItem(item.id)!!
        return stoleSuccess(call.playerInventory.addItem(item), call.targetNpc.withComponent(nextInv), social, nextInv, itemName, check)
    }

    private fun stoleSuccess(
        playerInventory: InventoryComponent,
        targetNpc: Entity.NPC,
        targetSocial: com.jcraw.mud.core.SocialComponent?,
        targetInventory: InventoryComponent,
        itemName: String,
        skillCheckResult: com.jcraw.mud.core.SkillCheckResult
    ) = PickpocketHandler.PickpocketResult.Success(
        playerInventory = playerInventory,
        targetNpc = targetNpc,
        targetSocial = targetSocial,
        targetInventory = targetInventory,
        itemName = itemName,
        action = "stole",
        roll = skillCheckResult.roll,
        total = skillCheckResult.total,
        dc = skillCheckResult.dc
    )
}
