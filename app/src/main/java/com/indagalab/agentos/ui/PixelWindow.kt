package com.indagalab.agentos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ventana estilo sistema operativo retro.
 *
 * Es el contenedor de TODA la app: si el producto se llama AgentOS y la
 * pantalla principal es una oficina en pixel art, las tarjetas de Material por
 * defecto desentonan. Esto le da a cada bloque una barra de título con su
 * nombre y sus tres luces, como una ventana de escritorio.
 *
 * Deliberadamente sobrio: barra fina, borde de 1,5 dp y esquinas apenas
 * redondeadas. El objetivo es que parezca un sistema, no una parodia de
 * Windows 95.
 */
@Composable
fun PixelWindow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val acento = accent ?: c.primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = c.surface,
        border = BorderStroke(1.5.dp, c.outlineVariant),
    ) {
        Column {
            // Barra de título
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(acento.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (icon != null) {
                    Icon(icon, null, tint = acento, modifier = Modifier.size(16.dp))
                }
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                    color = acento,
                    modifier = Modifier.weight(1f),
                )
                // Las tres luces de la ventana. Decorativas, pero son lo que
                // hace que se lea como "ventana" y no como tarjeta.
                Luz(Color(0xFF6E7B8C))
                Luz(Color(0xFFD4AF6A))
                Luz(acento)
            }

            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { content() }
        }
    }
}

@Composable
private fun Luz(color: Color) {
    Box(
        Modifier
            .size(7.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color.copy(alpha = 0.65f))
    )
}
