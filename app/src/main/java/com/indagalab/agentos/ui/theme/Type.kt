package com.indagalab.agentos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Escala tipográfica de AgentOS.
 *
 * Sin fuentes de archivo a propósito: cada TTF son ~300 KB en un APK que ya
 * pesa 33 MB por CPython. El carácter sale de los pesos, el interletraje y la
 * relación de tamaños, no de una familia comprada.
 *
 * Criterio "expressive": contraste alto entre titulares y cuerpo. Los display
 * y headline van en Bold con tracking negativo (compactos, con presencia); el
 * cuerpo va holgado y con más interlínea para leerse bien en pantalla pequeña.
 */

private val Sans = FontFamily.SansSerif

// Recorta el hueco extra que Compose deja arriba y abajo de cada línea; sin
// esto los titulares grandes parecen desalineados dentro de las cards.
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

val AgentTypography = Typography(

    // Marca y números grandes (el "AgentOS" de la barra, contadores)
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8).sp,
        lineHeightStyle = Trim,
    ),

    // Títulos de pantalla
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
        lineHeightStyle = Trim,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp,
        lineHeightStyle = Trim,
    ),

    // Cabeceras de card y filas destacadas
    titleLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp,
        lineHeightStyle = Trim,
    ),

    // Cuerpo: interlínea generosa, es lo que más se lee
    bodyLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp,
    ),

    // Botones, chips y pills: tracking positivo, se leen mejor en mayúsculas
    labelLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.6.sp,
    ),
)
