package com.indagalab.agentos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta de marca Indaga (cálida)
private val Orange = Color(0xFFEA580C)
private val OrangeLight = Color(0xFFF97316)
private val Gold = Color(0xFFD4AF6A)
private val Cream = Color(0xFFFFF7ED)
private val Stone = Color(0xFF1C1917)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Stone,
    tertiary = OrangeLight,
    background = Cream,
    onBackground = Stone,
    surface = Color.White,
    onSurface = Stone,
)

private val DarkColors = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Stone,
    secondary = Gold,
    onSecondary = Stone,
    tertiary = Orange,
    background = Color(0xFF14110F),
    onBackground = Cream,
    surface = Color(0xFF1C1917),
    onSurface = Cream,
)

@Composable
fun AgentOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
