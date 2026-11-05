package com.ucd.player.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ucd.player.bible.BgSettingsPanel
import com.ucd.player.bible.ExternalDisplay
import com.ucd.player.bible.ExternalExtras
import com.ucd.player.bible.ProjectorActivity
import com.ucd.player.data.Bible
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.theme.glass
import com.ucd.player.ui.theme.LocalUcdStyle
import com.ucd.player.ui.theme.rs
import kotlinx.coroutines.delay

/** Rojo de las palabras de Cristo sobre fondo oscuro (proyector). */
internal val ChristRed = Color(0xFFFF6B63)

@Composable
internal fun christColor(): Color = LocalUcdStyle.current.christRed

/**
 * Texto de un versículo listo para mostrar. En el archivo, las palabras de Cristo van entre
 * marcas "_": cada grupo de "_" abre o cierra el rojo.
 */
internal fun christText(raw: String, red: Boolean, redColor: Color): AnnotatedString {
    if (raw.indexOf('_') < 0) return AnnotatedString(raw)
    return buildAnnotatedString {
        var inRed = false
        var i = 0
        val n = raw.length
        while (i < n) {
            if (raw[i] == '_') {
                while (i < n && raw[i] == '_') i++
                inRed = !inRed
            } else {
                var j = i
                while (j < n && raw[j] != '_') j++
                val piece = raw.substring(i, j)
                if (inRed && red) withStyle(SpanStyle(color = redColor)) { append(piece) } else append(piece)
                i = j
            }
        }
    }
}

/** Abre la pantalla completa (proyector) en el versículo indicado (posición global). */
internal fun openProjector(ctx: Context, flat: Int) {
    // Con pantalla externa (TV) el versículo va al TV y el celular queda libre
    if (ExternalDisplay.show(ctx, flat)) return
    ctx.startActivity(Intent(ctx, ProjectorActivity::class.java).putExtra(ProjectorActivity.EXTRA_FLAT, flat))
}

private data class BibleNav(val book: Int, val chapter: Int) {
    /** Profundidad (para saber si la animación entra o sale). */
    val rank: Int get() = when {
        book < 0 -> 0
        chapter <= 0 -> 1
        else -> 2
    }
}

private val bibleListPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 104.dp)

/**
 * Pestaña Biblia. `book` = -1 muestra los libros; `chapter` = 0 muestra los capítulos del libro;
 * con capítulo se muestran los versículos. Si hay texto en el buscador se muestran resultados.
 */
@Composable
fun BibleTab(
    book: Int, chapter: Int, query: String, red: Boolean,
    onBook: (Int) -> Unit, onChapter: (Int) -> Unit, onGoTo: (Int, Int) -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = remember { UcdPrefs(ctx) }
    var panel by remember { mutableStateOf(false) }
    var bgOn by remember { mutableStateOf(prefs.bibleBg) }
    var motion by remember { mutableStateOf(prefs.bibleMotion) }
    var bgMode by remember { mutableIntStateOf(prefs.bibleBgMode) }
    var dim by remember { mutableFloatStateOf(prefs.bibleDim) }
    var toneIdx by remember { mutableIntStateOf(prefs.bibleTextColor) }
    // Vigila si se conecta/desconecta un TV mientras se está en la pestaña Biblia
    DisposableEffect(Unit) {
        ExternalDisplay.startWatching(ctx)
        onDispose { ExternalDisplay.stopWatching() }
    }
    // El botón "A" de arriba (palabras de Cristo en rojo) también cambia lo que se ve en el TV
    LaunchedEffect(red) { ExternalDisplay.touch() }
    // Posición de la barra del TV: se puede arrastrar y se recuerda
    val sp = remember { ctx.getSharedPreferences("ucd_prefs", Context.MODE_PRIVATE) }
    val density = LocalDensity.current
    var cont by remember { mutableStateOf(IntSize.Zero) }
    var barSize by remember { mutableStateOf(IntSize.Zero) }
    var barX by remember { mutableFloatStateOf(sp.getFloat("ext_bar_x", 0f)) }
    var barY by remember { mutableFloatStateOf(sp.getFloat("ext_bar_y", 0f)) }
    Box(Modifier.fillMaxSize().onSizeChanged { cont = it }) {
        BibleTabContent(book, chapter, query, red, onBook, onChapter, onGoTo)
        if (ExternalDisplay.available || ExternalDisplay.flat >= 0) {
            val bottomPx = with(density) { 108.dp.toPx() }
            val maxX = ((cont.width - barSize.width) / 2f).coerceAtLeast(0f)
            val minY = -(cont.height - barSize.height - bottomPx).coerceAtLeast(0f)
            val maxY = bottomPx
            Box(
                Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 108.dp)
                    .offset { IntOffset(barX.coerceIn(-maxX, maxX).roundToInt(), barY.coerceIn(minY, maxY).roundToInt()) }
                    .onSizeChanged { barSize = it }
                    .pointerInput(maxX, minY, maxY) {
                        val save = { sp.edit().putFloat("ext_bar_x", barX).putFloat("ext_bar_y", barY).apply() }
                        detectDragGestures(onDragEnd = { save() }, onDragCancel = { save() }) { change, drag ->
                            change.consume()
                            barX = (barX + drag.x).coerceIn(-maxX, maxX)
                            barY = (barY + drag.y).coerceIn(minY, maxY)
                        }
                    },
            ) {
                ExternalBar(onSettings = { panel = true })
            }
        }
        BgSettingsPanel(
            visible = panel,
            bgOn = bgOn, motion = motion, mode = bgMode, dim = dim, tone = toneIdx,
            onTone = { toneIdx = it; prefs.bibleTextColor = it; ExternalDisplay.touch() },
            onBg = { bgOn = it; prefs.bibleBg = it; ExternalDisplay.touch() },
            onMotion = { motion = it; prefs.bibleMotion = it; ExternalDisplay.touch() },
            onMode = { bgMode = it; prefs.bibleBgMode = it; ExternalDisplay.touch() },
            onDim = { dim = it; prefs.bibleDim = it; ExternalDisplay.touch() },
            onClose = { panel = false },
            extra = { ExternalExtras(prefs) },
        )
    }
}

