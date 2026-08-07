package com.indagalab.agentos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Menu
import kotlinx.coroutines.launch

/**
 * Panel lateral que entra por la DERECHA.
 *
 * Compose sólo sabe abrir el drawer por el lado "inicial" de la disposición,
 * así que se invierte la dirección a RTL para el contenedor y se vuelve a LTR
 * dentro del contenido y del propio panel. Es el truco estándar; sin él
 * habría que reimplementar el gesto de arrastre a mano.
 */
@Composable
fun DrawerDerecho(
    estado: androidx.compose.material3.DrawerState,
    panel: @Composable () -> Unit,
    contenido: @Composable () -> Unit,
) {
    // Un único estado, el del propio drawer. Antes había un Boolean aparte
    // sincronizado con LaunchedEffect y el panel se cerraba solo: el efecto que
    // observaba currentValue disparaba en la primera composición (cuando aún
    // está Closed) y apagaba el Boolean en cuanto empezaba a abrirse.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = estado,
            // Sin gesto: el arrastre horizontal es del carrusel de plantas de
            // la sala. El panel se abre con el botón de la barra.
            gesturesEnabled = false,
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerShape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
                        modifier = Modifier.width(300.dp),
                    ) { panel() }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                contenido()
            }
        }
    }
}

/** Contenido del panel: los módulos agrupados. */
@Composable
fun PanelModulos(
    actual: Modulo,
    onElegir: (Modulo) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            "AgentOS",
            style = MaterialTheme.typography.headlineSmall,
            color = c.primary,
            modifier = Modifier.padding(start = 10.dp),
        )
        Text(
            "Tu teléfono. Tu agente.",
            style = MaterialTheme.typography.bodySmall,
            color = c.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp, bottom = 12.dp),
        )

        Modulo.porGrupo.forEach { (grupo, modulos) ->
            Text(
                grupo.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                color = c.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, top = 16.dp, bottom = 6.dp),
            )
            modulos.forEach { m ->
                val sel = m == actual
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (sel) c.primary.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable { onElegir(m) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        m.icono, null,
                        Modifier.size(19.dp),
                        tint = if (sel) c.primary else c.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            m.titulo,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (sel) c.primary else c.onSurface,
                        )
                        Text(
                            m.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Botón de menú para la barra superior. */
@Composable
fun BotonMenu(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(Lucide.Menu, contentDescription = "Módulos",
            tint = MaterialTheme.colorScheme.onBackground)
    }
}
