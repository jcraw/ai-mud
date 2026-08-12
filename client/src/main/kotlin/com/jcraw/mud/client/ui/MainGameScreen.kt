package com.jcraw.mud.client.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.jcraw.mud.client.GameViewModel
import com.jcraw.mud.client.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGameScreen(
    viewModel: GameViewModel,
    uiState: UiState
) {
    val colors = if (uiState.theme == UiState.Theme.DARK) DarkColorScheme else LightColorScheme
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            StatusBar(
                playerState = uiState.playerState,
                theme = uiState.theme,
                onThemeToggle = { viewModel.toggleTheme() },
                onCopyLog = {
                    clipboardManager.setText(AnnotatedString(viewModel.getLogAsText()))
                    scope.launch {
                        snackbarHostState.showSnackbar("Log copied to clipboard")
                    }
                },
                colors = colors
            )
        },
        containerColor = colors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Log window
            GameLogWindow(
                logEntries = uiState.logEntries,
                colors = colors,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Input field
            GameInputField(
                onSendInput = { viewModel.sendInput(it) },
                onNavigateHistory = { direction -> viewModel.navigateHistory(direction) },
                colors = colors,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Show errors if any
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
}
