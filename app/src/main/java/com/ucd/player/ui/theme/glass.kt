package com.ucd.player.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.ucd.player.data.Perf
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ucd.player.ui.bounceClick

/**
 * Superficie de cristal esmerilado de la app (estilo mate, como las referencias):
 *  - relleno translúcido suave, sin reflejos ni franjas;
 *  - un solo borde fino de luz, más brillante arriba a la izquierda y tenue abajo a la derecha.
 * Los temas planos usan una tarjeta sólida con borde fino.
 * `dark = true` fuerza el cristal oscuro neutro (útil sobre el video).
 */
@Composable
fun Modifier.glass(shape: Shape, dark: Boolean? = null): Modifier {
    val st = LocalUcdStyle.current
    val onVideo = dark == true
    if (st.flat && !onVideo) {
        return this.clip(shape).background(st.surface).border(1.dp, st.outline, shape)
    }
    if (onVideo && Perf.enabled) {
        // Modo ligero: sobre el video, una placa oscura simple (sin degradados ni reflejos)
        return this.clip(shape).background(Color.Black.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
    }
    val g = if (onVideo) Themes.Graphite else st
    val light = !g.dark
    val base = if (light) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.16f)
    val top = if (light) 0.50f else 0.18f
    val bottom = if (light) 0.26f else 0.07f
    return this
        .clip(shape)
        .background(base)
        .background(Brush.verticalGradient(listOf(
            g.glassTint.copy(alpha = top), g.glassTint.copy(alpha = bottom),
        )))
        .border(1.dp, glassEdge(light), shape)
}

/** Borde fino de luz del cristal: brilla arriba a la izquierda y se apaga abajo a la derecha. */
private fun glassEdge(light: Boolean): Brush = Brush.linearGradient(
    if (light) listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.70f))
    else listOf(Color.White.copy(alpha = 0.46f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.22f)),
)

/** Solo el borde de la superficie (para miniaturas y tarjetas con imagen). */
@Composable
fun Modifier.glassBorder(shape: Shape): Modifier {
    val st = LocalUcdStyle.current
    return if (st.flat) this.border(1.dp, st.outline, shape)
    else this.border(1.dp, glassEdge(!st.dark), shape)
}

/**
 * Cápsula flotante de la barra inferior: oscura, con un borde fino de luz (como la cápsula de las
 * referencias). Con `haze` desenfoca de verdad lo que pasa por debajo (así el texto de las tarjetas
 * ya no se transparenta) y encima lleva una capa oscura. En los temas planos es una tarjeta normal.
 */
@Composable
fun Modifier.glassCapsule(shape: Shape, haze: HazeState? = null): Modifier {
    val st = LocalUcdStyle.current
    if (st.flat) return this.glassPanel(shape)
    val base = when {
        st.navBg != Color.Unspecified -> st.navBg
        st.dark -> Color.Black
        else -> Color(0xFF272F3D)
    }
    if (haze == null || Perf.enabled) {
        return this
            .clip(shape)
            .background(base.copy(alpha = 0.94f))
            .border(1.dp, glassEdge(false), shape)
    }
    return this
        .clip(shape)
        .hazeEffect(haze) {
            blurRadius = 14.dp
            noiseFactor = 0f
            tints = listOf(HazeTint(base.copy(alpha = 0.88f)))
        }
        .border(1.dp, glassEdge(false), shape)
}

/** Cápsula pequeña sobre una miniatura (duración del video): oscura translúcida con borde de luz, sin desenfoque en vivo. */
@Composable
fun Modifier.glassBadge(shape: Shape): Modifier =
    this.clip(shape).background(Color.Black.copy(alpha = 0.42f)).border(1.dp, Color.White.copy(alpha = 0.30f), shape)

/**
 * Etiqueta de cristal sobre una imagen (nombre de carpeta, duración...): oscura translúcida con
 * borde de luz. Ya no desenfoca en vivo (con muchas tarjetas en pantalla eso causaba lag).
 * En los temas planos queda una etiqueta oscura translúcida sin borde.
 */
@Composable
fun Modifier.glassOnImage(shape: Shape): Modifier {
    val st = LocalUcdStyle.current
    if (st.flat) return this.clip(shape).background(Color.Black.copy(alpha = 0.55f))
    return this
        .clip(shape)
        .background(Color.Black.copy(alpha = 0.45f))
        .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
}


/** Degradado de marca (el mismo del icono de la app): lavanda arriba a la izquierda, azul cielo abajo a la derecha. */
private val BrandChipColors = listOf(Color(0xFFCDA2E4), Color(0xFFA9C4EC))

/** Cuadrito con el degradado del icono y un símbolo blanco: adorna las opciones de los menús. */
@Composable
fun BrandChip(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(38.dp)
            .clip(rs(13.dp))
            .background(Brush.linearGradient(BrandChipColors))
            .border(1.dp, Color.White.copy(alpha = 0.45f), rs(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(21.dp), tint = Color.White)
    }
}

/** Opción de un menú o diálogo: fila suave con el cuadrito del icono (estilo del icono de la app) y el texto. */
@Composable
fun GlassMenuItem(text: String, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = rs(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(shape, onClick)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
            .border(1.dp, Color(0xFFCDA2E4).copy(alpha = 0.30f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            BrandChip(icon)
            Spacer(Modifier.width(14.dp))
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 6.dp),
        )
    }
}

