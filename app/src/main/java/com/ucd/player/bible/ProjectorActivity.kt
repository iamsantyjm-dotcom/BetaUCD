package com.ucd.player.bible

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ucd.player.data.Bible
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.ChristRed
import com.ucd.player.ui.christText
import com.ucd.player.ui.theme.UcdTheme
import com.ucd.player.ui.theme.glassPanel
import com.ucd.player.ui.theme.rs
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import android.content.Context
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import com.ucd.player.data.Perf
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.abs
import com.ucd.player.ui.theme.glass
import kotlinx.coroutines.delay

/**
 * Versículo a pantalla completa, pensado para proyectar (Smart View): fondo negro, letra
 * grande que se ajusta sola al tamaño de la pantalla y sin botones a la vista.
 * Deslizar hacia el costado pasa al versículo siguiente / anterior (también entre capítulos y libros).
 */
class ProjectorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        val prefs = UcdPrefs(this)
        val start = intent.getIntExtra(EXTRA_FLAT, 0)
        setContent {
            UcdTheme(themeId = prefs.themeId) { ProjectorScreen(start, prefs, onClose = { finish() }) }
        }
        hideBars()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
    }

    private fun hideBars() {
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        const val EXTRA_FLAT = "ucd.bible.flat"
    }
}

/**
 * Zoom con dos dedos. Con un solo dedo no hace nada (deja pasar el deslizamiento entre
 * versículos). Entrega el cambio de zoom, el desplazamiento y el punto entre los dedos.
 */
private suspend fun PointerInputScope.detectPinch(onPinch: (zoom: Float, pan: Offset, centroid: Offset) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val z = event.calculateZoom()
                val p = event.calculatePan()
                if (z != 1f || p != Offset.Zero) onPinch(z, p, event.calculateCentroid())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

/** Colores de letra disponibles (los oscuros son para fondos claros). */
private class TextTone(val name: String, val color: Color) {
    val dark: Boolean get() = color.luminance() < 0.5f
}

private val TextTones = listOf(
    TextTone("Blanco", Color(0xFFFFFFFF)),
    TextTone("Amarillo", Color(0xFFFFE27A)),
    TextTone("Negro", Color(0xFF0A0A0A)),
    TextTone("Azul oscuro", Color(0xFF0B1E4A)),
)

// Tamaño máximo de letra (sp). Los botones − y + cambian un 12 % cada vez.
private const val MIN_SP = 24
private const val MAX_SP = 320
private fun spStep(sp: Int) = (sp * 0.12f).roundToInt().coerceAtLeast(4)

private fun verseStyle(sp: Int, tone: TextTone, align: Int = 0, spacing: Int = 1) = TextStyle(
    fontSize = sp.sp,
    lineHeight = (sp * when (spacing) { 0 -> 1.12f; 2 -> 1.45f; else -> 1.25f }).sp,
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.Medium,
    textAlign = when (align) { 1 -> TextAlign.Start; 2 -> TextAlign.Justify; else -> TextAlign.Center },
    color = tone.color,
    // Sombra oscura detrás de letra clara; resplandor claro detrás de letra oscura
    shadow = if (tone.dark) Shadow(color = Color.White.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 12f)
    else Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 4f), blurRadius = 14f),
)

