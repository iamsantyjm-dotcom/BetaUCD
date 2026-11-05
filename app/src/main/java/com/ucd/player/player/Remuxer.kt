package com.ucd.player.player

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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

    /** Una sola conversión a la vez (así dos pedidos del mismo video nunca escriben el mismo archivo). */
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = HashMap<String, Deferred<File?>>()

    /** Marca la copia como "usada ahora": al llenarse la caché se borra primero lo que hace más tiempo que no se ve. */
    fun markUsed(ctx: Context, uri: String) {
        fileFor(ctx, uri).takeIf { it.exists() }?.setLastModified(System.currentTimeMillis())
    }

    /**
     * ¿Es un MP4 "fragmentado" (de los que no se pueden recorrer)? Solo lee las cabeceras de las
     * cajas del archivo, sin leer el video, así que es instantáneo. Se usa para no convertir
     * por adelantado videos que no lo necesitan.
     */
    fun isFragmentedMp4(ctx: Context, uri: String): Boolean = runCatching {
        ctx.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { pfd ->
            FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                val size = ch.size()
                var pos = 0L
                var n = 0
                var fragmented = false
                val hdr = ByteBuffer.allocate(16)
                while (pos + 8 <= size && n < 24) {
                    hdr.clear()
                    val read = ch.read(hdr, pos)
                    if (read < 8) break
                    hdr.flip()
                    var boxSize = hdr.int.toLong() and 0xFFFFFFFFL
                    val type = ByteArray(4).also { hdr.get(it) }.toString(Charsets.ISO_8859_1)
                    if (n == 0 && type != "ftyp") break          // no es un MP4
                    if (type == "moof") { fragmented = true; break }
                    if (boxSize == 1L) { if (read < 16) break; boxSize = hdr.long }
                    if (boxSize == 0L) boxSize = size - pos
                    if (boxSize < 8) break
                    pos += boxSize
                    n++
                }
                fragmented
            }
        } ?: false
    }.getOrDefault(false)

    /**
     * Convierte en segundo plano sin cancelarse si cambia el video que se está viendo
     * (el trabajo ya hecho no se pierde). Devuelve el resultado cuando termina.
     */
    fun prefetch(ctx: Context, uri: String): Deferred<File?> {
        val app = ctx.applicationContext
        return synchronized(inFlight) {
            inFlight[uri]?.takeIf { it.isActive } ?: scope.async { convert(app, uri) }.also { d ->
                inFlight[uri] = d
                d.invokeOnCompletion { synchronized(inFlight) { if (inFlight[uri] === d) inFlight.remove(uri) } }
            }
        }
    }

    /** Devuelve el MP4 convertido, o null si no se pudo. Si ya se está convirtiendo por adelantado, espera ese resultado. */
    suspend fun remux(ctx: Context, uri: String): File? {
        cached(ctx, uri)?.let { return it }
        val running = synchronized(inFlight) { inFlight[uri] }
        return if (running != null) running.await() else convert(ctx, uri)
    }

    private suspend fun convert(ctx: Context, uri: String): File? = withContext(Dispatchers.IO) { lock.withLock {
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
    } }
}
