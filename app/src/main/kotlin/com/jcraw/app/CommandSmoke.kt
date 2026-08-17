package com.jcraw.app

import com.jcraw.mud.core.DatabaseConfig
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

/**
 * Headless look → take → inventory → attack smoke (MUD-038). Null LLM only.
 */
fun main() {
    val dataDir = System.getenv("MUD_DATA_DIR")?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getProperty("mud.data.dir")?.trim()?.takeIf { it.isNotEmpty() }
        ?: fail("setup", "MUD_DATA_DIR unset")
    System.setProperty("mud.data.dir", dataDir)
    DatabaseConfig.init()

    val game = MudGame(initialWorldState = CommandSmokeWorld.build(), llmClient = null)
    CommandSmokeWorld.seedPlayerSkills(game)
    val adapter = RealGameEngineAdapter(game)

    assertLook(runCmd(adapter, "look"))
    runCmd(adapter, "take iron sword")
    assertTake(game)
    assertInventory(runCmd(adapter, "inventory"))
    assertAttack(runCmd(adapter, "attack rat"))
    println("PASS")
}

private fun runCmd(adapter: RealGameEngineAdapter, input: String): String =
    runBlocking { adapter.processInput(input) }

private fun assertLook(out: String) {
    if (out.contains("[No space data")) fail("look", "no space data")
    if (!out.contains(CommandSmokeWorld.SPACE_NAME)) fail("look", "missing space name")
}

private fun assertTake(game: MudGame) {
    val hasSword = game.worldState.player.inventoryComponent.items
        .any { it.templateId == CommandSmokeWorld.TEMPLATE_ID }
    if (!hasSword) fail("take", "inventory missing iron_sword")
}

private fun assertInventory(out: String) {
    if (!out.contains(CommandSmokeWorld.ITEM_NAME, ignoreCase = true)) {
        fail("inventory", "stdout missing Iron Sword")
    }
}

private fun assertAttack(out: String) {
    if (out.contains("don't see anyone", ignoreCase = true) || out.contains("Attack whom?")) {
        fail("attack", "no target")
    }
    if (out.contains("Attack failed:")) fail("attack", "AttackResult.Failure")
}

private fun fail(step: String, reason: String): Nothing {
    System.err.println("FAIL $step: $reason")
    exitProcess(1)
}
