package com.ucd.player.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.TouchApp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.ui.Alignment
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.ucd.player.data.Perf
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ucd.player.bible.ExternalDisplay
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.theme.GlassAlertDialog
import com.ucd.player.ui.theme.UcdTheme
import com.ucd.player.ui.theme.glass

/** Pantalla de ajustes de la app. Cada ajuste se guarda al instante. */
class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val prefs = UcdPrefs(this)
        setContent {
            UcdTheme(themeId = prefs.themeId, applySystemBars = true) {
                SettingsScreen(prefs, onBack = { finish() })
            }
        }
    }
}

/** Categorías de la pantalla principal de ajustes: icono, nombre y resumen del estado actual. */
private val CATEGORY_NAMES = listOf(
    "Reproducción", "Fundido", "Controles y gestos", "Biblia y pantalla externa",
    "Fondo de la proyección", "Rendimiento", "Datos",
)
private val CATEGORY_ICONS: List<ImageVector> = listOf(
    Icons.Rounded.PlayArrow, Icons.Rounded.GraphicEq, Icons.Rounded.TouchApp, Icons.Rounded.MenuBook,
    Icons.Rounded.Image, Icons.Rounded.Speed, Icons.Rounded.Delete,
)

/** Un ajuste individual: pertenece a una categoría y se puede encontrar con el buscador. */
private class SettingItem(val cat: Int, val title: String, val keywords: String, val content: @Composable () -> Unit)

