@file:Suppress(
    "ReturnCount",
    "LongParameterList",
    "MaxLineLength",
    "CyclomaticComplexMethod",
)

package com.jcraw.mud.perception

/**
 * LLM JSON map: inventory / take / drop / give / equip / use / trade / treasure.
 * Pure extract (MUD-034c) — keys and outcomes unchanged.
 */
internal object IntentLlmJsonMapItems {

    fun mapItems(
        intentType: String,
        target: String?,
        npcTarget: String?,
        tradeAction: String?,
        merchantTarget: String?,
        quantity: Int?
    ): Intent? = mapBasic(intentType, target, npcTarget)
        ?: mapAdvanced(intentType, target, tradeAction, merchantTarget, quantity)

    private fun mapBasic(intentType: String, target: String?, npcTarget: String?): Intent? =
        when (intentType) {
            "inventory" -> Intent.Inventory
            "take" -> if (target != null) Intent.Take(target) else Intent.Invalid("Intent.Take what?")
            "take_all" -> Intent.TakeAll
            "drop" -> if (target != null) Intent.Drop(target) else Intent.Invalid("Intent.Drop what?")
            "give" -> mapGive(target, npcTarget)
            else -> null
        }

    private fun mapAdvanced(
        intentType: String,
        target: String?,
        tradeAction: String?,
        merchantTarget: String?,
        quantity: Int?
    ): Intent? = when (intentType) {
        "equip" -> if (target != null) Intent.Equip(target) else Intent.Invalid("Intent.Equip what?")
        "use" -> if (target != null) Intent.Use(target) else Intent.Invalid("Intent.Use what?")
        "trade" -> mapTrade(target, tradeAction, merchantTarget, quantity)
        "take_treasure" -> if (target != null) Intent.TakeTreasure(target) else Intent.Invalid("Intent.Take which treasure?")
        "return_treasure" -> if (target != null) Intent.ReturnTreasure(target) else Intent.Invalid("Return which treasure?")
        "examine_pedestal" -> Intent.ExaminePedestal(target)
        else -> null
    }

    private fun mapGive(target: String?, npcTarget: String?): Intent =
        if (target != null && npcTarget != null) {
            Intent.Give(target, npcTarget)
        } else if (target == null) {
            Intent.Invalid("Intent.Give what?")
        } else {
            Intent.Invalid("Intent.Give to whom?")
        }

    private fun mapTrade(
        target: String?,
        tradeAction: String?,
        merchantTarget: String?,
        quantity: Int?
    ): Intent {
        val action = tradeAction?.lowercase()
        val normalizedQuantity = quantity?.takeIf { it > 0 } ?: 1
        val itemTarget = target ?: ""
        return when {
            action == null -> Intent.Invalid("Intent.Trade action missing")
            action in setOf("list") -> Intent.Trade(
                action,
                if (itemTarget.isNotBlank()) itemTarget else "stock",
                normalizedQuantity,
                merchantTarget
            )
            itemTarget.isBlank() -> Intent.Invalid("${action.replaceFirstChar { it.uppercase() }} what?")
            else -> Intent.Trade(action, itemTarget, normalizedQuantity, merchantTarget)
        }
    }
}
