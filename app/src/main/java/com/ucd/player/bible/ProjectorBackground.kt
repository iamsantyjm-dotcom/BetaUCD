package com.ucd.player.bible

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ucd.player.data.UcdPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sin
import kotlin.random.Random

/** Opciones de "Cambiar de imagen" (el número guardado es la posición en esta lista). */
internal val BgModeLabels = listOf("Por capítulo", "15 s", "30 s", "60 s", "Fija")

/** Segundos entre cambios (0 = no cambia por tiempo). */
private fun bgSeconds(mode: Int): Int = when (mode) {
    1 -> 15
    2 -> 30
    3 -> 60
    else -> 0
}

/** Las imágenes son los archivos "fondo*.jpg" de assets: si agregas más, se usan solas. */
private fun listBackgrounds(ctx: Context): List<String> =
    runCatching {
        ctx.assets.list("")?.filter { it.startsWith("fondo") && it.endsWith(".jpg") }?.sorted()
    }.getOrNull() ?: emptyList()

/**
 * Caché de fondos ya desenfocados, por "nombre+nivel". Evita decodificar y volver a pasar el
 * desenfoque de caja cada vez que se repite una imagen (al pasar de capítulo, al cambiar de
 * verso, etc). Las imágenes guardadas son chicas (quedaron a 1/4 de tamaño), así que el límite
 * en KB alcanza para varias decenas sin presionar la memoria.
 */
private val backgroundCache = object : LruCache<String, ImageBitmap>(6 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.width * value.height * 4 / 1024).coerceAtLeast(1)
}

private suspend fun decodeBackground(ctx: Context, name: String, blur: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val cacheKey = "$name:$blur"
        backgroundCache.get(cacheKey)?.let { return@withContext it }
        runCatching {
            // Con desenfoque, extraBlur termina reduciendo la imagen a 1/4: decodificarla ya a ese
            // tamaño con inSampleSize evita cargar el bitmap completo (unos 6-7 MB) solo para
            // achicarlo después. Sin desenfoque se decodifica entera, como antes.
            val sample = sampleSizeFor(blur)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = ctx.assets.open(name).use { BitmapFactory.decodeStream(it, null, opts) }
            val result = if (bmp == null) null else extraBlur(bmp, blur, alreadySampled = sample > 1).asImageBitmap()
            if (result != null) backgroundCache.put(cacheKey, result)
            result
        }.getOrNull()
    }

/** inSampleSize (potencia de 2) a pedirle al decoder según el nivel de desenfoque. */
private fun sampleSizeFor(level: Int): Int = if (BlurRadius.getOrElse(level) { 0 } > 0) 4 else 1

/** Radio del desenfoque extra por nivel (se aplica en una copia a 1/4 de tamaño). 0 = sin cambios. */
private val BlurRadius = intArrayOf(0, 5, 8, 12, 17)

/**
 * Suma desenfoque a la imagen: la reduce a 1/4 (salvo que ya venga reducida por el decoder, ver
 * [alreadySampled]) y le pasa 3 veces un desenfoque de caja. Se hace una sola vez al cargar la
 * imagen (no en cada fotograma), así que no pesa durante la proyección. La imagen resultante es
 * más chica; al dibujarla se estira y queda igual de suave.
 */