private fun norm(s: String): String =
    java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(prefs: UcdPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var resume by remember { mutableStateOf(prefs.resumePlayback) }
    var background by remember { mutableStateOf(prefs.backgroundPlay) }
    var keepOn by remember { mutableStateOf(prefs.keepScreenOn) }
    var external by remember { mutableStateOf(ExternalDisplay.isEnabled(ctx)) }
    var red by remember { mutableStateOf(prefs.bibleRed) }
    var autoNext by remember { mutableStateOf(prefs.autoNext) }
    var repeatAll by remember { mutableStateOf(prefs.repeatAll) }
    var fadeSec by remember { mutableFloatStateOf(prefs.fadeMs / 1000f) }
    var fadePicture by remember { mutableStateOf(prefs.fadePicture) }
    var swipe by remember { mutableStateOf(prefs.swipeGestures) }
    var hideIdx by remember { mutableIntStateOf(HIDE_OPTIONS.indexOf(prefs.controlsHideMs).coerceAtLeast(0)) }
    var refMode by remember { mutableIntStateOf(prefs.bibleRefMode) }
    var refSizeIdx by remember { mutableIntStateOf(REF_SIZES.indexOf(prefs.bibleRefSize).coerceAtLeast(0)) }
    var align by remember { mutableIntStateOf(prefs.bibleAlign) }
    var spacing by remember { mutableIntStateOf(prefs.bibleLineSpacing) }
    var bgOn by remember { mutableStateOf(prefs.bibleBg) }
    var motion by remember { mutableStateOf(prefs.bibleMotion) }
    var bgMode by remember { mutableIntStateOf(prefs.bibleBgMode) }
    var dim by remember { mutableFloatStateOf(prefs.bibleDim.coerceIn(0.4f, 0.9f)) }
    var blur by remember { mutableIntStateOf(prefs.bibleBlur) }
    var preload by remember { mutableStateOf(prefs.preload) }
    var instant by remember { mutableStateOf(prefs.bibleInstant) }
    var lightExt by remember { mutableStateOf(prefs.lightModeExternal) }
    var videoExt by remember { mutableStateOf(prefs.videoOnExternal) }
    var confirmClear by remember { mutableStateOf(false) }

    // Navegación: -1 = pantalla principal con categorías; 0.. = categoría abierta.
    var current by remember { mutableIntStateOf(-1) }
    var query by remember { mutableStateOf("") }
    val searching = query.isNotBlank()

    BackHandler(enabled = current >= 0 || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else current = -1
    }

    val version = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
    }

    val items = listOf(
        // 0 · Reproducción
        SettingItem(0, "Continuar donde lo dejaste", "reanudar posicion progreso") {
            SwitchSetting(
                "Continuar donde lo dejaste",
                "Si lo apagas, cada video empieza desde el principio.",
                resume,
            ) { resume = it; prefs.resumePlayback = it }
        },
        SettingItem(0, "Seguir sonando al salir con \"atrás\"", "segundo plano audio") {
            SwitchSetting(
                "Seguir sonando al salir con \"atrás\"",
                "Si lo apagas, al salir del reproductor con \"atrás\" el video se pausa.",
                background,
            ) { background = it; prefs.backgroundPlay = it }
        },
        SettingItem(0, "Mantener la pantalla encendida", "pantalla apagar") {
            SwitchSetting(
                "Mantener la pantalla encendida",
                "Mientras estás en el reproductor, la pantalla no se apaga sola.",
                keepOn,
            ) { keepOn = it; prefs.keepScreenOn = it }
        },
        SettingItem(0, "Pasar solo al siguiente video", "siguiente automatico") {
            SwitchSetting(
                "Pasar solo al siguiente video",
                "Al terminar un video empieza el siguiente. Si lo apagas, se detiene al terminar cada uno y tú eliges cuándo seguir.",
                autoNext,
            ) { autoNext = it; prefs.autoNext = it }
        },
        SettingItem(0, "Repetir la lista al terminar", "bucle loop repetir") {
            SwitchSetting(
                "Repetir la lista al terminar",
                "Al terminar el último video, vuelve a empezar desde el primero. Se aplica al abrir una lista nueva.",
                repeatAll,
            ) { repeatAll = it; prefs.repeatAll = it }
        },
        // 1 · Fundido
        SettingItem(1, "Duración del fundido", "fade volumen transicion") {
            SliderSetting(
                "Duración del fundido",
                if (fadeSec <= 0f) "Apagado" else String.format(java.util.Locale.US, "%.1f s", fadeSec),
                "El volumen baja al final de cada video y sube al empezar el siguiente. En 0 queda apagado.",
                fadeSec, 0f..5f, 9,
            ) { fadeSec = it; prefs.fadeMs = (it * 1000f).toInt() }
        },
        SettingItem(1, "Fundido a negro en la imagen", "fade imagen oscurece") {
            SwitchSetting(
                "Fundido a negro en la imagen",
                "Además del sonido, la imagen se oscurece y vuelve. Apagado: solo cambia el volumen.",
                fadePicture,
            ) { fadePicture = it; prefs.fadePicture = it }
        },
        // 2 · Controles y gestos
        SettingItem(2, "Gestos de deslizar", "swipe volumen avanzar retroceder") {
            SwitchSetting(
                "Gestos de deslizar",
                "Deslizar para avanzar o retroceder y para cambiar el volumen. Apágalo si se mueve algo sin querer al proyectar.",
                swipe,
            ) { swipe = it; prefs.swipeGestures = it }
        },
        SettingItem(2, "Ocultar los controles tras", "botones tiempo esconder") {
            ChoiceSetting(
                "Ocultar los controles tras",
                "Tiempo que tardan en esconderse los botones al dejar de tocar la pantalla.",
                HIDE_OPTIONS.map { String.format(java.util.Locale.US, "%.1f s", it / 1000f) },
                hideIdx,
            ) { hideIdx = it; prefs.controlsHideMs = HIDE_OPTIONS[it] }
        },
        // 3 · Biblia y pantalla externa
        SettingItem(3, "Usar la pantalla externa para la Biblia", "tv proyector versiculos") {
            SwitchSetting(
                "Usar la pantalla externa para la Biblia",
                "Con un TV conectado, los versículos se ven en el TV y el celular queda libre. Apagado: se abren a pantalla completa en el celular.",
                external,
            ) { external = it; ExternalDisplay.setEnabled(ctx, it) }
        },
        SettingItem(3, "Enviar el video a la pantalla externa", "tv proyector video") {
            SwitchSetting(
                "Enviar el video a la pantalla externa",
                "Con un TV conectado, el video se ve en el TV y el celular queda libre con los controles. También puedes cambiarlo con el botón del reproductor.",
                videoExt,
            ) { videoExt = it; prefs.videoOnExternal = it }
        },
        SettingItem(3, "Palabras de Cristo en rojo", "rojo letras rojas") {
            SwitchSetting(
                "Palabras de Cristo en rojo",
                "Se aplica a la lista de versículos y a la proyección.",
                red,
            ) { red = it; prefs.bibleRed = it; ExternalDisplay.touch() }
        },
        SettingItem(3, "Cita del versículo", "referencia libro capitulo") {
            ChoiceSetting(
                "Cita del versículo",
                "Dónde se muestra el libro, capítulo y versículo (por ejemplo, Juan 3:16).",
                listOf("Debajo", "Arriba", "Oculta"),
                refMode,
            ) { refMode = it; prefs.bibleRefMode = it; ExternalDisplay.touch() }
        },
        SettingItem(3, "Tamaño de la cita", "referencia letra") {
            ChoiceSetting(
                "Tamaño de la cita",
                "Tamaño de la letra de la cita en la proyección.",
                listOf("Normal", "Grande", "Muy grande"),
                refSizeIdx,
            ) { refSizeIdx = it; prefs.bibleRefSize = REF_SIZES[it]; ExternalDisplay.touch() }
        },
        SettingItem(3, "Alineación del texto", "centrado izquierda justificado") {
            ChoiceSetting(
                "Alineación del texto",
                "Cómo se acomoda el versículo en la pantalla.",
                listOf("Centrado", "Izquierda", "Justificado"),
                align,
            ) { align = it; prefs.bibleAlign = it; ExternalDisplay.touch() }
        },
        SettingItem(3, "Espacio entre líneas", "interlineado") {
            ChoiceSetting(
                "Espacio entre líneas",
                "Más espacio hace el texto más fácil de leer a distancia, pero deja la letra un poco más pequeña.",
                listOf("Compacto", "Normal", "Amplio"),
                spacing,
            ) { spacing = it; prefs.bibleLineSpacing = it; ExternalDisplay.touch() }
        },
        // 4 · Fondo de la proyección
        SettingItem(4, "Imagen de fondo", "fondo negro foto") {
            SwitchSetting(
                "Imagen de fondo",
                "Apagado: el versículo se ve sobre fondo negro.",
                bgOn,
            ) { bgOn = it; prefs.bibleBg = it; ExternalDisplay.touch() }
        },
        SettingItem(4, "Cambio instantáneo en la pantalla externa", "microcortes fundidos transmitir") {
            SwitchSetting(
                "Cambio instantáneo en la pantalla externa",
                "Prueba para evitar microcortes de imagen o sonido al transmitir: en el TV el versículo y el fondo cambian al instante, sin fundidos.",
                instant,
            ) { instant = it; prefs.bibleInstant = it; ExternalDisplay.touch() }
        },
        SettingItem(4, "Movimiento suave del fondo", "animacion desplaza") {
            SwitchSetting(
                "Movimiento suave del fondo",
                "El fondo se desplaza muy despacio. Apágalo para que quede quieto.",
                motion,
            ) { motion = it; prefs.bibleMotion = it; ExternalDisplay.touch() }
        },
        SettingItem(4, "Cambio de fondo", "imagen cada capitulo fija") {
            ChoiceSetting(
                "Cambio de fondo",
                "Cada cuánto cambia la imagen de fondo.",
                listOf("Por capítulo", "Cada 15 s", "Cada 30 s", "Cada 60 s", "Fija"),
                bgMode,
            ) { bgMode = it; prefs.bibleBgMode = it; ExternalDisplay.touch() }
        },
        SettingItem(4, "Oscuridad del fondo", "brillo legible") {
            SliderSetting(
                "Oscuridad del fondo",
                "${(dim * 100).toInt()} %",
                "Más oscuro hace que la letra se lea mejor sobre la imagen.",
                dim, 0.4f..0.9f, 0,
            ) { dim = it; prefs.bibleDim = it; ExternalDisplay.touch() }
        },
        SettingItem(4, "Desenfoque del fondo", "blur difuso") {
            ChoiceSetting(
                "Desenfoque del fondo",
                "Las fotos ya vienen suaves. Más desenfoque las vuelve más difusas y deja la letra más limpia.",
                listOf("Normal", "Suave", "Medio", "Fuerte", "Máximo"),
                blur,
            ) { blur = it; prefs.bibleBlur = it; ExternalDisplay.touch() }
        },
        // 5 · Rendimiento
        SettingItem(5, "Modo ligero con pantalla externa", "lag microcortes animaciones") {
            SwitchSetting(
                "Modo ligero con pantalla externa",
                "Mientras haya un TV conectado, quita el desenfoque en vivo y las animaciones de menús del celular para evitar microcortes en la imagen del TV. Se activa y se apaga solo.",
                lightExt,
            ) { lightExt = it; prefs.lightModeExternal = it }
        },
        SettingItem(5, "Precargar miniaturas y fondos (prueba)", "bateria cache") {
            SwitchSetting(
                "Precargar miniaturas y fondos (prueba)",
                "Prepara en segundo plano las miniaturas de carpetas, videos y cola, y el siguiente fondo de la Biblia, para que no se carguen justo cuando los abres. Usa un poco más de batería.",
                preload,
            ) { preload = it; prefs.preload = it }
        },
        // 6 · Datos
        SettingItem(6, "Borrar el progreso de todos los videos", "datos reiniciar resetear") {
            ActionSetting(
                "Borrar el progreso de todos los videos",
                "Todos empezarán desde el principio la próxima vez.",
            ) { confirmClear = true }
        },
        SettingItem(6, "Versión de la app", "acerca de version info") {
            ListItem(
                modifier = Modifier.glass(MaterialTheme.shapes.large),
                leadingContent = { Icon(Icons.Rounded.Info, null) },
                headlineContent = { Text("Versión de la app") },
                supportingContent = { Text(version.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall) },
            )
        },
    )

    // Resumen del estado actual de cada categoría (se muestra bajo el nombre).
    val summaries = listOf(
        listOfNotNull(
            if (resume) "Continuar" else null,
            if (background) "segundo plano" else null,
            if (keepOn) "pantalla encendida" else null,
            if (autoNext) "siguiente video" else null,
            if (repeatAll) "repetir lista" else null,
        ).joinToString(", ").ifEmpty { "Todo apagado" },
        (if (fadeSec <= 0f) "Apagado" else String.format(java.util.Locale.US, "%.1f s", fadeSec)) +
            if (fadeSec > 0f && fadePicture) " · fundido a negro activado" else "",
        (if (swipe) "Gestos activados" else "Gestos desactivados") +
            " · ocultar en " + String.format(java.util.Locale.US, "%.1f s", HIDE_OPTIONS[hideIdx] / 1000f),
        "Cita " + listOf("debajo", "arriba", "oculta").getOrElse(refMode) { "debajo" } + " · texto " +
            listOf("centrado", "a la izquierda", "justificado").getOrElse(align) { "centrado" } +
            if (external) " · TV" else "",
        if (!bgOn) "Fondo negro" else "Imagen · oscuridad ${(dim * 100).toInt()} %" +
            if (motion) " · movimiento" else "",
        listOfNotNull(
            if (lightExt) "Modo ligero" else null,
            if (preload) "precarga" else null,
        ).joinToString(", ").ifEmpty { "Sin optimizaciones" },
        "Borrar el progreso de los videos",
    )

    val title = when {
        searching -> "Buscar"
        current >= 0 -> CATEGORY_NAMES[current]
        else -> "Ajustes"
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (query.isNotEmpty()) query = "" else if (current >= 0) current = -1 else onBack()
                    }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AnimatedVisibility(
                visible = current < 0,
                enter = if (Perf.enabled) EnterTransition.None else fadeIn(tween(250)) + expandVertically(tween(250)),
                exit = if (Perf.enabled) ExitTransition.None else fadeOut(tween(200)) + shrinkVertically(tween(200)),
            ) { SearchField(query) { query = it } }

            // -2 = resultados de búsqueda, -1 = categorías, 0.. = categoría abierta.
            val view = if (searching) -2 else current
            AnimatedContent(
                targetState = view,
                transitionSpec = {
                    if (Perf.enabled) EnterTransition.None togetherWith ExitTransition.None
                    else if (initialState == -1) {
                        (slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(200)))
                    } else if (targetState == -1) {
                        (slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(200)))
                    } else fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                },
                label = "settingsPage",
            ) { v ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        v == -2 -> {
                            val q = norm(query.trim())
                            val found = items.filter {
                                norm(it.title).contains(q) || norm(it.keywords).contains(q) ||
                                    norm(CATEGORY_NAMES[it.cat]).contains(q)
                            }
                            if (found.isEmpty()) {
                                Text(
                                    "No hay ajustes que coincidan con \"${query.trim()}\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(8.dp),
                                )
                            } else found.forEachIndexed { i, it -> Appear(i) { it.content() } }
                        }
                        v >= 0 -> items.filter { it.cat == v }.forEachIndexed { i, it -> Appear(i) { it.content() } }
                        else -> CATEGORY_NAMES.forEachIndexed { i, name ->
                            Appear(i) { CategoryRow(CATEGORY_ICONS[i], name, summaries[i]) { current = i } }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        GlassAlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar el progreso?") },
            text = { Text("Todos los videos volverán a empezar desde el principio. No se borra ningún video.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.clearAllPositions()
                    confirmClear = false
                    Toast.makeText(ctx, "Progreso borrado", Toast.LENGTH_SHORT).show()
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text("Buscar un ajuste") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, "Borrar") }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Aparece con fundido y un pequeño desplazamiento hacia arriba, escalonado según su posición. */
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    if (Perf.enabled) { content(); return }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 45L); shown = true }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(340), label = "appear")
    Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 36f }) { content() }
}

