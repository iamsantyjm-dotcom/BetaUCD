package com.ucd.player.download

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import com.ucd.player.data.UcdPrefs
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Un formato concreto que ofrece el sitio (lo que lista yt-dlp con -F). */
data class FormatInfo(
    val id: String,
    val ext: String,
    val height: Int,
    val fps: Int,
    val vcodec: String,
    val acodec: String,
    val sizeBytes: Long,
) {
    val hasVideo get() = vcodec.isNotBlank() && vcodec != "none"
    val hasAudio get() = acodec.isNotBlank() && acodec != "none"
}

/** Detalles del video a descargar. */
data class MediaInfo(
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnail: String?,
    val pageUrl: String,
    val formats: List<FormatInfo>,
) {
    /** Alturas de video disponibles, de mayor a menor. */
    val heights: List<Int> get() =
        formats.filter { it.hasVideo && it.height > 0 }.map { it.height }.distinct().sortedDescending()

    /** Tamaño estimado (bytes) para una altura dada (null = mejor). 0 si el sitio no lo informa. */
    fun estimateSize(height: Int?): Long {
        val vids = formats.filter { it.hasVideo && (height == null || it.height == height) }
        val video = vids.filter { it.height == (height ?: vids.maxOfOrNull { v -> v.height }) }
            .maxOfOrNull { it.sizeBytes } ?: 0L
        val audio = formats.filter { it.hasAudio && !it.hasVideo }.maxOfOrNull { it.sizeBytes } ?: 0L
        return video + audio
    }
}

/** Puente con yt-dlp (librería youtubedl-android). */
object Ytdl {
    private val mutex = Mutex()
    @Volatile private var ready = false

    /** La primera vez descomprime Python/yt-dlp/ffmpeg (tarda unos segundos), por eso va fuera del hilo principal. */
    suspend fun ensureInit(ctx: Context) = withContext(Dispatchers.IO) {
        if (ready) return@withContext
        mutex.withLock {
            if (!ready) {
                val app = ctx.applicationContext
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)
                ready = true
            }
        }
    }

    /** Baja la versión más reciente de yt-dlp (canal nightly): YouTube cambia seguido y lo viejo deja de funcionar. */
    suspend fun update(ctx: Context): Result<String> = withContext(Dispatchers.IO) {
        try {
            ensureInit(ctx)
            val st = YoutubeDL.getInstance().updateYoutubeDL(ctx.applicationContext, UpdateChannel.NIGHTLY)
            UcdPrefs(ctx.applicationContext).ytdlUpdatedAt = System.currentTimeMillis()
            Result.success(st?.toString() ?: "listo")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private const val AUTO_UPDATE_DAYS = 3L

    /** Actualiza yt-dlp solo si hace más de unos días que no se hace (en silencio; si falla, se sigue igual). */
    suspend fun autoUpdateIfStale(ctx: Context) {
        if (Downloads.isRunning()) return
        val last = UcdPrefs(ctx.applicationContext).ytdlUpdatedAt
        if (System.currentTimeMillis() - last < AUTO_UPDATE_DAYS * 24L * 3600L * 1000L) return
        update(ctx)
    }

    /** Pide a yt-dlp los datos del enlace SIN descargar nada. */
    suspend fun analyze(ctx: Context, url: String): Result<MediaInfo> = withContext(Dispatchers.IO) {
        try {
            ensureInit(ctx)
            autoUpdateIfStale(ctx)
            val req = YoutubeDLRequest(url)
            req.addOption("-J")
            req.addOption("--no-playlist")
            req.addOption("--no-warnings")
            val out = YoutubeDL.getInstance().execute(req).out
            Result.success(parse(out, url))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun parse(raw: String, url: String): MediaInfo {
        val start = raw.indexOf('{')
        require(start >= 0) { "yt-dlp no devolvió datos" }
        val j = JSONObject(raw.substring(start))
        val arr = j.optJSONArray("formats")
        val formats = ArrayList<FormatInfo>()
        if (arr != null) for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            val ext = f.optString("ext")
            if (ext == "mhtml") continue  // storyboards, no son video
            val size = if (f.has("filesize") && !f.isNull("filesize")) f.optLong("filesize")
            else f.optLong("filesize_approx", 0L)
            formats += FormatInfo(
                id = f.optString("format_id"),
                ext = ext,
                height = f.optInt("height", 0),
                fps = f.optDouble("fps", 0.0).toInt(),
                vcodec = f.optString("vcodec", "none"),
                acodec = f.optString("acodec", "none"),
                sizeBytes = size,
            )
        }
        return MediaInfo(
            title = j.optString("title", "Video"),
            uploader = j.optString("uploader", j.optString("channel", "")),
            durationSec = j.optDouble("duration", 0.0).toLong(),
            thumbnail = j.optString("thumbnail").takeIf { it.isNotBlank() && it != "null" },
            pageUrl = j.optString("webpage_url", url),
            formats = formats,
        )
    }
}