private fun extraBlur(src: Bitmap, level: Int, alreadySampled: Boolean = false): Bitmap {
    val r = BlurRadius.getOrElse(level) { 0 }
    if (r <= 0) return src
    val scaled = if (alreadySampled) {
        // El decoder ya entregó el bitmap a ~1/4 de tamaño (inSampleSize): no hace falta reescalar.
        src
    } else {
        val w = (src.width / 4).coerceAtLeast(16)
        val h = (src.height / 4).coerceAtLeast(16)
        Bitmap.createScaledBitmap(src, w, h, true)
    }
    val w = scaled.width
    val h = scaled.height
    var a = IntArray(w * h)
    var b = IntArray(w * h)
    scaled.getPixels(a, 0, w, 0, 0, w, h)
    repeat(3) {
        boxBlur(a, b, w, h, r, horizontal = true)
        boxBlur(b, a, w, h, r, horizontal = false)
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(a, 0, w, 0, 0, w, h)
    return out
}

/** Desenfoque de caja en una dirección, repitiendo los bordes. */
private fun boxBlur(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val step = if (horizontal) 1 else w
    val div = 2 * r + 1
    for (line in 0 until lines) {
        val start = if (horizontal) line * w else line
        var rs = 0
        var gs = 0
        var bs = 0
        for (i in -r..r) {
            val p = src[start + i.coerceIn(0, len - 1) * step]
            rs += (p shr 16) and 0xFF
            gs += (p shr 8) and 0xFF
            bs += p and 0xFF
        }
        for (x in 0 until len) {
            dst[start + x * step] = (0xFF shl 24) or ((rs / div) shl 16) or ((gs / div) shl 8) or (bs / div)
            val add = src[start + (x + r + 1).coerceAtMost(len - 1) * step]
            val sub = src[start + (x - r).coerceAtLeast(0) * step]
            rs += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
            gs += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
            bs += (add and 0xFF) - (sub and 0xFF)
        }
    }
}

/** Orden aleatorio sin repetir: usa todas las imágenes antes de volver a empezar. */
private class BgPicker(private val names: List<String>) {
    private val queue = ArrayDeque<String>()
    private var last: String? = null

    private fun fill() {
        if (queue.isEmpty()) {
            val l = names.shuffled().toMutableList()
            if (l.size > 1 && l.first() == last) l.add(l.removeAt(0))
            queue.addAll(l)
        }
    }

    fun next(): String? {
        if (names.isEmpty()) return null
        fill()
        return queue.removeFirst().also { last = it }
    }

    /** La imagen que saldrá después (sin sacarla de la lista). */
    fun peek(): String? {
        if (names.isEmpty()) return null
        fill()
        return queue.first()
    }
}

/**
 * Fondo de la proyección: imagen ya desenfocada (viene así dentro de la app), con zoom y
 * desplazamiento muy lentos, motas de luz que flotan y una capa oscura encima para que el
 * texto siempre se lea. Cambia de imagen al pasar de capítulo o cada cierto tiempo.
 */
@Composable
internal fun ProjectorBackground(
    enabled: Boolean,
    motion: Boolean,
    dim: Float,
    mode: Int,
    chapterKey: Int,
    veil: Color = Color.Black,
    instant: Boolean = false,
    preload: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    // Desenfoque extra (ajuste): se vuelve a leer cuando cambia un ajuste (ExternalDisplay.touch)
    val settingsTick = ExternalDisplay.tick
    val blur = remember(settingsTick) { UcdPrefs(ctx).bibleBlur }
    val picker = remember { BgPicker(listBackgrounds(ctx)) }
    var currentName by remember { mutableStateOf<String?>(null) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var request by remember { mutableIntStateOf(0) }
    // La primera imagen siempre trae el polvo dorado de siempre; en cada cambio la luz cambia de estilo
    var lightStyle by remember { mutableStateOf(LightStyle.Dust) }

    // Carga (en segundo plano) la siguiente imagen cada vez que se pide un cambio
    // Con "precargar", la siguiente imagen ya está lista antes de que haga falta
    val ahead = remember { arrayOfNulls<Pair<String, ImageBitmap>>(1) }
    LaunchedEffect(enabled, request) {
        if (!enabled) return@LaunchedEffect
        val name = picker.next() ?: return@LaunchedEffect
        val pre = ahead[0]
        ahead[0] = null
        val bmp = if (pre != null && pre.first == name) pre.second else decodeBackground(ctx, name, blur)
        if (bmp != null) {
            bitmap = bmp
            currentName = name
            if (request > 0) {
                lightStyle = listOf(LightStyle.Dust, LightStyle.Dust, LightStyle.Fireflies, LightStyle.Bokeh, LightStyle.Rays)
                    .filter { it != lightStyle }.random()
            }
        }
        if (preload) {
            delay(2500)
            val n = picker.peek()
            if (n != null) decodeBackground(ctx, n, blur)?.let { ahead[0] = n to it }
        }
    }
    // Si cambia el desenfoque, la imagen actual se carga de nuevo con el nuevo nivel
    LaunchedEffect(blur) {
        ahead[0] = null
        val name = currentName ?: return@LaunchedEffect
        decodeBackground(ctx, name, blur)?.let { bitmap = it }
    }
    // Cambio al pasar de capítulo (la primera vez no cuenta: ya hay imagen inicial)
    var firstChapter by remember { mutableStateOf(true) }
    LaunchedEffect(chapterKey) {
        if (firstChapter) {
            firstChapter = false
        } else if (mode == 0) {
            request++
        }
    }
    // Cambio cada cierto tiempo
    LaunchedEffect(enabled, mode) {
        val sec = bgSeconds(mode)
        if (enabled && sec > 0) {
            while (true) {
                delay(sec * 1000L)
                request++
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (enabled) {
            Crossfade(targetState = bitmap, animationSpec = if (instant) snap<Float>() else tween(1800), label = "fondo") { bmp ->
                if (bmp != null) FondoImagen(bmp, motion)
            }
            if (motion) {
                Crossfade(targetState = lightStyle, animationSpec = if (instant) snap<Float>() else tween(1800), label = "luz") { st ->
                    LightLayer(st)
                }
            }
            // Capa oscura: más clara al centro (donde va el texto) y más cerrada en las orillas
            Box(
                Modifier.fillMaxSize().drawBehind {
                    val a0 = dim * 0.9f
                    val a1 = (dim + 0.2f).coerceAtMost(0.95f)
                    drawRect(
                        Brush.radialGradient(
                            listOf(veil.copy(alpha = a0), veil.copy(alpha = a1)),
                            center = this.center, radius = size.maxDimension * 0.65f,
                        ),
                    )
                },
            )
        }
    }
}

/** Cómo se mueve la imagen de fondo: cada imagen que entra usa uno distinto al azar. */
private enum class MoveStyle { Classic, ZoomIn, ZoomOut, PanRight, PanLeft, Drift }

/** La luz que flota sobre la imagen: cambia junto con la imagen. */
private enum class LightStyle { Dust, Fireflies, Bokeh, Rays }

private const val TAU = 6.2831855f

/** La imagen con un zoom y un desplazamiento muy lentos (efecto "Ken Burns"), con varios recorridos. */
@Composable
private fun FondoImagen(bmp: ImageBitmap, motion: Boolean) {
    val style = remember(bmp) { MoveStyle.values().random() }
    val dir = remember(bmp) { if (Random.nextBoolean()) 1f else -1f }
    val durMs = remember(bmp) { 26000 + Random.nextInt(0, 5) * 3500 }
    val prog: State<Float>? = if (motion) {
        rememberInfiniteTransition(label = "kb").animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durMs, easing = LinearEasing), RepeatMode.Reverse),
            label = "kbp",
        )
    } else null
    Image(
        bitmap = bmp, contentDescription = null, contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            val k = prog?.value ?: 0.5f
            val w = size.width
            val h = size.height
            var s = 1.12f
            var tx = 0f
            var ty = 0f
            // En todos el zoom es mayor que el desplazamiento: nunca se ven bordes
            when (style) {
                MoveStyle.Classic -> {
                    s = 1.10f + 0.12f * k
                    tx = dir * (k - 0.5f) * w * 0.05f
                    ty = (0.5f - k) * h * 0.03f
                }
                MoveStyle.ZoomIn -> {
                    s = 1.08f + 0.14f * k
                    ty = (0.5f - k) * h * 0.012f
                }
                MoveStyle.ZoomOut -> {
                    s = 1.22f - 0.14f * k
                    tx = dir * (k - 0.5f) * w * 0.015f
                }
                MoveStyle.PanRight -> {
                    s = 1.14f
                    tx = (k - 0.5f) * w * 0.07f
                    ty = (0.5f - k) * h * 0.01f
                }
                MoveStyle.PanLeft -> {
                    s = 1.14f
                    tx = (0.5f - k) * w * 0.07f
                    ty = (k - 0.5f) * h * 0.01f
                }
                MoveStyle.Drift -> {
                    s = 1.12f + 0.05f * k
                    tx = dir * (k - 0.5f) * w * 0.04f
                    ty = (k - 0.5f) * h * 0.05f
                }
            }
            scaleX = s
            scaleY = s
            translationX = tx
            translationY = ty
        },
    )
}