@Composable
private fun ProjectorScreen(start: Int, prefs: UcdPrefs, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(Bible.isLoaded) }
    LaunchedEffect(Unit) {
        if (!ready) { Bible.ensureLoaded(ctx); ready = true }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!ready) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            return@Box
        }
        val pager = rememberPagerState(initialPage = start.coerceIn(0, Bible.totalVerses - 1)) { Bible.totalVerses }
        var maxSp by remember { mutableIntStateOf(prefs.bibleFont) }
        var red by remember { mutableStateOf(prefs.bibleRed) }
        var bgOn by remember { mutableStateOf(prefs.bibleBg) }
        var motion by remember { mutableStateOf(prefs.bibleMotion) }
        var bgMode by remember { mutableIntStateOf(prefs.bibleBgMode) }
        var dim by remember { mutableFloatStateOf(prefs.bibleDim) }
        var panel by remember { mutableStateOf(false) }
        var goPanel by remember { mutableStateOf(false) }      // "Ir a un versículo"
        var blackout by remember { mutableStateOf(false) }     // pantalla en negro
        BackHandler(enabled = goPanel) { goPanel = false }
        BackHandler(enabled = blackout) { blackout = false }
        var toneIdx by remember { mutableIntStateOf(prefs.bibleTextColor.coerceIn(0, TextTones.lastIndex)) }
        val tone = TextTones[toneIdx]
        // Zoom con dos dedos (pellizcar): vale para todos los versículos; tocar "Aa" lo restablece
        var pinch by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var areaSize by remember { mutableStateOf(IntSize.Zero) }
        val scope = rememberCoroutineScope()
        var controls by remember { mutableStateOf(false) }
        var tick by remember { mutableIntStateOf(0) }
        // Capítulo en el que estamos (libro y capítulo): cuando cambia, cambia la imagen de fondo
        val chapterKey by remember {
            derivedStateOf {
                val l = Bible.locate(pager.currentPage)
                l[0] * 1000 + l[1]
            }
        }
        LaunchedEffect(controls, tick) {
            if (controls) {
                delay(2800)
                controls = false
            }
        }
        BackHandler(enabled = panel) { panel = false }

        ProjectorBackground(
            enabled = bgOn, motion = motion, dim = dim, mode = bgMode, chapterKey = chapterKey,
            // Con letra oscura, la capa sobre la imagen es blanca (aclara) en vez de negra
            veil = if (tone.dark) Color.White else Color.Black,
            preload = prefs.preload,
        )

        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize()
                .onSizeChanged { areaSize = it }
                .pointerInput(Unit) {
                    detectPinch { z, p, c ->
                        val ns = (pinch * z).coerceIn(0.5f, 4f)
                        val ratio = ns / pinch
                        val o = Offset(areaSize.width / 2f, areaSize.height / 2f)
                        val maxX = (areaSize.width * (ns - 1f) / 2f).coerceAtLeast(0f)
                        val maxY = (areaSize.height * (ns - 1f) / 2f).coerceAtLeast(0f)
                        // El punto entre los dedos se queda en su sitio mientras se acerca o aleja
                        val np = (c - o) * (1f - ratio) + pan * ratio + p
                        pinch = ns
                        pan = Offset(np.x.coerceIn(-maxX, maxX), np.y.coerceIn(-maxY, maxY))
                    }
                }
                .pointerInput(Unit) {
                    // Toque: muestra/oculta los controles. Mantener presionado: pantalla en negro
                    detectTapGestures(onLongPress = { blackout = true; controls = false }) {
                        controls = !controls; tick++
                    }
                },
            beyondViewportPageCount = 1,
            key = { it },
            // Al soltar, el versículo se acomoda con un movimiento suave (sin rebote)
            flingBehavior = PagerDefaults.flingBehavior(
                state = pager,
                snapAnimationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 220f),
            ),
        ) { page ->
            // Animación al deslizar: el versículo que sale se desvanece y se achica un poco,
            // el que entra aparece creciendo y se mueve más lento que el dedo (efecto de profundidad)
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val d = page - pager.currentPage - pager.currentPageOffsetFraction
                    val a = abs(d).coerceIn(0f, 1f)
                    alpha = (1f - 1.1f * a).coerceIn(0f, 1f)
                    val sc = 1f - 0.10f * a
                    scaleX = sc
                    scaleY = sc
                    translationX = -d * size.width * 0.35f
                },
            ) {
                // El zoom se aplica en la GPU (fluido) y es el mismo para todos los versículos
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = pinch
                        scaleY = pinch
                        translationX = pan.x
                        translationY = pan.y
                    },
                ) {
                    VerseSlide(
                        page, maxSp, red, tone,
                        refMode = prefs.bibleRefMode, refSp = prefs.bibleRefSize,
                        align = prefs.bibleAlign, spacing = prefs.bibleLineSpacing,
                    )
                }
            }
        }

        // Controles: solo aparecen al tocar la pantalla y se ocultan solos
        AnimatedVisibility(
            visible = controls,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(12.dp),
        ) {
            val shape = rs(28.dp)
            Surface(
                shape = shape, color = Color.Transparent, contentColor = Color.White,
                modifier = Modifier.glass(shape, dark = true),
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        maxSp = (maxSp - spStep(maxSp)).coerceAtLeast(MIN_SP); prefs.bibleFont = maxSp; tick++
                    }) { Icon(Icons.Rounded.Remove, "Letra más pequeña", tint = Color.White) }
                    Text(
                        if (abs(pinch - 1f) > 0.02f) "${(pinch * 100).roundToInt()}%" else "Aa",
                        color = Color.White,
                        modifier = Modifier.clip(CircleShape).clickable {
                            scope.launch {
                                val s0 = pinch
                                val p0 = pan
                                animate(0f, 1f, animationSpec = tween(280)) { t, _ ->
                                    pinch = s0 + (1f - s0) * t
                                    pan = p0 * (1f - t)
                                }
                            }
                            tick++
                        }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    IconButton(onClick = {
                        maxSp = (maxSp + spStep(maxSp)).coerceAtMost(MAX_SP); prefs.bibleFont = maxSp; tick++
                    }) { Icon(Icons.Rounded.Add, "Letra más grande", tint = Color.White) }
                    IconButton(onClick = {
                        red = !red; prefs.bibleRed = red; tick++
                    }) {
                        Icon(
                            Icons.Rounded.FormatColorText, "Palabras de Cristo en rojo",
                            tint = if (red) ChristRed else Color.White.copy(alpha = 0.6f),
                        )
                    }
                    IconButton(onClick = { goPanel = true; controls = false }) {
                        Icon(Icons.Rounded.Search, "Ir a un versículo", tint = Color.White)
                    }
                    IconButton(onClick = { blackout = true; controls = false }) {
                        Icon(Icons.Rounded.VisibilityOff, "Pantalla en negro", tint = Color.White)
                    }
                    IconButton(onClick = { panel = true; controls = false }) {
                        Icon(Icons.Rounded.Image, "Fondo de pantalla", tint = Color.White)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White) }
                }
            }
        }

        BgSettingsPanel(
            visible = panel,
            bgOn = bgOn, motion = motion, mode = bgMode, dim = dim, tone = toneIdx,
            onTone = { toneIdx = it; prefs.bibleTextColor = it },
            onBg = { bgOn = it; prefs.bibleBg = it },
            onMotion = { motion = it; prefs.bibleMotion = it },
            onMode = { bgMode = it; prefs.bibleBgMode = it },
            onDim = { dim = it; prefs.bibleDim = it },
            onClose = { panel = false },
        )

        GoToPanel(
            visible = goPanel,
            onGo = { flat ->
                scope.launch { pager.scrollToPage(flat) }
                goPanel = false
            },
            onClose = { goPanel = false },
        )

        // Pantalla en negro: se activa con el botón de la barra o manteniendo presionada la
        // pantalla, y se quita con un toque. Aparece y desaparece con un fundido suave.
        AnimatedVisibility(
            visible = blackout,
            enter = fadeIn(tween(350)), exit = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                Modifier.fillMaxSize().background(Color.Black)
                    .pointerInput(Unit) { detectTapGestures { blackout = false } },
            )
        }
    }
}

