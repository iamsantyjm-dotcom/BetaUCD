package com.ucd.player.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ucd.player.download.DlStatus
import com.ucd.player.download.DownloadService
import com.ucd.player.download.Downloader
import com.ucd.player.download.Downloads
import com.ucd.player.download.FormatInfo
import com.ucd.player.download.MediaInfo
import com.ucd.player.download.Saver
import com.ucd.player.download.Ytdl
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.theme.UcdTheme
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
    val prefs = remember { UcdPrefs(ctx) }
    var tree by remember { mutableStateOf(prefs.downloadTree) }
    val dl by Downloads.state.collectAsState()
    val running = dl.status == DlStatus.Running

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Descargar video") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = url, onValueChange = { url = it },
                label = { Text("Enlace del video") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let { url = it.trim() }
                    }) { Icon(Icons.Rounded.ContentPaste, "Pegar") }
                },
            )
            Button(onClick = { analyze() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Analizando…" else "Analizar enlace")
            }
            if (loading) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                Text(
                    "La primera vez puede tardar un poco mientras se prepara yt-dlp.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            info?.let { m ->
                Thumbnail(m.thumbnail)
                Text(m.title, style = MaterialTheme.typography.titleMedium)
                val meta = listOfNotNull(
                    m.uploader.takeIf { it.isNotBlank() },
                    m.durationSec.takeIf { it > 0 }?.let { fmtDuration(it) },
                ).joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Calidad", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !audioOnly && quality == null, onClick = { audioOnly = false; quality = null }, label = { Text("Mejor") })
                    m.heights.forEach { h ->
                        FilterChip(selected = !audioOnly && quality == h, onClick = { audioOnly = false; quality = h }, label = { Text("${h}p") })
                    }
                    FilterChip(selected = audioOnly, onClick = { audioOnly = true }, label = { Text("Solo audio") })
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


                Text("Guardar en", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(Saver.label(tree), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickFolder.launch(null) }, enabled = !running) { Text("Elegir carpeta") }
                    if (tree != null) {
                        TextButton(onClick = { prefs.downloadTree = null; tree = null }, enabled = !running) { Text("Usar predeterminada") }
                    }
                }

                Button(
                    onClick = {
                        val parts = if (!audioOnly && m.formats.any { it.hasVideo && !it.hasAudio }) 2 else 1
                        DownloadService.start(ctx, m.pageUrl, m.title, quality ?: 0, audioOnly, parts)
                    },
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (audioOnly) "Descargar audio" else "Descargar") }
            }

            // Estado de la descarga (se ve aunque vuelvas desde la notificación)
            when (dl.status) {
                DlStatus.Running -> {
                    Text(dl.title, style = MaterialTheme.typography.titleSmall)
                    Text(dl.phase)
                    if (dl.progress >= 0f) LinearProgressIndicator(progress = { dl.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    val pct = if (dl.progress >= 0f) "${(dl.progress * 100).toInt()} %" else ""
                    val line = listOf(pct, dl.detail).filter { it.isNotBlank() }.joinToString(" · ")
                    if (line.isNotBlank()) Text(line, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { Downloader.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar descarga") }
                }
                DlStatus.Done -> Text("Listo. Guardado en: ${dl.message}", color = MaterialTheme.colorScheme.primary)
                DlStatus.Failed -> Text("No se pudo descargar.\n${dl.message}", color = MaterialTheme.colorScheme.error)
                DlStatus.Cancelled -> Text("Descarga cancelada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                DlStatus.Idle -> {}
            }
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
        )
    }
}

private fun friendly(e: Throwable): String {
    val msg = (e.message ?: e.toString()).lines().lastOrNull { it.isNotBlank() } ?: "Error desconocido"
    return "No se pudo analizar el enlace.\n" + msg.take(300)
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
