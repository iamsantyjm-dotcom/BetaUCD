package com.ucd.player.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ucd.player.data.UcdPrefs
import com.ucd.player.download.DlEntry
import com.ucd.player.download.DlQueue
import com.ucd.player.download.DlStatus
import com.ucd.player.download.JobStatus
import com.ucd.player.download.DownloadService
import com.ucd.player.download.Downloader
import com.ucd.player.download.Downloads
import com.ucd.player.download.Errors
import com.ucd.player.download.FormatInfo
import com.ucd.player.download.MediaInfo
import com.ucd.player.download.Saver
import com.ucd.player.download.Ytdl
import com.ucd.player.player.PlayerActivity
import com.ucd.player.ui.theme.BrandChip
import com.ucd.player.ui.theme.GlassBackdrop
import com.ucd.player.ui.theme.GlassIconButton
import com.ucd.player.ui.theme.LocalUcdStyle
import com.ucd.player.ui.theme.UcdTheme
import com.ucd.player.ui.theme.glass
import com.ucd.player.ui.theme.glassBorder
import com.ucd.player.ui.theme.rs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/** Descargar un video con yt-dlp: pegar enlace → analizar → elegir calidad y carpeta → descargar (servicio en primer plano). */
class DownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        DlQueue.init(this)
        val prefs = UcdPrefs(this)
        // Si llega desde "Compartir" de otra app, el enlace viene en el texto compartido
        val shared = if (intent?.action == Intent.ACTION_SEND) extractUrl(intent.getStringExtra(Intent.EXTRA_TEXT)) else null
        setContent {
            UcdTheme(themeId = prefs.themeId, applySystemBars = true) {
                DownloadScreen(initialUrl = shared ?: "", onBack = { finish() })
            }
        }
    }
}

