package com.ucd.player.data

import android.content.Context

/** Cómo se muestran las carpetas y los videos. */
enum class ViewMode(val label: String) {
    Grid("Cuadrícula"),
    Compact("Cuadrícula compacta"),
    Rows("Lista"),
}

/** Criterio de orden. */
enum class SortKey(val label: String) {
    Name("Nombre"),
    Date("Fecha"),
    Duration("Duración"),
    Size("Tamaño"),
}

/** Filtro de la pantalla de videos (chips bajo el título). */
enum class VideoFilter(val label: String) {
    Folders("Carpetas"),
    All("Todos"),
    Recent("Recientes"),
    Started("Empezados"),
}

/** Ajustes sencillos guardados en el dispositivo + posición de reproducción por video. */
class UcdPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ucd_prefs", Context.MODE_PRIVATE)
    private val pos = ctx.getSharedPreferences("ucd_positions", Context.MODE_PRIVATE)

    var viewMode: ViewMode
        get() = runCatching { ViewMode.valueOf(sp.getString("view", "") ?: "") }.getOrDefault(ViewMode.Grid)
        set(v) { sp.edit().putString("view", v.name).apply() }

    var sortKey: SortKey
        get() = runCatching { SortKey.valueOf(sp.getString("sort", "") ?: "") }.getOrDefault(SortKey.Name)
        set(v) { sp.edit().putString("sort", v.name).apply() }

    var ascending: Boolean
        get() = sp.getBoolean("asc", true)
        set(v) { sp.edit().putBoolean("asc", v).apply() }

    /** Formato de pantalla del reproductor (Fit / Fill / Stretch), se recuerda entre videos. */
    var videoScale: String
        get() = sp.getString("scale", "Fit") ?: "Fit"
        set(v) { sp.edit().putString("scale", v).apply() }

    // --- Carpetas ocultas / con la miniatura oculta (por nombre de carpeta) ---
    var hiddenFolders: Set<String>
        get() = (sp.getStringSet("hidden_folders", null) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("hidden_folders", HashSet(v)).apply() }

    var hiddenThumbs: Set<String>
        get() = (sp.getStringSet("hidden_thumbs", null) ?: emptySet()).toSet()
        set(v) { sp.edit().putStringSet("hidden_thumbs", HashSet(v)).apply() }

    /** Filtro elegido en la pantalla de videos (Carpetas, Todos, Recientes, Empezados). */
    var videoFilter: VideoFilter
        get() = runCatching { VideoFilter.valueOf(sp.getString("video_filter", "") ?: "") }.getOrDefault(VideoFilter.Folders)
        set(v) { sp.edit().putString("video_filter", v.name).apply() }

    /** Tema visual elegido (id de UcdStyle). Por defecto: Medianoche. */
    var themeId: String
        get() = sp.getString("theme_id3", null) ?: "midnight"
        set(v) { sp.edit().putString("theme_id3", v).apply() }

    // --- Biblia ---
    var bibleRed: Boolean   // palabras de Cristo en rojo
        get() = sp.getBoolean("bible_red", true)
        set(v) { sp.edit().putBoolean("bible_red", v).apply() }

    var bibleTextColor: Int // color de la letra al proyectar (posición en la lista de colores)
        get() = sp.getInt("bible_text_color", 0)
        set(v) { sp.edit().putInt("bible_text_color", v).apply() }

    var bibleFont: Int      // tamaño máximo de letra al proyectar (sp)
        get() = sp.getInt("bible_font", 110)
        set(v) { sp.edit().putInt("bible_font", v).apply() }

    var bibleBook: Int      // último libro abierto (-1 = ninguno)
        get() = sp.getInt("bible_book", -1)
        set(v) { sp.edit().putInt("bible_book", v).apply() }

    var bibleChapter: Int   // último capítulo abierto (0 = ninguno)
        get() = sp.getInt("bible_chapter", 0)
        set(v) { sp.edit().putInt("bible_chapter", v).apply() }

    // --- Fondo de la proyección ---
    var bibleBg: Boolean        // imagen de fondo encendida
        get() = sp.getBoolean("bible_bg", true)
        set(v) { sp.edit().putBoolean("bible_bg", v).apply() }

    /** Precarga en segundo plano: miniaturas de carpetas, videos y cola, y el siguiente fondo de la Biblia. */
    var preload: Boolean
        get() = sp.getBoolean("preload", false)
        set(v) { sp.edit().putBoolean("preload", v).apply() }

    /** En la pantalla externa, el versículo y el fondo cambian al instante (sin fundidos), para probar si así hay menos cortes. */
    var bibleInstant: Boolean
        get() = sp.getBoolean("bible_instant", false)
        set(v) { sp.edit().putBoolean("bible_instant", v).apply() }

    var bibleMotion: Boolean    // movimiento suave del fondo
        get() = sp.getBoolean("bible_motion", true)
        set(v) { sp.edit().putBoolean("bible_motion", v).apply() }

    var bibleBgMode: Int        // 0 = por capítulo, 1/2/3 = cada 15/30/60 s, 4 = fija
        get() = sp.getInt("bible_bg_mode", 0)
        set(v) { sp.edit().putInt("bible_bg_mode", v).apply() }

    var bibleDim: Float         // oscuridad de la capa sobre la imagen
        get() = sp.getFloat("bible_dim", 0.65f)
        set(v) { sp.edit().putFloat("bible_dim", v).apply() }

    var bibleBlur: Int          // desenfoque extra del fondo: 0 = normal ... 4 = máximo
        get() = sp.getInt("bible_blur", 0).coerceIn(0, 4)
        set(v) { sp.edit().putInt("bible_blur", v.coerceIn(0, 4)).apply() }

    // --- Ajustes ---
    /** Al abrir un video, continuar donde se dejó (apagado = empezar desde el principio). */
    var resumePlayback: Boolean
        get() = sp.getBoolean("resume_playback", true)
        set(v) { sp.edit().putBoolean("resume_playback", v).apply() }

    /** Al salir del reproductor con "atrás", el video sigue sonando en segundo plano. */
    var backgroundPlay: Boolean
        get() = sp.getBoolean("background_play", true)
        set(v) { sp.edit().putBoolean("background_play", v).apply() }

    /** Mantener la pantalla encendida mientras se está en el reproductor. */
    var keepScreenOn: Boolean
        get() = sp.getBoolean("keep_screen_on", true)
        set(v) { sp.edit().putBoolean("keep_screen_on", v).apply() }

    /** Modo ligero: con una pantalla externa conectada, la interfaz del celular quita desenfoques y animaciones. */
    var lightModeExternal: Boolean
        get() = sp.getBoolean("light_mode_external", true)
        set(v) { sp.edit().putBoolean("light_mode_external", v).apply(); Perf.lightMode = v }

    /** Con una pantalla externa conectada, el video se ve en ella y el celular queda con los controles. */
    var videoOnExternal: Boolean
        get() = sp.getBoolean("video_on_external", true)
        set(v) { sp.edit().putBoolean("video_on_external", v).apply() }

    // --- Fundido entre videos ---
    /** Duración del fundido al terminar y al empezar un video, en milisegundos (0 = apagado). */
    var fadeMs: Int
        get() = sp.getInt("fade_ms", 2000).coerceIn(0, 5000)
        set(v) { sp.edit().putInt("fade_ms", v.coerceIn(0, 5000)).apply() }

    /** Además del volumen, la imagen se funde a negro. */
    var fadePicture: Boolean
        get() = sp.getBoolean("fade_picture", true)
        set(v) { sp.edit().putBoolean("fade_picture", v).apply() }

    /** Al terminar un video, pasar solo al siguiente (apagado = se detiene al terminar cada uno). */
    var autoNext: Boolean
        get() = sp.getBoolean("auto_next", true)
        set(v) { sp.edit().putBoolean("auto_next", v).apply() }

    /** Al terminar la lista, volver a empezar desde el primer video. */
    var repeatAll: Boolean
        get() = sp.getBoolean("repeat_all", false)
        set(v) { sp.edit().putBoolean("repeat_all", v).apply() }

    /** Gestos de deslizar en el reproductor (avanzar/retroceder y volumen). */
    var swipeGestures: Boolean
        get() = sp.getBoolean("swipe_gestures", true)
        set(v) { sp.edit().putBoolean("swipe_gestures", v).apply() }

    /** Tiempo que tardan en ocultarse los controles del reproductor (ms). */
    var controlsHideMs: Int
        get() = sp.getInt("controls_hide_ms", 1500)
        set(v) { sp.edit().putInt("controls_hide_ms", v).apply() }

    // --- Biblia: cómo se ve el versículo ---
    var bibleRefMode: Int       // cita (libro y versículo): 0 = debajo, 1 = arriba, 2 = oculta
        get() = sp.getInt("bible_ref_mode", 0).coerceIn(0, 2)
        set(v) { sp.edit().putInt("bible_ref_mode", v).apply() }

    var bibleRefSize: Int       // tamaño de la cita (sp)
        get() = sp.getInt("bible_ref_size", 22)
        set(v) { sp.edit().putInt("bible_ref_size", v).apply() }

    var bibleAlign: Int         // 0 = centrado, 1 = izquierda, 2 = justificado
        get() = sp.getInt("bible_align", 0).coerceIn(0, 2)
        set(v) { sp.edit().putInt("bible_align", v).apply() }

    var bibleLineSpacing: Int   // 0 = compacto, 1 = normal, 2 = amplio
        get() = sp.getInt("bible_line_spacing", 1).coerceIn(0, 2)
        set(v) { sp.edit().putInt("bible_line_spacing", v).apply() }

    // --- Continuar donde lo dejaste ---
    fun position(uri: String): Long = pos.getLong(uri, 0L)
    fun savePosition(uri: String, ms: Long) { pos.edit().putLong(uri, ms).apply() }
    fun clearPosition(uri: String) { pos.edit().remove(uri).apply() }
    fun clearAllPositions() { pos.edit().clear().apply() }
}
