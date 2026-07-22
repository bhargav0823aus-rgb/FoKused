package com.focusgate.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// FoKused — bold, Gen-Z, always-dark palette pulled straight from the dragon
// mascot: true-black canvas, vivid yellow body, bright green crest/tail, a dash
// of orange from the wings.
private val Yellow = Color(0xFFFFD21E)       // mascot body
private val Green = Color(0xFF3FBF4B)        // mascot crest / tail
private val GreenDeep = Color(0xFF2E8B3D)    // wing green (user bubbles)
private val Orange = Color(0xFFF0871F)       // wing membrane accent

private val FoKusedColors = darkColorScheme(
    primary = Yellow,
    onPrimary = Color(0xFF1A1500),
    primaryContainer = Yellow,
    onPrimaryContainer = Color(0xFF1A1500),
    secondary = Green,
    onSecondary = Color(0xFF06210A),
    secondaryContainer = GreenDeep,          // user chat bubbles
    onSecondaryContainer = Color(0xFFEAFBEA),
    tertiary = Orange,
    onTertiary = Color(0xFF1A0E00),
    background = Color(0xFF0A0A0A),           // true black
    onBackground = Color(0xFFF6F6EC),
    surface = Color(0xFF141410),
    onSurface = Color(0xFFF6F6EC),
    surfaceVariant = Color(0xFF23231A),       // agent chat bubbles
    onSurfaceVariant = Color(0xFFD6D6C2),
    outline = Color(0xFF585843),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun FocusGateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FoKusedColors, content = content)
}