/** Convierte lo escrito ("Ro 8:28", "1 co 13", "juan") en la posición de un versículo; -1 si no entiende. */
private suspend fun resolveTarget(ctx: Context, q: String): Int {
    if (q.isBlank()) return -1
    val r = Bible.search(ctx, q)
    val ref = r.ref
    if (ref != null) return Bible.flatIndex(ref[0], ref[1] - 1, (ref[2] - 1).coerceAtLeast(0))
    if (r.books.isNotEmpty()) return Bible.flatIndex(r.books[0], 0, 0)
    return -1
}

/** Ir a un versículo sin salir de la proyección: escribes la cita y saltas directo a ella. */
@Composable
private fun BoxScope.GoToPanel(visible: Boolean, onGo: (Int) -> Unit, onClose: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)), exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically { -it / 2 },
        exit = fadeOut(tween(160)) + slideOutVertically { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(top = 8.dp)
            .fillMaxWidth(0.82f).widthIn(max = 560.dp),
    ) {
        val ctx = LocalContext.current
        val focus = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current
        var text by remember { mutableStateOf("") }
        var target by remember { mutableIntStateOf(-1) }
        LaunchedEffect(Unit) {
            delay(220)
            runCatching { focus.requestFocus() }
        }
        LaunchedEffect(text) {
            delay(120)
            target = resolveTarget(ctx, text)
        }
        fun go() {
            if (target >= 0) {
                focusManager.clearFocus()
                onGo(target)
            }
        }
        val shape = rs(28.dp)
        Column(
            Modifier.glassPanel(shape, dark = true)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("Ej.: Ro 8:28") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    shape = rs(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                val ready = target >= 0
                Box(
                    (if (ready) Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
                    else Modifier.glass(CircleShape, dark = true).alpha(0.5f))
                        .clickable(enabled = ready) { go() }
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Ir", color = if (ready) Color(0xFF0A0F1E) else Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (target >= 0) {
                Text(
                    Bible.label(target), color = Color.White,
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    Bible.verseAt(target).replace("_", "").replace('\n', ' '),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text(
                    "Escribe una cita: Ro 8:28 · 1 Co 13:4 · Juan 3",
                    color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Ajustes pequeños del fondo: encender/apagar, movimiento, oscuridad y cada cuánto cambia. */
@Composable
internal fun BoxScope.BgSettingsPanel(
    visible: Boolean,
    bgOn: Boolean, motion: Boolean, mode: Int, dim: Float, tone: Int, onTone: (Int) -> Unit,
    onBg: (Boolean) -> Unit, onMotion: (Boolean) -> Unit, onMode: (Int) -> Unit, onDim: (Float) -> Unit,
    onClose: () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)), exit = fadeOut(tween(160)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.94f),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.94f),
        modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.82f).widthIn(max = 560.dp).heightIn(max = 340.dp),
    ) {
        val shape = rs(32.dp)
        Column(
            Modifier.glassPanel(shape, dark = true)
                .pointerInput(Unit) { detectTapGestures { } }
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Fondo y letra", color = Color.White,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Cerrar", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth().padding(end = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SwitchRow("Imagen de fondo", bgOn, onBg, Modifier.weight(1f))
                SwitchRow(
                    "Movimiento suave", motion, onMotion,
                    Modifier.weight(1f).alpha(if (bgOn) 1f else 0.4f), enabled = bgOn,
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(end = 10.dp).alpha(if (bgOn) 1f else 0.4f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Oscurecer", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(92.dp))
                Slider(
                    value = dim, onValueChange = onDim, valueRange = 0.4f..0.9f, enabled = bgOn,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Color de la letra", color = Color.White,
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                )
                TextTones.forEachIndexed { i, t ->
                    ToneSwatch(t.color, i == tone) { onTone(i) }
                    Spacer(Modifier.width(10.dp))
                }
            }
            Text(
                "Cambiar de imagen", color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.alpha(if (bgOn) 1f else 0.4f),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).alpha(if (bgOn) 1f else 0.4f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BgModeLabels.forEachIndexed { i, label -> GlassChip(label, mode == i) { onMode(i) } }
            }
            extra()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0A0F1E), checkedTrackColor = Color.White.copy(alpha = 0.92f),
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.85f), uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.4f),
            ),
        )
    }
}

