package com.ucd.player.player

/**
 * Cálculo del fundido entre videos. Se usa tanto para el volumen (en PlaybackService)
 * como para la imagen (en PlayerScreen), así los dos van siempre a la par.
 * Devuelve 1 = normal y 0 = silencio / negro.
 */
object Fade {
    /** El fundido nunca ocupa más de un tercio del video. */
    private fun window(durationMs: Long, fadeMs: Int): Long =
        minOf(fadeMs.toLong(), durationMs / 3).coerceAtLeast(1L)

    /** Entrada: sube desde 0 durante los primeros segundos del video. */
    fun fadeIn(positionMs: Long, durationMs: Long, fadeMs: Int): Float {
        if (fadeMs <= 0 || durationMs <= 0L) return 1f
        val w = window(durationMs, fadeMs)
        return (positionMs.coerceIn(0L, durationMs).toFloat() / w).coerceIn(0f, 1f)
    }

    /** Salida: baja hasta 0 durante los últimos segundos del video. */
    fun fadeOut(positionMs: Long, durationMs: Long, fadeMs: Int): Float {
        if (fadeMs <= 0 || durationMs <= 0L) return 1f
        val w = window(durationMs, fadeMs)
        return ((durationMs - positionMs.coerceIn(0L, durationMs)).toFloat() / w).coerceIn(0f, 1f)
    }

    fun factor(positionMs: Long, durationMs: Long, fadeMs: Int): Float =
        minOf(fadeIn(positionMs, durationMs, fadeMs), fadeOut(positionMs, durationMs, fadeMs))
}
