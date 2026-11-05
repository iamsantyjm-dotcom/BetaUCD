package com.ucd.player.bible

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ucd.player.data.Bible
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.theme.UcdTheme

/**
 * Pantalla externa (TV por Smart View, cable, etc.): muestra el versículo elegido en el TV
 * mientras el celular sigue libre para buscar otros. Si no hay pantalla externa, [show]
 * devuelve false y la app se comporta como siempre.
 */
object ExternalDisplay {
    /** Versículo que está en el TV (posición global); -1 = nada proyectándose. */
    var flat by mutableIntStateOf(-1)
        private set

    /** TV en negro (el versículo sigue elegido). */
    var blackout by mutableStateOf(false)

    /** Interruptor del usuario: apagado = la Biblia se abre a pantalla completa en el celular como siempre. */
    var enabled by mutableStateOf(true)
        private set

    /** Hay una pantalla externa conectada ahora mismo. */
    var available by mutableStateOf(false)
        private set

    /** Sube cuando cambia algún ajuste (letra, fondo, color...) para que el TV se redibuje. */
    var tick by mutableIntStateOf(0)
        private set

    /** Zoom y desplazamiento del versículo en el TV (el desplazamiento es una fracción del tamaño de la pantalla). */
    var zoom by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    /** Cambia el zoom y el desplazamiento del versículo en el TV. */
    fun setZoom(z: Float, x: Float, y: Float) {
        zoom = z
        panX = x
        panY = y
    }

    /** Vuelve al tamaño normal y centra el versículo. */
    fun resetZoom() = setZoom(1f, 0f, 0f)

    private var loaded = false
    private var resumeFlat = -1
    private var presentation: VersePresentation? = null
    private var observed: ComponentActivity? = null
    private var listener: DisplayManager.DisplayListener? = null
    private var watchedManager: DisplayManager? = null

    private fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        enabled = ctx.applicationContext.getSharedPreferences("ucd_prefs", Context.MODE_PRIVATE)
            .getBoolean("external_enabled", true)
    }

    fun isEnabled(ctx: Context): Boolean { load(ctx); return enabled }

    fun setEnabled(ctx: Context, on: Boolean) {
        load(ctx)
        enabled = on
        ctx.applicationContext.getSharedPreferences("ucd_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("external_enabled", on).apply()
        if (!on) hide()
    }

    fun touch() { tick++ }

    /**
     * Vigila si se conecta o desconecta una pantalla externa. Si el TV se había cortado
     * (por ejemplo al bloquear el celular) y vuelve a conectarse, retoma el último versículo.
     */
    fun startWatching(ctx: Context) {
        load(ctx)
        stopWatching()
        val act = ctx.findComponentActivity()
        val dm = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        fun refresh() { available = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty() }
        refresh()
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {
                refresh()
                if (enabled && resumeFlat >= 0 && presentation == null && act != null && !act.isFinishing) {
                    show(act, resumeFlat)
                }
            }
            override fun onDisplayRemoved(displayId: Int) = refresh()
            override fun onDisplayChanged(displayId: Int) {}
        }
        dm.registerDisplayListener(l, Handler(Looper.getMainLooper()))
        listener = l
        watchedManager = dm
    }

    fun stopWatching() {
        listener?.let { watchedManager?.unregisterDisplayListener(it) }
        listener = null
        watchedManager = null
    }

    private fun firstDisplay(act: ComponentActivity): Display? =
        (act.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
            .getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()

    /** Crea (si hace falta) la ventana que ocupa el TV. Devuelve false si no se pudo. */
    private fun ensurePresentation(act: ComponentActivity, display: Display): Boolean {
        val cur = presentation
        if (cur != null && cur.isShowing && cur.display.displayId == display.displayId) return true
        cur?.dismiss()
        return try {
            val p = VersePresentation(act, display)
            p.setOnDismissListener {
                if (presentation === p) {
                    // Si se cortó solo (TV desconectado), se recuerda el versículo para retomarlo
                    if (flat >= 0) resumeFlat = flat
                    presentation = null
                    flat = -1
                    blackout = false
                }
            }
            p.show()
            presentation = p
            if (observed !== act) {
                observed = act
                act.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        hide()
                        stopWatching()
                        if (observed === act) observed = null
                    }
                })
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Muestra [target] en la pantalla externa. Devuelve false si no hay ninguna o está apagada. */
    fun show(ctx: Context, target: Int): Boolean {
        load(ctx)
        if (!enabled) return false
        val act = ctx.findComponentActivity() ?: return false
        val display = firstDisplay(act) ?: return false
        // La Biblia y el video no pueden usar el TV a la vez
        com.ucd.player.player.VideoCast.stop()
        if (!ensurePresentation(act, display)) { flat = -1; return false }
        flat = target.coerceIn(0, (Bible.totalVerses - 1).coerceAtLeast(0))
        blackout = false
        resumeFlat = -1
        return true
    }

    /** Pasa al versículo siguiente (+1) o anterior (-1) en el TV. */
    fun move(delta: Int) {
        if (flat < 0) return
        flat = (flat + delta).coerceIn(0, (Bible.totalVerses - 1).coerceAtLeast(0))
        blackout = false
    }

    /** Deja de proyectar y libera la pantalla externa. */
    fun hide() {
        resumeFlat = -1
        val p = presentation
        if (p != null) p.dismiss() else { flat = -1; blackout = false }
    }
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

private class VersePresentation(private val act: ComponentActivity, display: Display) : Presentation(act, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decor = window!!.decorView
        // Compose necesita estos "dueños" en la ventana del TV; se reutilizan los de la actividad
        decor.setViewTreeLifecycleOwner(act)
        decor.setViewTreeViewModelStoreOwner(act)
        decor.setViewTreeSavedStateRegistryOwner(act)
        val prefs = UcdPrefs(act)
        val view = ComposeView(context).apply {
            setContent { UcdTheme(themeId = prefs.themeId) { ExternalVerseScreen(prefs) } }
        }
        setContentView(view)
    }
}
