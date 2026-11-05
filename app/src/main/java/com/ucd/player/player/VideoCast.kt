package com.ucd.player.player

import android.app.Presentation
import android.content.Context
import android.content.ContextWrapper
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.media3.common.Player
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ucd.player.bible.ExternalDisplay

/** Busca la actividad dentro de un Context (los composables a veces reciben un envoltorio). */
internal fun Context.findActivity(): ComponentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Envía solo la imagen del video a la pantalla externa (TV). El celular se queda con los
 * controles y los menús, así que abrir la cola o los ajustes no toca lo que se ve en el TV.
 *
 * La ventana del TV no depende de la pantalla del reproductor: al salir con "atrás" el video
 * sigue en el TV mientras sigue sonando, así la lista de carpetas no se ve en el TV. Se cierra
 * sola cuando el video se pausa o termina (y el TV vuelve a lo que tenía), al proyectar la
 * Biblia, al desconectar el TV o al cerrar el servicio.
 */
object VideoCast {
    /** Tamaño del video en el TV (el mismo botón Ajustar / Rellenar / Estirar del reproductor). */
    var scale by mutableStateOf(VideoScale.Fit)

    /** Fundido a negro de la imagen (1 = normal, 0 = negro). */
    var fade by mutableFloatStateOf(1f)

    /** TV en negro (el video sigue sonando). */
    var blackout by mutableStateOf(false)

    /** Zoom del video en el TV (1 = normal) y desplazamiento como fracción del tamaño de la pantalla. */
    var zoom by mutableFloatStateOf(1f)
        private set
    var panX by mutableFloatStateOf(0f)
        private set
    var panY by mutableFloatStateOf(0f)
        private set

    fun setZoom(z: Float, x: Float, y: Float) {
        zoom = z
        panX = x
        panY = y
    }

    /** El video se está mostrando en la pantalla externa ahora mismo. */
    var active by mutableStateOf(false)
        private set

    /** La pantalla del reproductor está abierta en el celular. */
    var screenOpen = false

    private var presentation: VideoPresentation? = null
    private var host: CastHost? = null
    private var watched: Player? = null

    private val idleListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady) closeIfIdle()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) closeIfIdle()
        }
    }

    /** Si ya no hay reproductor abierto y el video está en pausa o terminó, libera el TV. */
    fun closeIfIdle() {
        if (screenOpen || presentation == null) return
        // Con la pantalla en negro activada, el TV se queda en negro aunque pauses o salgas
        if (blackout) return
        val p = watched
        if (p == null || !p.playWhenReady ||
            p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE
        ) stop()
    }

    /** Muestra el video en la pantalla externa. Devuelve false si no hay ninguna o no se pudo. */
    fun start(act: ComponentActivity, player: Player): Boolean {
        val dm = act.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        if (display == null) { stop(); return false }
        val cur = presentation
        if (cur != null && cur.isShowing && cur.display.displayId == display.displayId && watched === player) {
            active = true
            return true
        }
        stop()
        // La Biblia y el video no pueden usar el TV a la vez
        ExternalDisplay.hide()
        // Primero con el contexto de la app (sobrevive a la pantalla del reproductor);
        // si el sistema no lo permite, con el de la actividad.
        for (c in listOf<Context>(act.applicationContext, act)) {
            val h = CastHost()
            try {
                val p = VideoPresentation(c, display, h, player)
                p.setOnDismissListener { if (presentation === p) stop() }
                p.show()
                presentation = p
                host = h
                watched = player
                player.addListener(idleListener)
                active = true
                return true
            } catch (e: Exception) {
                h.destroy()
            }
        }
        active = false
        return false
    }

    /** Quita el video del TV (el TV vuelve a lo que tenía). */
    fun stop() {
        val p = presentation
        val h = host
        presentation = null
        host = null
        active = false
        blackout = false
        fade = 1f
        setZoom(1f, 0f, 0f)
        watched?.removeListener(idleListener)
        watched = null
        if (p != null) runCatching { p.dismiss() }
        h?.destroy()
    }
}

/** "Dueño" propio del ciclo de vida de la ventana del TV (no depende de ninguna actividad). */
private class CastHost : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val controller = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry

    init {
        controller.performAttach()
        controller.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        runCatching {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }
}

private class VideoPresentation(
    ctx: Context,
    display: Display,
    private val host: CastHost,
    private val player: Player,
) : Presentation(ctx, display) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val decor = window!!.decorView
        // Compose necesita estos "dueños" en la ventana del TV
        decor.setViewTreeLifecycleOwner(host)
        decor.setViewTreeViewModelStoreOwner(host)
        decor.setViewTreeSavedStateRegistryOwner(host)
        val view = ComposeView(context).apply {
            setContent {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    VideoSurface(
                        player, VideoCast.scale,
                        Modifier.fillMaxSize().graphicsLayer {
                            scaleX = VideoCast.zoom
                            scaleY = VideoCast.zoom
                            translationX = VideoCast.panX * size.width
                            translationY = VideoCast.panY * size.height
                        },
                    )
                    // Fundido a negro: solo se vuelve a dibujar, no recompone
                    Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = 1f - VideoCast.fade) })
                    if (VideoCast.blackout) Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        setContentView(view)
    }
}