/** Círculo de color para elegir el color de la letra (el elegido lleva un aro blanco). */
@Composable
private fun ToneSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, Color.White.copy(alpha = if (selected) 1f else 0.45f), CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val base = if (selected) Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.92f))
    else Modifier.glass(CircleShape, dark = true)
    Box(
        base.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (selected) Color(0xFF0A0F1E) else Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Un versículo centrado; la letra es lo más grande posible sin salirse de la pantalla. */
@Composable
private fun VerseSlide(
    flat: Int, maxSp: Int, red: Boolean, tone: TextTone,
    refMode: Int = 0, refSp: Int = 22, align: Int = 0, spacing: Int = 1,
) {
    val raw = remember(flat) { Bible.verseAt(flat) }
    val label = remember(flat) { Bible.label(flat) }
    // Con letra oscura, las palabras de Cristo van en un rojo más oscuro para que se lean
    val spans = remember(raw, red, tone) { christText(raw, red, if (tone.dark) Color(0xFFB71C1C) else ChristRed) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 20.dp)) {
        val reserve = with(density) { (if (refMode == 2) 8f else refSp * 1.7f + 20f).dp.roundToPx() }
        val w = constraints.maxWidth
        val h = (constraints.maxHeight - reserve).coerceAtLeast(100)
        // Búsqueda binaria del tamaño de letra más grande que cabe
        val size = remember(spans, w, h, maxSp, tone, align, spacing) {
            var lo = 18
            var hi = maxSp.coerceAtLeast(18)
            var best = 18
            while (lo <= hi) {
                val mid = (lo + hi) / 2
                val r = measurer.measure(text = spans, style = verseStyle(mid, tone, align, spacing), constraints = Constraints(maxWidth = w))
                if (r.size.height <= h) { best = mid; lo = mid + 1 } else hi = mid - 1
            }
            best
        }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (refMode == 1) {
                VerseRef(label, tone, refSp)
                Spacer(Modifier.height(18.dp))
            }
            Text(text = spans, style = verseStyle(size, tone, align, spacing), modifier = Modifier.fillMaxWidth())
            if (refMode == 0) {
                Spacer(Modifier.height(18.dp))
                VerseRef(label, tone, refSp)
            }
        }
    }
}