/** Botón circular de la barra superior con la superficie del tema. */
@Composable
fun GlassIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(42.dp).bounceClick(CircleShape, onClick).glass(CircleShape),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Dibuja el fondo de un tema (degradado + manchas de luz). Es estático: no hay fondo animado. */
fun DrawScope.drawBackdrop(
    st: UcdStyle,
    art: List<Color> = emptyList(), artAlpha: Float = 0f,
) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(st.bg))
    // Manchas de color sacadas de las miniaturas de tus videos (solo en los temas de cristal)
    if (art.isNotEmpty() && artAlpha > 0.01f && !st.flat) {
        val a = artAlpha * (if (st.dark) 0.50f else 0.30f)
        art.forEachIndexed { i, c ->
            val (sx, sy, sr) = BackdropArt.spots[i % BackdropArt.spots.size]
            drawRect(
                Brush.radialGradient(
                    0f to c.copy(alpha = a),
                    0.55f to c.copy(alpha = a * 0.45f),
                    1f to c.copy(alpha = 0f),
                    center = Offset(w * sx, h * sy), radius = w * sr,
                ),
            )
        }
    }
    st.glows.forEach { g ->
        drawRect(
            Brush.radialGradient(
                listOf(g.color, g.color.copy(alpha = 0f)),
                center = Offset(w * g.cx, h * g.cy), radius = w * g.radius,
            ),
        )
    }
}

/** Fondo de la app según el tema activo. Se dibuja una sola vez en una textura y cada frame solo se copia. */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, art: List<Color> = emptyList()) {
    val st = LocalUcdStyle.current
    // Negro puro: fondo negro liso, sin manchas de color ni capa intermedia
    if (Perf.pureBlack) {
        Box(modifier.fillMaxSize().background(Color.Black))
        return
    }
    val artAlpha = animateFloatAsState(if (art.isNotEmpty()) 1f else 0f, tween(1200), label = "backdropArt")
    // Cada mancha cambia de color con una transición suave (al entrar a otra carpeta, por ejemplo).
    // Si no llegan colores nuevos, conserva los últimos para que no parpadee.
    val last = remember { arrayOfNulls<Color>(3) }
    val spec = tween<Color>(900)
    val c0 = animateColorAsState(art.getOrNull(0)?.also { last[0] = it } ?: last[0] ?: Color.Transparent, spec, label = "art0")
    val c1 = animateColorAsState(art.getOrNull(1)?.also { last[1] = it } ?: last[1] ?: Color.Transparent, spec, label = "art1")
    val c2 = animateColorAsState(art.getOrNull(2)?.also { last[2] = it } ?: last[2] ?: Color.Transparent, spec, label = "art2")
    Canvas(
        modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        drawBackdrop(st, art = listOf(c0.value, c1.value, c2.value), artAlpha = artAlpha.value)
    }
}

// ---------- Menús y cuadros de diálogo ----------

private fun glassForeground(st: UcdStyle, onVideo: Boolean) = if (onVideo) Color(0xFFF1EEEA) else st.fg
private fun glassAccent(st: UcdStyle, onVideo: Boolean) =
    if (onVideo && !st.dark) Color(0xFFE8C9A6) else st.accent

/**
 * Panel flotante (menús, diálogos y barra inferior): lleva una base casi opaca debajo de la
 * superficie del tema para que se lea bien aunque haya miniaturas o videos detrás.
 */
@Composable
fun Modifier.glassPanel(shape: Shape, dark: Boolean? = null): Modifier {
    val st = LocalUcdStyle.current
    val base = if (dark == true) Color(0xFF16161A).copy(alpha = 0.90f) else st.panel.copy(alpha = 0.90f)
    return this.clip(shape).background(base).glass(shape, dark)
}

/** Fija colores de texto, iconos y acentos legibles sobre el panel. */
@Composable
private fun GlassColors(onVideo: Boolean, content: @Composable () -> Unit) {
    val st = LocalUcdStyle.current
    val fg = glassForeground(st, onVideo)
    val scheme = MaterialTheme.colorScheme.copy(
        primary = glassAccent(st, onVideo),
        onSurface = fg,
        onSurfaceVariant = fg.copy(alpha = 0.72f),
        onBackground = fg,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/** Menú desplegable con la superficie del tema. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    dark: Boolean? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val onVideo = dark == true
    val shape = rs(24.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier.glassPanel(shape, dark),
    ) {
        GlassColors(onVideo) { Column(content = content) }
    }
}

/** Cuadro de diálogo con la superficie del tema (en el reproductor se usa con dark = true). */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    dark: Boolean? = null,
) {
    val st = LocalUcdStyle.current
    val onVideo = dark == true
    val shape = rs(32.dp)
    val fg = glassForeground(st, onVideo)
    // Entrada elástica: aparece creciendo con un pequeño rebote
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow))
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { GlassColors(onVideo) { confirmButton() } },
        modifier = modifier
            .graphicsLayer {
                val p = appear.value
                alpha = p.coerceIn(0f, 1f)
                val sc = 0.88f + 0.12f * p
                scaleX = sc
                scaleY = sc
            }
            .glassPanel(shape, dark),
        dismissButton = if (dismissButton == null) null else ({ GlassColors(onVideo) { dismissButton() } }),
        title = if (title == null) null else ({ GlassColors(onVideo) { title() } }),
        text = if (text == null) null else ({ GlassColors(onVideo) { text() } }),
        shape = shape,
        containerColor = Color.Transparent,
        titleContentColor = fg,
        textContentColor = fg,
        tonalElevation = 0.dp,
    )
}
