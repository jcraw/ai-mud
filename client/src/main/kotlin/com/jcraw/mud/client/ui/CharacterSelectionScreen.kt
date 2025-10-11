package com.jcraw.mud.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jcraw.mud.client.GameViewModel
import com.jcraw.mud.client.UiState
import com.jcraw.mud.core.CharacterTemplate

@Composable
fun CharacterSelectionScreen(
    viewModel: GameViewModel,
    theme: UiState.Theme
) {
    val colors = if (theme == UiState.Theme.DARK) DarkColorScheme else LightColorScheme

    var selectedTemplate by remember { mutableStateOf<CharacterTemplate?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Choose Your Hero",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = "Select a character to begin your adventure",
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = colors.onBackground.copy(alpha = 0.7f)
                ),
                textAlign = TextAlign.Center
            )

            // Character grid
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(CharacterTemplate.ALL_TEMPLATES) { template ->
                    CharacterCard(
                        template = template,
                        isSelected = selectedTemplate == template,
                        onClick = { selectedTemplate = template },
                        colors = colors
                    )
                }
            }

            // Start button
            Button(
                onClick = {
                    selectedTemplate?.let { viewModel.selectCharacter(it) }
                },
                enabled = selectedTemplate != null,
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                )
            ) {
                Text(
                    text = "Begin Adventure",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.Serif
                    )
                )
            }
        }
    }
}

@Composable
fun CharacterCard(
    template: CharacterTemplate,
    isSelected: Boolean,
    onClick: () -> Unit,
    colors: ColorScheme
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.primaryContainer else colors.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, colors.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = template.name,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
                )
            )

            Text(
                text = template.description,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isSelected) colors.onPrimaryContainer.copy(alpha = 0.8f) else colors.onSurface.copy(alpha = 0.7f)
                ),
                lineHeight = 20.sp
            )

            Divider(color = colors.outline.copy(alpha = 0.3f))

            // Stats display
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Stats:",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) colors.onPrimaryContainer else colors.onSurface
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatChip("STR", template.stats.strength, colors, isSelected)
                    StatChip("DEX", template.stats.dexterity, colors, isSelected)
                    StatChip("CON", template.stats.constitution, colors, isSelected)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatChip("INT", template.stats.intelligence, colors, isSelected)
                    StatChip("WIS", template.stats.wisdom, colors, isSelected)
                    StatChip("CHA", template.stats.charisma, colors, isSelected)
                }
            }
        }
    }
}

@Composable
fun StatChip(label: String, value: Int, colors: ColorScheme, isSelected: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) colors.primary.copy(alpha = 0.2f) else colors.surfaceVariant,
        modifier = Modifier.padding(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant
                )
            )
        }
    }
}
