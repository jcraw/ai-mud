package com.jcraw.mud.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraw.mud.client.GameViewModel
import com.jcraw.mud.client.LogEntry
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBar(
    playerState: com.jcraw.mud.core.PlayerState?,
    theme: UiState.Theme,
    onThemeToggle: () -> Unit,
    onCopyLog: () -> Unit,
    colors: androidx.compose.material3.ColorScheme
) {
    TopAppBar(
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playerState != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = playerState.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "HP: ${playerState.health}/${playerState.maxHealth}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Gold: ${playerState.gold}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color(0xFFD4AF37)
                            )
                        )
                        Text(
                            text = "XP: ${playerState.experiencePoints}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    Text(
                        text = "AI-MUD",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onCopyLog) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy log")
            }
            IconButton(onClick = onThemeToggle) {
                Icon(
                    imageVector = if (theme == UiState.Theme.DARK) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = "Toggle theme"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface
        )
    )
}

@Composable
fun GameLogWindow(
    logEntries: List<LogEntry>,
    colors: androidx.compose.material3.ColorScheme,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new entries arrive
    LaunchedEffect(logEntries.size) {
        if (logEntries.isNotEmpty()) {
            listState.animateScrollToItem(logEntries.size - 1)
        }
    }

    Surface(
        modifier = modifier,
        color = colors.background,
        tonalElevation = 1.dp
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logEntries) { entry ->
                LogEntryText(entry = entry, colors = colors)
            }
        }
    }
}

@Composable
fun LogEntryText(entry: LogEntry, colors: androidx.compose.material3.ColorScheme) {
    val (textColor, fontStyle, fontWeight) = when (entry.type) {
        LogEntry.EntryType.NARRATIVE -> Triple(
            colors.onBackground,
            FontStyle.Normal,
            FontWeight.Normal
        )
        LogEntry.EntryType.PLAYER_ACTION -> Triple(
            colors.primary,
            FontStyle.Italic,
            FontWeight.Bold
        )
        LogEntry.EntryType.COMBAT -> Triple(
            Color(0xFFCF6679),  // Red for combat
            FontStyle.Normal,
            FontWeight.Bold
        )
        LogEntry.EntryType.SYSTEM -> Triple(
            colors.secondary,
            FontStyle.Italic,
            FontWeight.Normal
        )
        LogEntry.EntryType.QUEST -> Triple(
            Color(0xFFD4AF37),  // Gold for quests
            FontStyle.Normal,
            FontWeight.Bold
        )
        LogEntry.EntryType.STATUS -> Triple(
            colors.tertiary,
            FontStyle.Normal,
            FontWeight.Normal
        )
    }

    Text(
        text = when (entry.type) {
            LogEntry.EntryType.PLAYER_ACTION -> "> ${entry.text}"
            else -> entry.text
        },
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            color = textColor,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            lineHeight = 24.sp
        )
    )
}

@Composable
fun GameInputField(
    onSendInput: (String) -> Unit,
    onNavigateHistory: (Int) -> String?,
    colors: androidx.compose.material3.ColorScheme,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier,
        color = colors.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { keyEvent ->
                        when {
                            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter -> {
                                if (inputText.isNotBlank()) {
                                    onSendInput(inputText)
                                    inputText = ""
                                }
                                true
                            }
                            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp -> {
                                onNavigateHistory(1)?.let { inputText = it }
                                true
                            }
                            keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown -> {
                                onNavigateHistory(-1)?.let { inputText = it } ?: run { inputText = "" }
                                true
                            }
                            else -> false
                        }
                    },
                placeholder = { Text("Enter command...") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.surfaceVariant,
                    unfocusedContainerColor = colors.surfaceVariant,
                    focusedTextColor = colors.onSurface,
                    unfocusedTextColor = colors.onSurface
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendInput(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = "Send")
            }
        }
    }
}