/** Barra del TV: interruptor de pantalla externa y, si está encendida, anterior / siguiente, negro y ajustes. */
@Composable
private fun ExternalBar(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val flat = ExternalDisplay.flat
    val on = ExternalDisplay.enabled
    val shape = rs(28.dp)
    Surface(
        shape = shape, color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().widthIn(max = 300.dp).glass(shape),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Asa: la barra se arrastra para moverla
            Box(
                Modifier.padding(top = 2.dp).size(width = 36.dp, height = 4.dp).clip(rs(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Cast, null, modifier = Modifier.padding(start = 8.dp))
                Text(
                    when {
                        !on -> "Pantalla externa apagada"
                        flat >= 0 -> "En el TV: ${Bible.label(flat)}"
                        else -> "TV listo: toca un versículo"
                    },
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                )
                Switch(checked = on, onCheckedChange = { ExternalDisplay.setEnabled(ctx, it) })
            }
            if (on) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { ExternalDisplay.move(-1) }, enabled = flat >= 0) {
                        Icon(Icons.Rounded.ChevronLeft, "Versículo anterior")
                    }
                    IconButton(onClick = { ExternalDisplay.move(1) }, enabled = flat >= 0) {
                        Icon(Icons.Rounded.ChevronRight, "Versículo siguiente")
                    }
                    IconButton(
                        onClick = { ExternalDisplay.blackout = !ExternalDisplay.blackout }, enabled = flat >= 0,
                    ) {
                        Icon(
                            if (ExternalDisplay.blackout) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            "TV en negro",
                        )
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Fondo, letra y zoom del TV") }
                }
            }
        }
    }
}