private fun extractUrl(text: String?): String? =
    text?.let { Regex("""https?://\S+""").find(it)?.value }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadScreen(initialUrl: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<MediaInfo?>(null) }
    var quality by remember { mutableStateOf<Int?>(null) }   // null = mejor
    var audioOnly by remember { mutableStateOf(false) }
    var showFormats by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    val prefs = remember { UcdPrefs(ctx) }
    var tree by remember { mutableStateOf(prefs.downloadTree) }
    val dl by Downloads.state.collectAsState()
    val entries by DlQueue.items.collectAsState()
    val queueActive by DlQueue.active.collectAsState()
    val running = entries.any { it.status == JobStatus.Running }
    val busy = entries.any { it.status == JobStatus.Queued || it.status == JobStatus.Running }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            prefs.downloadTree = uri.toString()
            tree = uri.toString()
        }
    }

    fun analyze() {
        val u = extractUrl(url) ?: url.trim()
        if (u.isBlank()) { error = "Pega un enlace primero."; return }
        loading = true; error = null; info = null
        Downloads.clearFinished()
        scope.launch {
            Ytdl.analyze(ctx, u)
                .onSuccess { info = it; quality = null; audioOnly = false; showFormats = false }
                .onFailure { error = friendly(it) }
            loading = false
        }
    }
    LaunchedEffect(Unit) { if (initialUrl.isNotBlank()) analyze() }

    Box(Modifier.fillMaxSize()) {
        GlassBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "Descargar video",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = LocalUcdStyle.current.titleSize.sp),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    navigationIcon = { GlassIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { pad ->
            Column(
                Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Enlace
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Enlace del video") },
                    singleLine = true,
                    shape = rs(18.dp),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { url = it.trim() }
                        }) { Icon(Icons.Rounded.ContentPaste, "Pegar") }
                    },
                )
                PillButton(
                    if (loading) "Analizando…" else "Analizar enlace",
                    onClick = { analyze() }, enabled = !loading, modifier = Modifier.fillMaxWidth(),
                )
                if (loading) {
                    Text(
                        "Puede tardar un poco si hay que preparar o actualizar yt-dlp.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                info?.let { m ->
                    // Video: miniatura, título y canal
                    GlassCard {
                        Thumbnail(m.thumbnail)
                        Text(m.title, style = MaterialTheme.typography.titleMedium)
                        val meta = listOfNotNull(
                            m.uploader.takeIf { it.isNotBlank() },
                            m.durationSec.takeIf { it > 0 }?.let { fmtDuration(it) },
                        ).joinToString(" · ")
                        if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    // Calidad
                    GlassCard {
                        SectionLabel("Calidad")
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilterPill("Mejor", !audioOnly && quality == null) { audioOnly = false; quality = null }
                            m.heights.forEach { h ->
                                FilterPill("${h}p", !audioOnly && quality == h) { audioOnly = false; quality = h }
                            }
                            FilterPill("Solo audio", audioOnly) { audioOnly = true }
                        }
                        val est = if (audioOnly) m.formats.filter { it.hasAudio && !it.hasVideo }.maxOfOrNull { it.sizeBytes } ?: 0L
                        else m.estimateSize(quality)
                        Text(
                            "Tamaño aproximado: " + if (est > 0) fmtBytes(est) else "no informado",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showFormats = !showFormats }) {
                            Text(if (showFormats) "Ocultar formatos" else "Ver todos los formatos (${m.formats.size})")
                        }
                        if (showFormats) {
                            m.formats.forEach { Text(formatLine(it), style = MaterialTheme.typography.bodySmall) }
                        }
                    }

                    // Carpeta de destino
                    GlassCard {
                        SectionLabel("Guardar en")
                        Text(Saver.label(tree), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "Cada descarga usa la carpeta que había al añadirla a la cola.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            PillButton("Elegir carpeta", onClick = { pickFolder.launch(null) }, filled = false)
                            if (tree != null) {
                                TextButton(onClick = { prefs.downloadTree = null; tree = null }) {
                                    Text("Usar predeterminada")
                                }
                            }
                        }
                    }

                    PillButton(
                        when {
                            busy -> "Añadir a la cola"
                            audioOnly -> "Descargar audio"
                            else -> "Descargar"
                        },
                        onClick = {
                            val parts = if (!audioOnly && m.formats.any { it.hasVideo && !it.hasAudio }) 2 else 1
                            val added = DownloadService.enqueue(ctx, m.pageUrl, m.title, quality ?: 0, audioOnly, parts, tree)
                            Toast.makeText(
                                ctx, if (added) "Añadido a la cola" else "Esa descarga ya está en la cola", Toast.LENGTH_SHORT,
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = true,
                    )
                }

                // Cola e historial (se ven aunque vuelvas desde la notificación)
                val ordered = remember(entries) {
                    entries.filter { it.status == JobStatus.Running } +
                        entries.filter { it.status == JobStatus.Queued } +
                        entries.filter { it.status != JobStatus.Running && it.status != JobStatus.Queued }
                            .sortedByDescending { it.at }
                }
                if (ordered.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Descargas")
                        Spacer(Modifier.weight(1f))
                        if (ordered.any { it.status != JobStatus.Running && it.status != JobStatus.Queued }) {
                            TextButton(onClick = { DlQueue.clearFinished() }) { Text("Limpiar historial") }
                        }
                    }
                    if (!queueActive && entries.any { it.status == JobStatus.Queued }) {
                        PillButton("Reanudar cola", onClick = { DownloadService.kick(ctx) }, modifier = Modifier.fillMaxWidth())
                    }
                    ordered.forEach { e -> EntryCard(e, dl) }
                }

                // Mantenimiento de yt-dlp
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        enabled = !running && !updating && !loading,
                        onClick = {
                            updating = true; updateMsg = null
                            scope.launch {
                                Ytdl.update(ctx)
                                    .onSuccess { updateMsg = "yt-dlp actualizado ($it)." }
                                    .onFailure { updateMsg = "No se pudo actualizar: " + friendly(it).lines().last() }
                                updating = false
                            }
                        },
                    ) { Text(if (updating) "Actualizando yt-dlp…" else "Actualizar yt-dlp") }
                }
                updateMsg?.let {
                    Text(
                        it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Una descarga de la lista: en cola, bajando, terminada o con error. */
@Composable
private fun EntryCard(e: DlEntry, live: com.ucd.player.download.DlState) {
    val ctx = LocalContext.current
    GlassCard {
        Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
        val kind = (if (e.audioOnly) "Solo audio" else if (e.height > 0) "${e.height}p" else "Mejor calidad")
        when (e.status) {
            JobStatus.Running -> {
                val isLive = live.status == DlStatus.Running
                Text(
                    if (isLive) live.phase else "Preparando…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ProgressPill(if (isLive) live.progress else -1f)
                val pct = if (isLive && live.progress >= 0f) "${(live.progress * 100).toInt()} %" else ""
                val line = listOf(pct, if (isLive) live.detail else "").filter { it.isNotBlank() }.joinToString(" · ")
                if (line.isNotBlank()) Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PillButton("Cancelar descarga", onClick = { Downloader.cancel() }, modifier = Modifier.fillMaxWidth(), filled = false)
            }
            JobStatus.Queued -> {
                Text("En cola · $kind", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { DlQueue.remove(e.id) }) { Text("Quitar de la cola") }
            }
            JobStatus.Done -> {
                Text(
                    "Listo · $kind\nGuardado en: ${e.message}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.uri.isNotBlank()) {
                        PillButton(
                            "Reproducir",
                            onClick = {
                                runCatching {
                                    ctx.startActivity(PlayerActivity.intent(ctx, listOf(Uri.parse(e.uri)), listOf(e.title), 0))
                                }
                            },
                        )
                    }
                    TextButton(onClick = { DlQueue.remove(e.id) }) { Text("Quitar del historial") }
                }
            }
            JobStatus.Failed, JobStatus.Cancelled -> {
                Text(
                    if (e.status == JobStatus.Cancelled) "Descarga cancelada · $kind" else "No se pudo descargar · $kind\n${e.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (e.status == JobStatus.Cancelled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton("Reintentar", onClick = {
                        DlQueue.retry(e.id)
                        DownloadService.kick(ctx)
                    })
                    TextButton(onClick = { DlQueue.remove(e.id) }) { Text("Borrar") }
                }
            }
        }
    }
}

