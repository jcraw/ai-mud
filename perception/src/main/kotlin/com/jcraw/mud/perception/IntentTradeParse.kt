@file:Suppress(
    "ReturnCount",
    "CyclomaticComplexMethod",
)

package com.jcraw.mud.perception

/**
 * Intent.Trade / list-stock pure helpers for [IntentRecognizer].
 * Pure extract (MUD-034c) — no parsing semantics change.
 */
internal object IntentTradeParse {

    fun parseTradeCommand(
        args: String?,
        action: String,
        missingItemMessage: String,
        merchantPrepositions: List<String>
    ): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Invalid(missingItemMessage)
        }
        val (itemSegment, merchant) = splitMerchant(args.trim(), merchantPrepositions)
        if (itemSegment.isEmpty()) {
            return Intent.Invalid(missingItemMessage)
        }
        val (quantity, itemName) = parseQuantityAndItem(itemSegment)
        if (itemName.isBlank()) {
            return Intent.Invalid(missingItemMessage)
        }
        val sanitizedMerchant = IntentSayParse.sanitizeNpcTarget(merchant)
        return Intent.Trade(action.lowercase(), itemName, quantity, sanitizedMerchant)
    }

    fun parseListStock(args: String?): Intent {
        if (args.isNullOrBlank()) {
            return Intent.Trade(action = "list", target = "stock", quantity = 1, merchantTarget = null)
        }
        val (descriptor, merchant) = extractListDescriptor(args.trim())
        val sanitizedMerchant = IntentSayParse.sanitizeNpcTarget(merchant)
        return Intent.Trade(action = "list", target = "stock", quantity = 1, merchantTarget = sanitizedMerchant)
    }

    private fun splitMerchant(
        itemSegmentIn: String,
        merchantPrepositions: List<String>
    ): Pair<String, String?> {
        var itemSegment = itemSegmentIn
        var merchant: String? = null
        for (preposition in merchantPrepositions) {
            val regex = Regex("\\b$preposition\\b", RegexOption.IGNORE_CASE)
            val match = regex.find(itemSegment)
            if (match != null) {
                merchant = itemSegment.substring(match.range.last + 1).trim().takeIf { it.isNotBlank() }
                itemSegment = itemSegment.substring(0, match.range.first).trim()
                break
            }
        }
        return itemSegment to merchant
    }

    private fun parseQuantityAndItem(itemSegment: String): Pair<Int, String> {
        val quantityMatch = Regex("^(\\d+)\\s+(.+)$").find(itemSegment)
        val quantity = quantityMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 } ?: 1
        val itemName = quantityMatch?.groupValues?.getOrNull(2)?.trim().takeIf { !it.isNullOrBlank() } ?: itemSegment
        return quantity to itemName
    }

    private fun extractListDescriptor(args: String): Pair<String, String?> {
        var descriptor = args
        var merchant: String? = null
        val prepositions = listOf("from", "at", "with")
        for (preposition in prepositions) {
            val regex = Regex("\\b$preposition\\b", RegexOption.IGNORE_CASE)
            val match = regex.find(descriptor)
            if (match != null) {
                merchant = descriptor.substring(match.range.last + 1).trim().takeIf { it.isNotBlank() }
                descriptor = descriptor.substring(0, match.range.first).trim()
                break
            }
        }
        if (descriptor.isBlank()) {
            descriptor = "stock"
        }
        val lowerDescriptor = descriptor.lowercase()
        if (!lowerDescriptor.contains("stock") && !lowerDescriptor.contains("wares")) {
            merchant = merchant ?: descriptor
        }
        return descriptor to merchant
    }
}