@Composable
private fun LightLayer(style: LightStyle) {
    when (style) {
        LightStyle.Dust -> LightMotes()
        LightStyle.Fireflies -> Fireflies()
        LightStyle.Bokeh -> Bokeh()
        LightStyle.Rays -> LightRays()
    }
}

/** Reloj lento (0 a 1 cada 90 s) que comparten las capas de luz. Los ciclos son enteros: el bucle no se nota. */
@Composable
private fun rememberLightTime(): State<Float> =
    rememberInfiniteTransition(label = "luz").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(90000, easing = LinearEasing)),
        label = "t",
    )

/** Motas de luz muy tenues que suben despacio, como polvo dorado en un rayo de sol. */
@Composable
private fun LightMotes() {
    // [x inicial, fase, tamaño, vueltas verticales, vaivenes, brillo]
    val motes = remember {
        val r = Random(5)
        List(16) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), 0.6f + r.nextFloat() * 1.4f,
                (1 + r.nextInt(3)).toFloat(), (1 + r.nextInt(2)).toFloat(), 0.25f + r.nextFloat() * 0.45f)
        }
    }
    val time by rememberLightTime()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time
        for (m in motes) {
            val up = (t * m[3] + m[1]) % 1f
            val y = h * (1.05f - 1.1f * up)
            val x = w * m[0] + sin((t * m[4] + m[1]) * TAU) * 24.dp.toPx()
            val a = m[5] * sin(up * 3.1415927f)       // aparece y se desvanece
            val r = m[2] * 6.dp.toPx()
            drawCircle(Color(0xFFFFF1CC).copy(alpha = a * 0.55f), r, Offset(x, y))
            drawCircle(Color(0xFFFFF1CC).copy(alpha = a * 0.16f), r * 2.4f, Offset(x, y))
        }
    }
}

