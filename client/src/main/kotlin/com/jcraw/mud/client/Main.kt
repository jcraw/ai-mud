package com.jcraw.mud.client

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jcraw.mud.client.ui.CharacterSelectionScreen
import com.jcraw.mud.client.ui.MainGameScreen
import com.jcraw.mud.reasoning.procedural.DungeonTheme
import java.io.File

fun main() = application {
    val windowState = rememberWindowState()

    Window(
        onCloseRequest = ::exitApplication,
        title = "AI-MUD Client",
        state = windowState
    ) {
        App()
    }
}

@Composable
fun App() {
    // Get OpenAI API key from environment or local.properties
    val apiKey = System.getenv("OPENAI_API_KEY")
        ?: System.getProperty("openai.api.key")
        ?: loadApiKeyFromLocalProperties()

    // For now, use mock client for character selection
    // Real client will be created after character selection
    val viewModel = remember { GameViewModel(gameClient = null, apiKey = apiKey) }
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (uiState.screen) {
                is UiState.Screen.CharacterSelection -> {
                    CharacterSelectionScreen(
                        viewModel = viewModel,
                        theme = uiState.theme
                    )
                }
                is UiState.Screen.MainGame -> {
                    MainGameScreen(
                        viewModel = viewModel,
                        uiState = uiState
                    )
                }
            }
        }
    }
}

/**
 * Load API key from local.properties file.
 */
private fun loadApiKeyFromLocalProperties(): String? {
    val possibleLocations = listOf(
        "local.properties",
        "../local.properties",
        "../../local.properties"
    )

    for (path in possibleLocations) {
        val file = File(path)
        if (file.exists()) {
            return try {
                file.readLines()
                    .firstOrNull { it.trim().startsWith("openai.api.key=") }
                    ?.substringAfter("openai.api.key=")
                    ?.trim()
            } catch (e: Exception) {
                null
            }
        }
    }

    return null
}
