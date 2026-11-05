package com.ucd.player.download

/** Convierte el texto crudo de yt-dlp en un mensaje que se entienda. */
object Errors {
    fun explain(raw: String): String {
        val t = raw.lowercase()
        fun has(vararg k: String) = k.any { t.contains(it) }
        return when {
            has("private video") -> "El video es privado. Solo su dueño puede verlo."
            has("confirm your age", "age-restricted", "age restricted") ->
                "El video tiene restricción de edad y exige iniciar sesión (todavía no se puede)."
            has("not a bot", "sign in to confirm") ->
                "YouTube pide comprobar que no eres un robot. Pulsa «Actualizar yt-dlp» y prueba con otra red (wifi o datos)."
            has("members-only", "members only", "join this channel") -> "Es un video solo para miembros del canal."
            has("premieres in", "live event will begin", "will begin in") ->
                "El video todavía no empieza (estreno o directo programado)."
            has("video unavailable", "this video is not available", "has been removed", "no longer available", "copyright", "blocked it in your country", "not made this video available") ->
                "El video no está disponible (lo borraron o está bloqueado en tu país)."
            has("unable to resolve host", "failed to resolve", "name resolution", "network is unreachable", "no address associated", "timed out", "connection reset", "connection refused", "unable to connect") ->
                "Sin conexión a internet. Revisa tu wifi o tus datos e inténtalo otra vez."
            has("no space left", "enospc", "not enough space") -> "No hay espacio suficiente en el teléfono."
            has("403", "forbidden") -> "YouTube bloqueó la descarga (error 403). Pulsa «Actualizar yt-dlp» y reintenta."
            has("429", "too many requests") -> "Demasiadas solicitudes seguidas. Espera unos minutos e inténtalo de nuevo."
            has("unsupported url") -> "Este enlace no es compatible."
            has("requested format is not available") -> "Esa calidad no está disponible para este video. Elige otra."
            has("postprocessing", "ffmpeg") -> "Falló la conversión del archivo (ffmpeg)."
            has("securityexception", "permission denied", "eacces") ->
                "No hay permiso para guardar en esa carpeta. Elígela de nuevo."
            has("no se encontró el archivo descargado") -> "La descarga terminó, pero no se encontró el archivo."
            else -> {
                val last = raw.lines().lastOrNull { it.isNotBlank() } ?: "Error desconocido"
                last.removePrefix("ERROR: ").take(200)
            }
        }
    }
}
