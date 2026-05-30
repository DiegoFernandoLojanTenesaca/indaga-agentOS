package com.indagalab.agentos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Paleta de marca Indaga (cálida)
private val Orange = Color(0xFFEA580C)
private val OrangeLight = Color(0xFFF97316)
private val Gold = Color(0xFFD4AF6A)
private val Cream = Color(0xFFFFF7ED)
private val Stone = Color(0xFF1C1917)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2C7),
    onPrimaryContainer = Color(0xFF5A2600),
    secondary = Gold,
    onSecondary = Stone,
    tertiary = OrangeLight,
    background = Cream,
    onBackground = Stone,
    surface = Color.White,
    onSurface = Stone,
    surfaceVariant = Color(0xFFF1E7DB),
    onSurfaceVariant = Color(0xFF53493F),
    outline = Color(0xFF8C8074),
    outlineVariant = Color(0xFFD8CCBE),
)

private val DarkColors = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Color(0xFF1A0E02),
    primaryContainer = Color(0xFF7C3D10),
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Stone,
    tertiary = Orange,
    background = Color(0xFF14110F),
    onBackground = Cream,
    surface = Color(0xFF1F1B18),
    onSurface = Cream,
    surfaceVariant = Color(0xFF2C2622),
    onSurfaceVariant = Color(0xFFD9CCBE),
    outline = Color(0xFF8F8276),
    outlineVariant = Color(0xFF3A332E),
)

// Espíritu "Expressive": formas redondeadas pronunciadas (cards, botones, chips).
private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun AgentOSTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = ExpressiveShapes,
        content = content,
    )
}
