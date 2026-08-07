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

// Los *Container que faltaban: sin ellos Material 3 rellena con su lavanda de
// fábrica, y ese púrpura aparecía en el botón de "Configura tu bot" y en el
// item activo de la barra inferior, chocando con toda la paleta cálida.
private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2C7),
    onPrimaryContainer = Color(0xFF5A2600),
    secondary = Gold,
    onSecondary = Stone,
    secondaryContainer = Color(0xFFF6E7C8),
    onSecondaryContainer = Color(0xFF463209),
    tertiary = OrangeLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9B8),
    onTertiaryContainer = Color(0xFF5A2600),
    background = Cream,
    onBackground = Stone,
    surface = Color.White,
    onSurface = Stone,
    surfaceVariant = Color(0xFFF1E7DB),
    onSurfaceVariant = Color(0xFF53493F),
    outline = Color(0xFF8C8074),
    outlineVariant = Color(0xFFD8CCBE),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val DarkColors = darkColorScheme(
    primary = OrangeLight,
    onPrimary = Color(0xFF1A0E02),
    primaryContainer = Color(0xFF7C3D10),
    onPrimaryContainer = Cream,
    secondary = Gold,
    onSecondary = Stone,
    secondaryContainer = Color(0xFF473516),
    onSecondaryContainer = Color(0xFFF6E3BE),
    tertiary = Orange,
    onTertiary = Color(0xFF1A0E02),
    tertiaryContainer = Color(0xFF6B3208),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF14110F),
    onBackground = Cream,
    surface = Color(0xFF1F1B18),
    onSurface = Cream,
    surfaceVariant = Color(0xFF2C2622),
    onSurfaceVariant = Color(0xFFD9CCBE),
    outline = Color(0xFF8F8276),
    outlineVariant = Color(0xFF3A332E),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
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
        typography = AgentTypography,
        content = content,
    )
}
