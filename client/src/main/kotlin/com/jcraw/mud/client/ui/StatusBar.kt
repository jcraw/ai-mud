@file:Suppress("MagicNumber")

package com.jcraw.mud.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jcraw.mud.client.UiState
import com.jcraw.mud.core.PlayerState

private val GoldTextColor = Color(0xFFD4AF37)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusBar(
    playerState: PlayerState?,
    theme: UiState.Theme,
    onThemeToggle: () -> Unit,
    onCopyLog: () -> Unit,
    colors: ColorScheme
) {
    TopAppBar(
        title = { StatusBarTitle(playerState) },
        actions = {
            StatusBarActions(
                theme = theme,
                onThemeToggle = onThemeToggle,
                onCopyLog = onCopyLog
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surface,
            titleContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface
        )
    )
}

@Composable
private fun StatusBarTitle(playerState: PlayerState?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playerState != null) {
            PlayerStatsRow(playerState)
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
}

@Composable
private fun PlayerStatsRow(playerState: PlayerState) {
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
            style = MaterialTheme.typography.bodyMedium.copy(color = GoldTextColor)
        )
        Text(
            text = "XP: ${playerState.experiencePoints}",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StatusBarActions(
    theme: UiState.Theme,
    onThemeToggle: () -> Unit,
    onCopyLog: () -> Unit
) {
    IconButton(onClick = onCopyLog) {
        Icon(Icons.Default.ContentCopy, contentDescription = "Copy log")
    }
    IconButton(onClick = onThemeToggle) {
        Icon(
            imageVector = if (theme == UiState.Theme.DARK) {
                Icons.Default.LightMode
            } else {
                Icons.Default.DarkMode
            },
            contentDescription = "Toggle theme"
        )
    }
}
