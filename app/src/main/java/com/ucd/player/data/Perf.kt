package com.ucd.player.data

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Modo ligero con pantalla externa. Mientras haya un TV conectado (y el ajuste esté encendido),
 * la app quita el desenfoque en vivo y las animaciones de menús del celular, para que el envío
 * de imagen al TV no se corte al abrir la cola, los ajustes, etc. Se activa y se apaga solo.
 */
object Perf {
    /** Interruptor del usuario (Ajustes). */
    var lightMode by mutableStateOf(true)

    /** Hay una pantalla externa conectada ahora mismo. */
    var externalConnected by mutableStateOf(false)
        private set

    /** Verdadero = interfaz ligera. Se puede leer desde cualquier pantalla: se actualizan solas. */
    val enabled: Boolean get() = lightMode && externalConnected

    private var started = false

    /** Empieza a vigilar si se conecta o desconecta una pantalla externa (se llama una vez al abrir la app). */
    fun start(ctx: Context) {
        if (started) return
        started = true
        val dm = ctx.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        fun refresh() {
            externalConnected = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).isNotEmpty()
        }
        refresh()
        dm.registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) { refresh() }
            override fun onDisplayRemoved(displayId: Int) { refresh() }
            override fun onDisplayChanged(displayId: Int) {}
        }, Handler(Looper.getMainLooper()))
    }
}