/** La cita del versículo (por ejemplo "Juan 3:16"). */
@Composable
private fun VerseRef(label: String, tone: TextTone, sp: Int) {
    Text(
        text = label,
        color = tone.color.copy(alpha = 0.7f),
        fontFamily = FontFamily.Serif,
        fontSize = sp.sp,
        style = TextStyle(
            shadow = if (tone.dark) Shadow(color = Color.White.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 10f)
            else Shadow(color = Color.Black.copy(alpha = 0.7f), offset = Offset(0f, 3f), blurRadius = 10f),
        ),
    )
}

/** Lo que se ve en la pantalla externa (TV): el versículo elegido desde el celular. */
@Composable
internal fun ExternalVerseScreen(prefs: UcdPrefs) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(Bible.isLoaded) }
    LaunchedEffect(Unit) {
        if (!ready) { Bible.ensureLoaded(ctx); ready = true }
    }
    val flat = ExternalDisplay.flat
    ExternalDisplay.tick   // al cambiar un ajuste en el celular, el TV se redibuja
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!ready || flat < 0) return@Box
        val tone = TextTones[prefs.bibleTextColor.coerceIn(0, TextTones.lastIndex)]
        val loc = Bible.locate(flat)
        ProjectorBackground(
            enabled = prefs.bibleBg, motion = prefs.bibleMotion, dim = prefs.bibleDim, mode = prefs.bibleBgMode,
            chapterKey = loc[0] * 1000 + loc[1],
            veil = if (tone.dark) Color.White else Color.Black,
            instant = prefs.bibleInstant,
            preload = prefs.preload,
        )
        val size = prefs.bibleFont
        val red = prefs.bibleRed
        val refMode = prefs.bibleRefMode
        val refSp = prefs.bibleRefSize
        val align = prefs.bibleAlign
        val spacing = prefs.bibleLineSpacing
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = ExternalDisplay.zoom
                scaleY = ExternalDisplay.zoom
                translationX = ExternalDisplay.panX * this.size.width
                translationY = ExternalDisplay.panY * this.size.height
            },
        ) {
            Crossfade(targetState = flat, animationSpec = if (prefs.bibleInstant) snap<Float>() else tween(300), label = "tv") { f ->
                VerseSlide(f, size, red, tone, refMode, refSp, align, spacing)
            }
        }
        AnimatedVisibility(
            visible = ExternalDisplay.blackout,
            enter = fadeIn(tween(350)), exit = fadeOut(tween(350)),
            modifier = Modifier.fillMaxSize(),
        ) { Box(Modifier.fillMaxSize().background(Color.Black)) }
    }
}

