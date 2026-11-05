package com.ucd.player.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class JobStatus { Queued, Running, Done, Failed, Cancelled }

/** Una descarga de la cola o del historial. */
data class DlEntry(
    val id: Long,
    val url: String,
    val title: String,
    /** 0 = la mejor calidad. */
    val height: Int,
    val audioOnly: Boolean,
    val parts: Int,
    /** Carpeta elegida al añadirla a la cola (null = predeterminada). */
    val tree: String?,
    val status: JobStatus,
    /** Done: dónde quedó guardado. Failed: el motivo. */
    val message: String = "",
    /** URI del archivo guardado (solo Done), para el botón Reproducir. */
    val uri: String = "",
    /** Cuándo terminó (milisegundos); 0 mientras está en cola o bajando. */
    val at: Long = 0L,
)

/**
 * Cola de descargas + historial. Se guarda en un archivo para que el historial sobreviva al cierre
 * de la app. El progreso en vivo de la descarga que está corriendo sigue en [Downloads].
 */
object DlQueue {
    private const val MAX_FINISHED = 100

    private val lock = Any()
    private var file: File? = null
    private var lastId = 0L

    private val _items = MutableStateFlow<List<DlEntry>>(emptyList())
    val items: StateFlow<List<DlEntry>> = _items.asStateFlow()

    /** true mientras el servicio está procesando la cola. */
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()
    fun setActive(v: Boolean) { _active.value = v }

    fun init(ctx: Context) = synchronized(lock) {
        if (file != null) return@synchronized
        val f = File(ctx.applicationContext.filesDir, "dl_queue.json")
        file = f
        _items.value = read(f).map {
            // Si la app se cerró en medio de una descarga, queda como fallida (se puede reintentar)
            if (it.status == JobStatus.Running) it.copy(status = JobStatus.Failed, message = "Se interrumpió la descarga.", at = System.currentTimeMillis())
            else it
        }
        persist()
    }

    private fun newId(): Long { lastId = maxOf(System.currentTimeMillis(), lastId + 1); return lastId }

    private fun update(f: (List<DlEntry>) -> List<DlEntry>) = synchronized(lock) {
        var next = f(_items.value)
        // Del historial se guardan solo las últimas; la cola y lo que está bajando nunca se recortan
        val finished = next.filter { it.status != JobStatus.Queued && it.status != JobStatus.Running }
        if (finished.size > MAX_FINISHED) {
            val drop = finished.sortedBy { it.at }.take(finished.size - MAX_FINISHED).map { it.id }.toSet()
            next = next.filter { it.id !in drop }
        }
        _items.value = next
        persist()
    }

    /** Añade a la cola. Devuelve false si ya hay una igual esperando o bajando. */
    fun add(url: String, title: String, height: Int, audioOnly: Boolean, parts: Int, tree: String?): Boolean {
        synchronized(lock) {
            val dup = _items.value.any {
                (it.status == JobStatus.Queued || it.status == JobStatus.Running) &&
                    it.url == url && it.height == height && it.audioOnly == audioOnly
            }
            if (dup) return false
        }
        update { it + DlEntry(newId(), url, title, height, audioOnly, parts, tree, JobStatus.Queued) }
        return true
    }

    fun nextQueued(): DlEntry? = _items.value.firstOrNull { it.status == JobStatus.Queued }

    fun markRunning(id: Long) = update { l -> l.map { if (it.id == id) it.copy(status = JobStatus.Running, message = "") else it } }

    fun finish(id: Long, status: JobStatus, message: String = "", uri: String = "") = update { l ->
        l.map { if (it.id == id) it.copy(status = status, message = message, uri = uri, at = System.currentTimeMillis()) else it }
    }

    /** Quita una descarga de la lista (no borra el archivo ya descargado). */
    fun remove(id: Long) = update { l -> l.filter { !(it.id == id && it.status != JobStatus.Running) } }

    fun clearFinished() = update { l -> l.filter { it.status == JobStatus.Queued || it.status == JobStatus.Running } }

    /** Vuelve a poner una descarga fallida o cancelada al final de la cola. */
    fun retry(id: Long) = update { l ->
        val e = l.firstOrNull { it.id == id && (it.status == JobStatus.Failed || it.status == JobStatus.Cancelled) }
            ?: return@update l
        l.filter { it.id != id } + e.copy(id = newId(), status = JobStatus.Queued, message = "", uri = "", at = 0L)
    }

    private fun persist() {
        val f = file ?: return
        runCatching {
            val arr = JSONArray()
            _items.value.forEach { e ->
                arr.put(JSONObject().apply {
                    put("id", e.id); put("url", e.url); put("title", e.title); put("height", e.height)
                    put("audio", e.audioOnly); put("parts", e.parts); put("tree", e.tree ?: JSONObject.NULL)
                    put("status", e.status.name); put("message", e.message); put("uri", e.uri); put("at", e.at)
                })
            }
            f.writeText(arr.toString())
        }
    }

    private fun read(f: File): List<DlEntry> = runCatching {
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val st = runCatching { JobStatus.valueOf(o.getString("status")) }.getOrNull() ?: return@mapNotNull null
            DlEntry(
                id = o.getLong("id"), url = o.getString("url"), title = o.optString("title", "Video"),
                height = o.optInt("height", 0), audioOnly = o.optBoolean("audio", false), parts = o.optInt("parts", 1),
                tree = if (o.isNull("tree")) null else o.getString("tree"),
                status = st, message = o.optString("message", ""), uri = o.optString("uri", ""), at = o.optLong("at", 0L),
            )
        }
    }.getOrDefault(emptyList())
}
