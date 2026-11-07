package com.ucd.player.ui

import androidx.compose.animation.core.snap
import com.ucd.player.data.Perf
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ucd.player.ui.theme.GlassAlertDialog
import com.ucd.player.ui.theme.LocalUcdStyle
import com.ucd.player.ui.theme.Themes
import com.ucd.player.ui.theme.UcdStyle
import com.ucd.player.ui.theme.drawBackdrop
import com.ucd.player.ui.theme.rs
import kotlinx.coroutines.delay

/**
 * Menú de apariencia: interruptor de modo oscuro y la lista de temas.
 * Los cambios se ven al instante, también en este mismo cuadro.
 */
@Composable
fun ThemePickerDialog(
    currentId: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val cur = Themes.byId(currentId)
    GlassAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Apariencia") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SwitchRow(
                    Icons.Rounded.DarkMode, "Modo oscuro",
                    "Cambia entre la versión clara y la oscura del tema",
                    cur.dark,
                ) { onPick(Themes.toggleDark(cur)) }
                Text(
                    "Temas: cada uno cambia colores, fondo, formas, letra y superficies de toda la app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Themes.all.forEachIndexed { i, t -> ThemeCard(i, t, t.id == currentId) { onPick(t.id) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
    )
}

@Composable
private fun SwitchRow(
    icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit,
) {
    val cur = LocalUcdStyle.current
    val shape = rs(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cur.fg.copy(alpha = 0.06f))
            .border(1.dp, cur.fg.copy(alpha = 0.14f), shape)
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeCard(index: Int, t: UcdStyle, selected: Boolean, onClick: () -> Unit) {
    val cur = LocalUcdStyle.current
    val shape = rs(20.dp)
    // Entrada escalonada: cada tarjeta sube y aparece un poco después de la anterior
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(80L + index * 45L)
        appear.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
    }
    val line by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else cur.fg.copy(alpha = 0.14f),
        tween(250), label = "cardLine",
    )
    Row(
        Modifier
            .graphicsLayer {
                val v = appear.value
                alpha = v.coerceIn(0f, 1f)
                translationY = (1f - v) * 24.dp.toPx()
            }
            .fillMaxWidth()
            .clip(shape)
            .background(cur.fg.copy(alpha = 0.06f))
            .border(if (selected) 1.5.dp else 1.dp, line, shape)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ThemePreview(t)
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall)
            Text(
                t.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = selected,
            enter = if (Perf.enabled) EnterTransition.None else scaleIn(spring(0.5f, Spring.StiffnessMedium)) + fadeIn(),
            exit = if (Perf.enabled) ExitTransition.None else scaleOut() + fadeOut(),
        ) {
            Icon(Icons.Rounded.Check, "Tema activo", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Miniatura del tema: su fondo, una cápsula con su superficie y su color de acento. */
@Composable
private fun ThemePreview(st: UcdStyle) {
    Canvas(Modifier.size(88.dp, 58.dp).clip(rs(14.dp))) {
        drawBackdrop(st)
        val pw = size.width * 0.74f
        val ph = size.height * 0.36f
        val tl = Offset(size.width * 0.13f, size.height * 0.46f)
        val r = CornerRadius(ph * 0.5f * st.corner.coerceAtMost(1f))
        if (st.flat) {
            drawRoundRect(st.surface, tl, Size(pw, ph), r)
            drawRoundRect(st.outline, tl, Size(pw, ph), r, style = Stroke(2f))
        } else {
            drawRoundRect(st.glassTint.copy(alpha = st.fillTop.coerceAtLeast(0.2f)), tl, Size(pw, ph), r)
            drawRoundRect(st.edgeA, tl, Size(pw, ph), r, style = Stroke(2f))
        }
        drawCircle(st.accent, radius = ph * 0.28f, center = Offset(tl.x + ph * 0.5f, tl.y + ph * 0.5f))
        drawRoundRect(
            st.fg.copy(alpha = 0.7f),
            Offset(tl.x + ph * 1.05f, tl.y + ph * 0.40f),
            Size(pw * 0.45f, ph * 0.2f),
            CornerRadius(ph * 0.1f),
        )
    }
}