/** Tarjeta de cristal con la superficie del tema activo. */
@Composable
private fun GlassCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(rs(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** Botón en forma de píldora: relleno con el color de acento del tema, o solo de cristal. */
@Composable
private fun PillButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, filled: Boolean = true, icon: Boolean = false,
) {
    val st = LocalUcdStyle.current
    val bg = if (filled) st.accent else Color.Transparent
    val fg = if (filled) st.onAccent else MaterialTheme.colorScheme.onSurface
    Box(
        modifier
            .height(50.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .bounceClick(CircleShape) { if (enabled) onClick() }
            .glass(CircleShape)
            .background(bg)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon) Icon(Icons.Rounded.Download, null, tint = fg)
            Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** Barra de progreso redondeada con el acento del tema. Negativo = no se sabe cuánto falta. */
@Composable
private fun ProgressPill(progress: Float) {
    val st = LocalUcdStyle.current
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "dlProgress")
    Box(
        Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
    ) {
        if (progress >= 0f) {
            Box(Modifier.fillMaxWidth(shown).fillMaxHeight().clip(CircleShape).background(st.accent))
        } else {
            LinearProgressIndicator(Modifier.fillMaxSize(), color = st.accent, trackColor = Color.Transparent)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
)

@Composable
private fun Thumbnail(url: String?) {
    if (url == null) return
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching { URL(url).openStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    bmp?.let {
        Image(
            it, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(rs(16.dp)).glassBorder(rs(16.dp)),
        )
    }
}

private fun friendly(e: Throwable): String {
    return "No se pudo analizar el enlace.\n" + Errors.explain(e.message ?: e.toString())
}

private fun formatLine(f: FormatInfo): String {
    val res = when {
        f.hasVideo && f.height > 0 -> "${f.height}p" + if (f.fps > 30) "${f.fps}" else ""
        f.hasVideo -> "video"
        else -> "audio"
    }
    val codec = if (f.hasVideo) f.vcodec.substringBefore('.') else f.acodec.substringBefore('.')
    val kind = if (f.hasVideo && f.hasAudio) " (con audio)" else ""
    return "${f.id} · ${f.ext} · $res · $codec$kind" + if (f.sizeBytes > 0) " · ${fmtBytes(f.sizeBytes)}" else ""
}

private fun fmtDuration(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(java.util.Locale.US, "%d:%02d", m, s)
}

private fun fmtBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format(java.util.Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
    else -> String.format(java.util.Locale.US, "%d KB", b / 1024)
}
