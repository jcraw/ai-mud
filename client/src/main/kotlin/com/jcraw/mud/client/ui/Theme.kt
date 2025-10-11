package com.jcraw.mud.client.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fantasy-themed dark color scheme
val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFD4AF37),        // Gold
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0xFF2D2416),
    onPrimaryContainer = Color(0xFFE5C876),

    secondary = Color(0xFF8B7355),      // Bronze/Brown
    onSecondary = Color(0xFF1A1A1A),

    background = Color(0xFF0F0E0B),     // Very dark brown (parchment night)
    onBackground = Color(0xFFE8E1D3),   // Cream/parchment

    surface = Color(0xFF1C1A15),        // Dark surface
    onSurface = Color(0xFFE8E1D3),
    surfaceVariant = Color(0xFF2D2A22),
    onSurfaceVariant = Color(0xFFD0C9B9),

    outline = Color(0xFF8B7355),
    error = Color(0xFFCF6679)
)

// Fantasy-themed light color scheme
val LightColorScheme = lightColorScheme(
    primary = Color(0xFF8B6F47),        // Dark gold/bronze
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5E6D3),
    onPrimaryContainer = Color(0xFF3E2723),

    secondary = Color(0xFFD4AF37),      // Gold accent
    onSecondary = Color(0xFF1A1A1A),

    background = Color(0xFFF5EFE0),     // Light parchment
    onBackground = Color(0xFF2C2416),

    surface = Color(0xFFFFFBF5),        // Lighter surface
    onSurface = Color(0xFF2C2416),
    surfaceVariant = Color(0xFFEFE8DC),
    onSurfaceVariant = Color(0xFF4A3F33),

    outline = Color(0xFF8B7355),
    error = Color(0xFFB00020)
)
