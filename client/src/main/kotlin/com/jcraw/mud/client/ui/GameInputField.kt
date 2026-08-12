package com.jcraw.mud.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun GameInputField(
    onSendInput: (String) -> Unit,
    onNavigateHistory: (Int) -> String?,
    colors: ColorScheme,
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
            CommandTextField(
                state = CommandFieldState(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSendInput(inputText)
                            inputText = ""
                        }
                    },
                    onNavigateHistory = { direction ->
                        onNavigateHistory(direction)?.let { inputText = it }
                            ?: run { if (direction < 0) inputText = "" }
                    }
                ),
                focusRequester = focusRequester,
                colors = colors
            )
            SendButton(
                enabled = inputText.isNotBlank(),
                colors = colors,
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendInput(inputText)
                        inputText = ""
                    }
                }
            )
        }
    }
}

private data class CommandFieldState(
    val inputText: String,
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onNavigateHistory: (Int) -> Unit
)

@Composable
private fun CommandTextField(
    state: CommandFieldState,
    focusRequester: FocusRequester,
    colors: ColorScheme
) {
    TextField(
        value = state.inputText,
        onValueChange = state.onInputChange,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { keyEvent ->
                handleCommandKey(keyEvent, state.onSend, state.onNavigateHistory)
            },
        placeholder = { Text("Enter command...") },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceVariant,
            unfocusedContainerColor = colors.surfaceVariant,
            focusedTextColor = colors.onSurface,
            unfocusedTextColor = colors.onSurface
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { state.onSend() })
    )
}

private fun handleCommandKey(
    keyEvent: KeyEvent,
    onSend: () -> Unit,
    onNavigateHistory: (Int) -> Unit
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown) return false
    return when (keyEvent.key) {
        Key.Enter, Key.NumPadEnter -> {
            onSend()
            true
        }
        Key.DirectionUp -> {
            onNavigateHistory(1)
            true
        }
        Key.DirectionDown -> {
            onNavigateHistory(-1)
            true
        }
        else -> false
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    colors: ColorScheme,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary
        )
    ) {
        Icon(Icons.Default.Send, contentDescription = "Send")
    }
}
