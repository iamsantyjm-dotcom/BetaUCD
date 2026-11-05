package com.ucd.player.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Algunos MP4 (los "fragmentados", típicos de grabadores y descargas) no traen índice,
 * y el reproductor no puede saltar dentro de ellos. Aquí se copian, SIN recomprimir
 * (rápido y sin pérdida de calidad), a un MP4 normal guardado en la caché de la app.
 */
object Remuxer {
    private const val MAX_CACHE_BYTES = 3L * 1024 * 1024 * 1024

    private fun dir(ctx: Context) = File(ctx.cacheDir, "remux").also { it.mkdirs() }

    private fun fileFor(ctx: Context, uri: String) =
        File(dir(ctx), "v${abs(uri.hashCode())}.mp4")

    /** Copia ya convertida (si existe). */
    fun cached(ctx: Context, uri: String): File? =
        fileFor(ctx, uri).takeIf { it.exists() && it.length() > 0 }

    private fun trim(ctx: Context) {
        val files = dir(ctx).listFiles()?.sortedBy { it.lastModified() }?.toMutableList() ?: return
        var total = files.sumOf { it.length() }
        while (total > MAX_CACHE_BYTES && files.isNotEmpty()) {
            val f = files.removeAt(0)
            total -= f.length()
            f.delete()
        }
    }

    /** Devuelve el MP4 convertido, o null si no se pudo. */
    suspend fun remux(ctx: Context, uri: String): File? = withContext(Dispatchers.IO) {
        cached(ctx, uri)?.let { return@withContext it }
        trim(ctx)
        val out = fileFor(ctx, uri)
        // Prioridad baja: la conversión no debe quitarle fuerza a la reproducción
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        val tmp = File(out.parentFile, out.name + ".tmp")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(ctx, Uri.parse(uri), null)
            val m = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val map = HashMap<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    if (mime.startsWith("video/") && f.containsKey(MediaFormat.KEY_ROTATION)) {
                        m.setOrientationHint(f.getInteger(MediaFormat.KEY_ROTATION))
                    }
                    map[i] = m.addTrack(f)
                    extractor.selectTrack(i)
                }
            }
            if (map.isEmpty()) return@withContext null
            m.start()
            started = true
            val buffer = ByteBuffer.allocate(8 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var count = 0
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val track = map[extractor.sampleTrackIndex]
                if (track != null) {
                    info.set(0, size, extractor.sampleTime.coerceAtLeast(0L), extractor.sampleFlags)
                    m.writeSampleData(track, buffer, info)
                }
                extractor.advance()
                if (++count % 200 == 0) ensureActive()
            }
            m.stop()
            started = false
            if (!tmp.renameTo(out)) { tmp.delete(); return@withContext null }
            out
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            null
        } finally {
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_DEFAULT) }
            if (tmp.exists()) tmp.delete()
        }
    }
}