@Composable
private fun CategoryRow(icon: ImageVector, title: String, summary: String, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier.fillMaxWidth().bounceClick(shape, onClick).glass(shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub: @Composable (String) -> Unit = {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (Perf.enabled) sub(summary) else Crossfade(summary, animationSpec = tween(250), label = "summary") { sub(it) }
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun SwitchSetting(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.bounceClick(MaterialTheme.shapes.large) { onChange(!checked) }.glass(MaterialTheme.shapes.large),
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun ActionSetting(title: String, description: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.bounceClick(MaterialTheme.shapes.large, onClick).glass(MaterialTheme.shapes.large),
        leadingContent = { Icon(Icons.Rounded.Delete, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
    )
}

/** Opciones de tiempo (ms) para ocultar los controles del reproductor. */
private val HIDE_OPTIONS = listOf(1500, 3000, 5000, 8000)

/** Tamaños (sp) de la cita del versículo: Normal, Grande, Muy grande. */
private val REF_SIZES = listOf(22, 30, 40)

@Composable
private fun SliderSetting(
    title: String,
    valueText: String,
    description: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    ListItem(
        modifier = Modifier.glass(MaterialTheme.shapes.large),
        headlineContent = { Text(title) },
        trailingContent = { Text(valueText, style = MaterialTheme.typography.labelLarge) },
        supportingContent = {
            Column {
                Text(description, style = MaterialTheme.typography.bodySmall)
                Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
            }
        },
    )
}

@Composable
private fun ChoiceSetting(
    title: String,
    description: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    ListItem(
        modifier = Modifier.glass(MaterialTheme.shapes.large),
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEachIndexed { i, label ->
                        val on = i == selected
                        val chipBg by animateColorAsState(
                            if (on) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                            tween(220), label = "chip",
                        )
                        Box(
                            Modifier.clip(CircleShape)
                                .background(chipBg)
                                .clickable { onSelect(i) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                label, style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
    )
}
