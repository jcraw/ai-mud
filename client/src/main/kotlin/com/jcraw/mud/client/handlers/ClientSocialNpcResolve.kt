@file:Suppress(
    "ReturnCount",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
    "LongMethod",
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "LongParameterList"
)

package com.jcraw.mud.client.handlers

import com.jcraw.mud.client.EngineGameClient
import com.jcraw.mud.client.SpaceEntitySupport
import com.jcraw.mud.core.ComponentType
import com.jcraw.mud.core.Entity
import com.jcraw.mud.core.SocialComponent
import com.jcraw.mud.core.SpacePropertiesComponent
import com.jcraw.mud.core.TradingComponent

/**
 * NPC resolve / question context / merchant reply for [ClientSocialHandlers] (MUD-034l).
 */
internal object ClientSocialNpcResolve {

    fun resolveSpaceNpc(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        npcTarget: String?
    ): Pair<String, Entity.NPC>? {
        if (space.entities.isEmpty()) return null

        val candidates = space.entities.map { id ->
            val persisted = game.loadEntity(id) as? Entity.NPC
            val npc = persisted ?: SpaceEntitySupport.createNpcStub(SpaceEntitySupport.getStub(id))
            id to npc
        }

        val lower = npcTarget?.lowercase()
        if (lower != null) {
            candidates.firstOrNull { (_, npc) ->
                npc.name.lowercase().contains(lower) || npc.id.lowercase().contains(lower)
            }?.let { return it }
        }

        val recent = game.lastConversationNpcId
        if (recent != null) {
            candidates.firstOrNull { it.first == recent }?.let { return it }
        }

        return candidates.firstOrNull()
    }

    fun buildSpaceQuestionContext(
        game: EngineGameClient,
        space: SpacePropertiesComponent,
        npc: Entity.NPC,
        topic: String
    ): String {
        val social = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)
        return buildString {
            appendLine("Space description: ${space.description}")
            appendLine("NPC name: ${npc.name}")
            appendLine("NPC description: ${npc.description}")
            appendSocialContext(this, social)
            appendLine("Player name: ${game.worldState.player.name}")
            appendLine("Topic requested: $topic")
        }
    }

    fun merchantResponse(game: EngineGameClient, npc: Entity.NPC, topic: String): String? {
        val trading = npc.getComponent<TradingComponent>(ComponentType.TRADING) ?: return null
        val lowerTopic = topic.lowercase()
        val keywords = listOf("sell", "stock", "wares", "goods", "buy", "inventory", "offer", "shop")
        if (keywords.none { lowerTopic.contains(it) }) {
            return null
        }
        if (trading.stock.isEmpty()) {
            return "I'm afraid my shelves are empty right now."
        }
        return formatMerchantStock(game, npc, trading)
    }

    private fun appendSocialContext(builder: StringBuilder, social: SocialComponent?) {
        if (social == null) return
        builder.appendLine("NPC personality: ${social.personality}")
        if (social.traits.isNotEmpty()) {
            builder.appendLine("NPC traits: ${social.traits.joinToString()}")
        }
        builder.appendLine("NPC disposition score: ${social.disposition}")
    }

    private fun formatMerchantStock(
        game: EngineGameClient,
        npc: Entity.NPC,
        trading: TradingComponent
    ): String {
        val disposition = npc.getComponent<SocialComponent>(ComponentType.SOCIAL)?.disposition ?: 0
        val entries = trading.stock
            .sortedBy { it.templateId }
            .take(5)
            .map { instance ->
                val template = game.getItemTemplate(instance.templateId)
                val price = trading.calculateBuyPrice(template, instance, disposition)
                val quantityText = if (instance.quantity > 1) " (x${instance.quantity})" else ""
                "${template.name}$quantityText for $price gold"
            }
        val moreSuffix = if (trading.stock.size > entries.size) {
            " Ask if you'd like to see the rest."
        } else {
            ""
        }
        return "I'm selling ${entries.joinToString(", ")}.$moreSuffix"
    }
}
