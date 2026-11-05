package com.ucd.player.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.ucd.player.data.UcdPrefs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Descarga real con yt-dlp: baja a la caché, ordena el índice (faststart) y copia a la carpeta de destino. */
object Downloader {
    class Request(
        val url: String,
        val title: String,
        /** 0 = la mejor calidad. */
        val height: Int,
        val audioOnly: Boolean,
        /** 2 si hay que bajar video y audio por separado y unirlos; 1 si es un solo archivo. */
        val parts: Int,
    )

    @Volatile private var processId: String? = null
    @Volatile var cancelRequested = false
        private set

    private val PCT = Regex("""\[download\]\s+(\d+(?:\.\d+)?)%""")
    private val SPEED = Regex("""at\s+(\S+/s)""")
    private val ETA = Regex("""ETA\s+(\S+)""")
    private val MEDIA_EXT = setOf("mp4", "m4v", "mov", "mkv", "webm", "flv", "avi", "3gp", "m4a", "mp3", "opus", "ogg", "aac", "flac", "wav")

    /** Prefiere MP4/M4A (se reproducen en cualquier teléfono) y cae a lo que haya. */
    fun selector(height: Int, audioOnly: Boolean): String {
        if (audioOnly) return "bestaudio[ext=m4a]/bestaudio/best"
        val h = if (height > 0) "[height<=$height]" else ""
        return "bv*$h[ext=mp4]+ba[ext=m4a]/b$h[ext=mp4]/bv*$h+ba/b$h/bv*+ba/b"
    }

    fun cancel() {
        cancelRequested = true
        processId?.let { id -> runCatching { YoutubeDL.getInstance().destroyProcessById(id) } }
    }

    /** Bloquea hasta terminar. Lanza excepción si falla o se cancela. Devuelve dónde quedó guardado. */
    suspend fun download(ctx: Context, req: Request): String = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        cancelRequested = false
        Downloads.set(DlState(DlStatus.Running, req.title, -1f, "Preparando yt-dlp…"))
        Ytdl.ensureInit(app)
        if (cancelRequested) throw CancellationException("Cancelado")

        val tmp = File(app.cacheDir, "ytdl/" + System.currentTimeMillis()).also { it.mkdirs() }
        val id = "ucd-" + System.currentTimeMillis()
        processId = id
        try {
            val r = YoutubeDLRequest(req.url)
            r.addOption("--no-playlist")
            r.addOption("--newline")
            r.addOption("--no-mtime")
            r.addOption("--windows-filenames")
            r.addOption("-N", "4")
            r.addOption("-f", selector(req.height, req.audioOnly))
            r.addOption("-o", tmp.absolutePath + "/%(title).100B.%(ext)s")
            if (req.audioOnly) {
                r.addOption("-x")
                r.addOption("--audio-format", "m4a")
            } else {
                r.addOption("--merge-output-format", "mp4")
                // Al unir video y audio, que ffmpeg deje el índice al principio
                r.addOption("--postprocessor-args", "Merger+ffmpeg:-movflags +faststart")
            }

            val parts = req.parts.coerceAtLeast(1)
            var part = 0
            Downloads.set(DlState(DlStatus.Running, req.title, 0f, "Descargando…"))
            YoutubeDL.getInstance().execute(r, id) { _, _, line ->
                val cur = Downloads.state.value
                when {
                    line.contains("[download] Destination:") -> part++
                    line.startsWith("[Merger]") ->
                        Downloads.set(cur.copy(progress = -1f, phase = "Uniendo video y audio…", detail = ""))
                    line.startsWith("[VideoRemuxer]") ->
                        Downloads.set(cur.copy(progress = -1f, phase = "Convirtiendo a MP4…", detail = ""))
                    line.startsWith("[ExtractAudio]") ->
                        Downloads.set(cur.copy(progress = -1f, phase = "Extrayendo audio…", detail = ""))
                    line.startsWith("[Fixup") ->
                        Downloads.set(cur.copy(progress = -1f, phase = "Ajustando el archivo…", detail = ""))
                    else -> {
                        val m = PCT.find(line)
                        if (m != null) {
                            val pct = m.groupValues[1].toFloatOrNull() ?: 0f
                            val idx = (part - 1).coerceIn(0, parts - 1)
                            val overall = ((idx + pct / 100f) / parts).coerceIn(0f, 0.99f)
                            val speed = SPEED.find(line)?.groupValues?.get(1)
                            val eta = ETA.find(line)?.groupValues?.get(1)
                            val detail = listOfNotNull(speed, eta?.let { "quedan $it" }).joinToString(" · ")
                            Downloads.set(
                                cur.copy(
                                    progress = overall,
                                    phase = if (parts > 1) "Descargando (${idx + 1}/$parts)…" else "Descargando…",
                                    detail = detail,
                                ),
                            )
                        }
                    }
                }
            }

            if (cancelRequested) throw CancellationException("Cancelado")

            val out = tmp.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in MEDIA_EXT }
                .maxByOrNull { it.length() }
                ?: throw IOException("yt-dlp terminó, pero no se encontró el archivo descargado")

            // Índice al principio (faststart): solo aplica a MP4/M4A que lo tengan al final
            val ext = out.extension.lowercase()
            if (ext == "mp4" || ext == "m4v" || ext == "mov" || ext == "m4a") {
                Downloads.set(Downloads.state.value.copy(progress = -1f, phase = "Preparando índice (faststart)…", detail = ""))
                Faststart.ensure(out)
            }

            Downloads.set(Downloads.state.value.copy(progress = -1f, phase = "Guardando en la carpeta…", detail = ""))
            val where = Saver.save(app, out, UcdPrefs(app).downloadTree)
            Downloads.set(DlState(DlStatus.Done, req.title, 1f, "", "", where))
            where
        } finally {
            processId = null
            tmp.deleteRecursively()
        }
    }
}