/**
 * Extra del panel de ajustes cuando se proyecta en el TV: un "pad" táctil
 * para acercar (pellizcar) y mover (arrastrar) el versículo que se ve en el TV.
 */
@Composable
internal fun ExternalExtras(prefs: UcdPrefs) {
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomPct = (ExternalDisplay.zoom * 100).roundToInt()
    Row(Modifier.fillMaxWidth().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Zoom en el TV: $zoomPct %", color = Color.White,
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { ExternalDisplay.resetZoom() }) { Text("Restablecer", color = Color.White) }
    }
    Box(
        Modifier.fillMaxWidth().padding(end = 10.dp).height(130.dp)
            .clip(rs(20.dp)).background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), rs(20.dp))
            .onSizeChanged { padSize = it }
            .pointerInput(Unit) {
                // Dos dedos: acercar / alejar (el punto entre los dedos se queda en su sitio)
                detectPinch { z, p, c ->
                    if (padSize.width == 0 || padSize.height == 0) return@detectPinch
                    val cur = ExternalDisplay.zoom
                    val ns = (cur * z).coerceIn(0.5f, 4f)
                    val ratio = ns / cur
                    val cn = Offset(c.x / padSize.width, c.y / padSize.height)
                    val pn = Offset(p.x / padSize.width, p.y / padSize.height)
                    val o = Offset(0.5f, 0.5f)
                    val np = (cn - o) * (1f - ratio) + Offset(ExternalDisplay.panX, ExternalDisplay.panY) * ratio + pn
                    val maxF = ((ns - 1f) / 2f).coerceAtLeast(0f)
                    ExternalDisplay.setZoom(ns, np.x.coerceIn(-maxF, maxF), np.y.coerceIn(-maxF, maxF))
                }
            }
            .pointerInput(Unit) {
                // Un dedo: mover el versículo (solo tiene efecto si hay zoom)
                detectDragGestures { change, drag ->
                    change.consume()
                    if (padSize.width == 0 || padSize.height == 0) return@detectDragGestures
                    val maxF = ((ExternalDisplay.zoom - 1f) / 2f).coerceAtLeast(0f)
                    ExternalDisplay.setZoom(
                        ExternalDisplay.zoom,
                        (ExternalDisplay.panX + drag.x / padSize.width).coerceIn(-maxF, maxF),
                        (ExternalDisplay.panY + drag.y / padSize.height).coerceIn(-maxF, maxF),
                    )
                }
            }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { ExternalDisplay.resetZoom() }) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Pellizca para acercar · arrastra para mover · doble toque para restablecer",
            color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp),
        )
    }
}
