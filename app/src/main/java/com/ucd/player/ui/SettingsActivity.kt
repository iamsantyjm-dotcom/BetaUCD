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
import androidx.compose.material.icons.rounded.RestartAlt
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") }
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
            SectionTitle("Reproducción")
            SwitchSetting(
                "Continuar donde lo dejaste",
                "Si lo apagas, cada video empieza desde el principio.",
                resume,
            ) { resume = it; prefs.resumePlayback = it }
            SwitchSetting(
                "Seguir sonando al salir con \"atrás\"",
                "Si lo apagas, al salir del reproductor con \"atrás\" el video se pausa.",
                background,
            ) { background = it; prefs.backgroundPlay = it }
            SwitchSetting(
                "Mantener la pantalla encendida",
                "Mientras estás en el reproductor, la pantalla no se apaga sola.",
                keepOn,
            ) { keepOn = it; prefs.keepScreenOn = it }
            SwitchSetting(
                "Pasar solo al siguiente video",
                "Al terminar un video empieza el siguiente. Si lo apagas, se detiene al terminar cada uno y tú eliges cuándo seguir.",
                autoNext,
            ) { autoNext = it; prefs.autoNext = it }
            SwitchSetting(
                "Repetir la lista al terminar",
                "Al terminar el último video, vuelve a empezar desde el primero. Se aplica al abrir una lista nueva.",
                repeatAll,
            ) { repeatAll = it; prefs.repeatAll = it }
            ActionSetting(
                "Borrar el progreso de todos los videos",
                "Todos empezarán desde el principio la próxima vez.",
            ) { confirmClear = true }

            SectionTitle("Fundido entre videos")
            SliderSetting(
                "Duración del fundido",
                if (fadeSec <= 0f) "Apagado" else String.format(java.util.Locale.US, "%.1f s", fadeSec),
                "El volumen baja al final de cada video y sube al empezar el siguiente. En 0 queda apagado.",
                fadeSec, 0f..5f, 9,
            ) { fadeSec = it; prefs.fadeMs = (it * 1000f).toInt() }
            SwitchSetting(
                "Fundido a negro en la imagen",
                "Además del sonido, la imagen se oscurece y vuelve. Apagado: solo cambia el volumen.",
                fadePicture,
            ) { fadePicture = it; prefs.fadePicture = it }

            SectionTitle("Controles del reproductor")
            SwitchSetting(
                "Gestos de deslizar",
                "Deslizar para avanzar o retroceder y para cambiar el volumen. Apágalo si se mueve algo sin querer al proyectar.",
                swipe,
            ) { swipe = it; prefs.swipeGestures = it }
            ChoiceSetting(
                "Ocultar los controles tras",
                "Tiempo que tardan en esconderse los botones al dejar de tocar la pantalla.",
                HIDE_OPTIONS.map { String.format(java.util.Locale.US, "%.1f s", it / 1000f) },
                hideIdx,
            ) { hideIdx = it; prefs.controlsHideMs = HIDE_OPTIONS[it] }

            SectionTitle("Biblia y pantalla externa")
            SwitchSetting(
                "Usar la pantalla externa para la Biblia",
                "Con un TV conectado, los versículos se ven en el TV y el celular queda libre. Apagado: se abren a pantalla completa en el celular.",
                external,
            ) { external = it; ExternalDisplay.setEnabled(ctx, it) }
            SwitchSetting(
                "Enviar el video a la pantalla externa",
                "Con un TV conectado, el video se ve en el TV y el celular queda libre con los controles. También puedes cambiarlo con el botón del reproductor.",
                videoExt,
            ) { videoExt = it; prefs.videoOnExternal = it }
            SwitchSetting(
                "Palabras de Cristo en rojo",
                "Se aplica a la lista de versículos y a la proyección.",
                red,
            ) { red = it; prefs.bibleRed = it; ExternalDisplay.touch() }
            ChoiceSetting(
                "Cita del versículo",
                "Dónde se muestra el libro, capítulo y versículo (por ejemplo, Juan 3:16).",
                listOf("Debajo", "Arriba", "Oculta"),
                refMode,
            ) { refMode = it; prefs.bibleRefMode = it; ExternalDisplay.touch() }
            ChoiceSetting(
                "Tamaño de la cita",
                "Tamaño de la letra de la cita en la proyección.",
                listOf("Normal", "Grande", "Muy grande"),
                refSizeIdx,
            ) { refSizeIdx = it; prefs.bibleRefSize = REF_SIZES[it]; ExternalDisplay.touch() }
            ChoiceSetting(
                "Alineación del texto",
                "Cómo se acomoda el versículo en la pantalla.",
                listOf("Centrado", "Izquierda", "Justificado"),
                align,
            ) { align = it; prefs.bibleAlign = it; ExternalDisplay.touch() }
            ChoiceSetting(
                "Espacio entre líneas",
                "Más espacio hace el texto más fácil de leer a distancia, pero deja la letra un poco más pequeña.",
                listOf("Compacto", "Normal", "Amplio"),
                spacing,
            ) { spacing = it; prefs.bibleLineSpacing = it; ExternalDisplay.touch() }

            SectionTitle("Fondo de la proyección")
            SwitchSetting(
                "Imagen de fondo",
                "Apagado: el versículo se ve sobre fondo negro.",
                bgOn,
            ) { bgOn = it; prefs.bibleBg = it; ExternalDisplay.touch() }
            SwitchSetting(
                "Cambio instantáneo en la pantalla externa",
                "Prueba para evitar microcortes de imagen o sonido al transmitir: en el TV el versículo y el fondo cambian al instante, sin fundidos.",
                instant,
            ) { instant = it; prefs.bibleInstant = it; ExternalDisplay.touch() }
            SwitchSetting(
                "Movimiento suave del fondo",
                "El fondo se desplaza muy despacio. Apágalo para que quede quieto.",
                motion,
            ) { motion = it; prefs.bibleMotion = it; ExternalDisplay.touch() }
            ChoiceSetting(
                "Cambio de fondo",
                "Cada cuánto cambia la imagen de fondo.",
                listOf("Por capítulo", "Cada 15 s", "Cada 30 s", "Cada 60 s", "Fija"),
                bgMode,
            ) { bgMode = it; prefs.bibleBgMode = it; ExternalDisplay.touch() }
            SliderSetting(
                "Oscuridad del fondo",
                "${(dim * 100).toInt()} %",
                "Más oscuro hace que la letra se lea mejor sobre la imagen.",
                dim, 0.4f..0.9f, 0,
            ) { dim = it; prefs.bibleDim = it; ExternalDisplay.touch() }
            ChoiceSetting(
                "Desenfoque del fondo",
                "Las fotos ya vienen suaves. Más desenfoque las vuelve más difusas y deja la letra más limpia.",
                listOf("Normal", "Suave", "Medio", "Fuerte", "Máximo"),
                blur,
            ) { blur = it; prefs.bibleBlur = it; ExternalDisplay.touch() }

            SectionTitle("Rendimiento")
            SwitchSetting(
                "Modo ligero con pantalla externa",
                "Mientras haya un TV conectado, quita el desenfoque en vivo y las animaciones de menús del celular para evitar microcortes en la imagen del TV. Se activa y se apaga solo.",
                lightExt,
            ) { lightExt = it; prefs.lightModeExternal = it }
            SwitchSetting(
                "Precargar miniaturas y fondos (prueba)",
                "Prepara en segundo plano las miniaturas de carpetas, videos y cola, y el siguiente fondo de la Biblia, para que no se carguen justo cuando los abres. Usa un poco más de batería.",
                preload,
            ) { preload = it; prefs.preload = it }
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
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp),
    )
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
        leadingContent = { Icon(Icons.Rounded.RestartAlt, null) },
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
                        Box(
                            Modifier.clip(CircleShape)
                                .background(
                                    if (on) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                                )
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
