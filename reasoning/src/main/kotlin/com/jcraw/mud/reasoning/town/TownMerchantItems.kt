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

package com.jcraw.mud.reasoning.town

import com.jcraw.mud.core.ItemInstance
import java.util.UUID

/**
 * ItemInstance factory for [TownMerchantTemplates] (MUD-034n).
 */
internal object TownMerchantItems {

    /**
     * Helper: Create ItemInstance from template ID.
     *
     * Creates instance with standard quality (5) and unique ID.
     *
     * @param templateId Template ID (must exist in item repository)
     * @param quantity Number of items in stack
     * @param quality Item quality (1-10, default 5)
     * @return ItemInstance
     */
    fun createItemInstance(
        templateId: String,
        quantity: Int = 1,
        quality: Int = 5
    ): ItemInstance {
        return ItemInstance(
            id = "${templateId}_${UUID.randomUUID()}",
            templateId = templateId,
            quality = quality,
            quantity = quantity,
            charges = null
        )
    }
}