/** Luciérnagas: puntos de luz que se quedan cerca de su sitio, vagan despacio y se encienden y apagan. */
@Composable
private fun Fireflies() {
    // [x, y, fase x, fase y, fase brillo, vaivenes x, vaivenes y, pulsos de brillo, tamaño]
    val flies = remember {
        val r = Random(11)
        List(11) {
            floatArrayOf(0.08f + r.nextFloat() * 0.84f, 0.12f + r.nextFloat() * 0.76f,
                r.nextFloat(), r.nextFloat(), r.nextFloat(),
                (2 + r.nextInt(3)).toFloat(), (2 + r.nextInt(3)).toFloat(),
                (5 + r.nextInt(5)).toFloat(), 0.7f + r.nextFloat() * 0.9f)
        }
    }
    val time by rememberLightTime()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time
        for (f in flies) {
            val x = w * f[0] + sin((t * f[5] + f[2]) * TAU) * w * 0.05f
            val y = h * f[1] + sin((t * f[6] + f[3]) * TAU) * h * 0.06f
            val pulse = 0.5f + 0.5f * sin((t * f[7] + f[4]) * TAU)
            val a = pulse * pulse                     // casi apagadas la mayor parte del tiempo
            val r = f[8] * 5.dp.toPx()
            drawCircle(Color(0xFFFFF6B8).copy(alpha = a * 0.75f), r * 0.55f, Offset(x, y))
            drawCircle(Color(0xFFFFF6B8).copy(alpha = a * 0.20f), r * 2.6f, Offset(x, y))
        }
    }
}

/** Círculos grandes y muy suaves (desenfoque de lente) que suben lentísimo. */
@Composable
private fun Bokeh() {
    // [x, fase, radio en dp, vueltas verticales, brillo, fase del vaivén]
    val discs = remember {
        val r = Random(23)
        List(9) {
            floatArrayOf(r.nextFloat(), r.nextFloat(), 18f + r.nextFloat() * 34f,
                (1 + r.nextInt(2)).toFloat(), 0.05f + r.nextFloat() * 0.07f, r.nextFloat())
        }
    }
    val time by rememberLightTime()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time
        for (d in discs) {
            val up = (t * d[3] + d[1]) % 1f
            val y = h * (1.15f - 1.3f * up)
            val x = w * d[0] + sin((t + d[5]) * TAU) * 18.dp.toPx()
            val a = d[4] * sin(up * 3.1415927f)
            val r = d[2].dp.toPx()
            drawCircle(Color(0xFFFFE6B0).copy(alpha = a * 0.7f), r, Offset(x, y))
            drawCircle(Color(0xFFFFE6B0).copy(alpha = a), r * 0.72f, Offset(x, y))
        }
    }
}

/** Tres haces de luz inclinados, muy tenues, que se mecen y cambian de brillo como luz entre nubes. */
@Composable
private fun LightRays() {
    // [centro x (fracción), ancho (fracción), fase, vaivenes, brillo]
    val beams = remember {
        listOf(
            floatArrayOf(0.30f, 0.16f, 0.00f, 1f, 0.070f),
            floatArrayOf(0.55f, 0.22f, 0.35f, 2f, 0.055f),
            floatArrayOf(0.80f, 0.13f, 0.70f, 1f, 0.065f),
        )
    }
    val time by rememberLightTime()
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = time
        val c = Color(0xFFFFF1CC)
        for (b in beams) {
            val cx = w * b[0] + sin((t * b[3] + b[2]) * TAU) * w * 0.05f
            val bw = w * b[1]
            val a = b[4] * (0.55f + 0.45f * sin((t * 2f + b[2]) * TAU))
            rotate(degrees = 16f, pivot = Offset(cx, 0f)) {
                drawRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, c.copy(alpha = a), Color.Transparent),
                        startX = cx - bw / 2f, endX = cx + bw / 2f,
                    ),
                    topLeft = Offset(cx - bw / 2f, -h * 0.3f),
                    size = Size(bw, h * 1.8f),
                )
            }
        }
    }
}
