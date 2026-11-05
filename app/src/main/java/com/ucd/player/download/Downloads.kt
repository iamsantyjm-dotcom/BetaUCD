package com.ucd.player.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DlStatus { Idle, Running, Done, Failed, Cancelled }

/** Estado de la descarga actual (una a la vez en esta parte). La pantalla y el servicio lo comparten. */
data class DlState(
    val status: DlStatus = DlStatus.Idle,
    val title: String = "",
    /** 0..1; negativo = no se sabe (barra indeterminada). */
    val progress: Float = -1f,
    val phase: String = "",
    /** Velocidad y tiempo restante, por ejemplo "2.1MiB/s · quedan 00:35". */
    val detail: String = "",
    /** Dónde se guardó (Done) o el motivo del error (Failed). */
    val message: String = "",
    /** URI del archivo guardado (solo en Done), para el botón Reproducir. */
    val uri: String = "",
)

object Downloads {
    private val _state = MutableStateFlow(DlState())
    val state: StateFlow<DlState> = _state.asStateFlow()

    fun set(s: DlState) { _state.value = s }
    fun isRunning(): Boolean = _state.value.status == DlStatus.Running
    fun clearFinished() { if (!isRunning()) _state.value = DlState() }
}