@Composable
private fun BibleTabContent(
    book: Int, chapter: Int, query: String, red: Boolean,
    onBook: (Int) -> Unit, onChapter: (Int) -> Unit, onGoTo: (Int, Int) -> Unit,
) {
    val ctx = LocalContext.current
    var ready by remember { mutableStateOf(Bible.isLoaded) }
    LaunchedEffect(Unit) {
        if (!ready) { Bible.ensureLoaded(ctx); ready = true }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val q = query.trim()
    if (q.isNotEmpty()) {
        BibleSearch(q, red, onBook, onGoTo)
        return
    }
    AnimatedContent(
        targetState = BibleNav(book, chapter),
        transitionSpec = { popTransition(initialState.rank, targetState.rank) },
        label = "bible",
    ) { nav ->
        when {
            nav.book < 0 -> BookList(onBook)
            nav.chapter <= 0 -> ChapterGrid(nav.book, onChapter)
            else -> VerseList(nav.book, nav.chapter, red, onChapter)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun BookRow(i: Int, onBook: (Int) -> Unit) {
    val b = Bible.books[i]
    val st = LocalUcdStyle.current
    // En los temas de cristal cada libro lleva un cuadrito pastel con su abreviatura (como las tarjetas de la referencia)
    val pastel = if (st.pastels.isNotEmpty()) st.pastels[i % st.pastels.size] else null
    ListItem(
        modifier = Modifier.bounceClick(MaterialTheme.shapes.large) { onBook(i) }.glass(MaterialTheme.shapes.large),
        leadingContent = if (pastel == null) null else ({
            Box(
                Modifier.size(46.dp).clip(rs(16.dp)).background(pastel),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    b.abbr, color = st.pastelText, maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }),
        headlineContent = { Text(b.name) },
        supportingContent = { Text("${b.chapters} capítulos", style = MaterialTheme.typography.labelSmall) },
        trailingContent = if (pastel != null) null else ({
            Text(b.abbr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }),
    )
}

@Composable
private fun BookList(onBook: (Int) -> Unit) {
    val firstNt = remember { Bible.books.indexOfFirst { it.nt } }
    LazyColumn(contentPadding = bibleListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Bible.books.size, key = { Bible.books[it].key }) { i ->
            Column {
                if (i == 0) SectionTitle("Antiguo Testamento")
                if (i == firstNt) SectionTitle("Nuevo Testamento")
                BookRow(i, onBook)
            }
        }
    }
}

@Composable
private fun ChapterGrid(book: Int, onChapter: (Int) -> Unit) {
    val n = Bible.chapterCount(book)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(68.dp),
        contentPadding = bibleListPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(n) { i ->
            val shape = rs(18.dp)
            Box(
                Modifier.aspectRatio(1f).bounceClick(shape) { onChapter(i + 1) }.glass(shape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun VerseList(book: Int, chapter: Int, red: Boolean, onChapter: (Int) -> Unit) {
    val ctx = LocalContext.current
    val verses = remember(book, chapter) { Bible.chapter(book, chapter - 1) }
    val redColor = christColor()
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Text(
                "Toca un versículo para proyectarlo en pantalla completa",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        itemsIndexed(verses) { i, text ->
            val styled = remember(text, red, redColor) { christText(text, red, redColor) }
            Row(
                Modifier.fillMaxWidth()
                    .bounceClick(MaterialTheme.shapes.medium) {
                        openProjector(ctx, Bible.flatIndex(book, chapter - 1, i))
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    "${i + 1}", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(30.dp).padding(top = 3.dp),
                )
                Text(styled, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                if (chapter > 1) TextButton(onClick = { onChapter(chapter - 1) }) { Text("‹ Capítulo ${chapter - 1}") }
                else Spacer(Modifier.width(1.dp))
                if (chapter < Bible.chapterCount(book)) TextButton(onClick = { onChapter(chapter + 1) }) { Text("Capítulo ${chapter + 1} ›") }
            }
        }
    }
}

@Composable
private fun VerseCard(label: String, text: String, red: Boolean, redColor: Color, onClick: () -> Unit) {
    val styled = remember(text, red, redColor) { christText(text, red, redColor) }
    Column(
        Modifier.fillMaxWidth()
            .bounceClick(MaterialTheme.shapes.medium, onClick)
            .glass(MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(styled, style = MaterialTheme.typography.bodyMedium, maxLines = 5, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BibleSearch(q: String, red: Boolean, onBook: (Int) -> Unit, onGoTo: (Int, Int) -> Unit) {
    val ctx = LocalContext.current
    var res by remember { mutableStateOf<Bible.SearchResult?>(null) }
    // Pequeña espera: la búsqueda no corre en cada letra, solo cuando dejas de escribir
    LaunchedEffect(q) {
        delay(220)
        res = Bible.search(ctx, q)
    }
    val r = res
    if (r == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val redColor = christColor()
    val empty = r.ref == null && r.books.isEmpty() && r.hits.isEmpty()
    LazyColumn(contentPadding = bibleListPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val ref = r.ref
        if (ref != null) {
            item {
                val b = ref[0]; val c = ref[1]; val v = ref[2]
                if (v > 0) {
                    val flat = Bible.flatIndex(b, c - 1, v - 1)
                    VerseCard(Bible.label(flat), Bible.verseAt(flat), red, redColor) { openProjector(ctx, flat) }
                } else {
                    ListItem(
                        modifier = Modifier.bounceClick(MaterialTheme.shapes.large) { onGoTo(b, c) }.glass(MaterialTheme.shapes.large),
                        leadingContent = { Icon(Icons.Rounded.AutoStories, null) },
                        headlineContent = { Text("Abrir ${Bible.books[b].name} $c") },
                        supportingContent = {
                            Text("${Bible.verseCount(b, c - 1)} versículos", style = MaterialTheme.typography.labelSmall)
                        },
                    )
                }
            }
        }
        if (r.books.isNotEmpty()) {
            item { SectionTitle("Libros") }
            items(r.books.size) { k -> BookRow(r.books[k], onBook) }
        }
        if (r.hits.isNotEmpty()) {
            item {
                SectionTitle(
                    if (r.total > r.hits.size) "Mostrando ${r.hits.size} de ${r.total} versículos"
                    else if (r.total == 1) "1 versículo" else "${r.total} versículos"
                )
            }
            items(r.hits.size) { k ->
                val flat = r.hits[k]
                VerseCard(Bible.label(flat), Bible.verseAt(flat), red, redColor) { openProjector(ctx, flat) }
            }
        }
        if (empty) {
            item {
                Text(
                    "Sin resultados para \"$q\"",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }
        }
    }
}
