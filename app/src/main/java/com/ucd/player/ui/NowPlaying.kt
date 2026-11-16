package com.ucd.player.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ucd.player.data.Perf

/**
 * Qué video está cargado en el reproductor ahora mismo (el mismo que muestra la notificación).
 * El servicio de reproducción lo mantiene al día; las listas solo lo leen para marcarlo.
 */
object NowPlaying {
    /** Dirección (uri) original del video cargado, o null si no hay ninguno. */
    var uri by mutableStateOf<String?>(null)
        private set

    /** true = sonando; false = en pausa. */
    var playing by mutableStateOf(false)
        private set

    fun attach(p: Player) {
        sync(p)
        p.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync(player)
        })
    }

    fun clear() { uri = null; playing = false }

    private fun sync(p: Player) {
        val loaded = p.mediaItemCount > 0 && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED
        uri = if (loaded) p.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } else null
        playing = loaded && p.isPlaying
    }
}

/** true si [uri] es el video que está cargado en el reproductor. Solo se recompone el video afectado. */
@Composable
fun rememberIsNowPlaying(uri: String): Boolean {
    val current by remember(uri) { derivedStateOf { NowPlaying.uri == uri } }
    return current
}

/** Barritas tipo ecualizador: se mueven si suena y quedan quietas si está en pausa. */
@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val playing = NowPlaying.playing
    val animate = playing && !Perf.enabled
    val a: Float; val b: Float; val c: Float
    if (animate) {
        val t = rememberInfiniteTransition(label = "eq")
        a = t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "a").value
        b = t.animateFloat(0.9f, 0.3f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "b").value
        c = t.animateFloat(0.4f, 0.85f, infiniteRepeatable(tween(360, easing = LinearEasing), RepeatMode.Reverse), label = "c").value
    } else {
        a = 0.45f; b = 0.9f; c = 0.6f
    }
    Canvas(modifier.size(size)) {
        val w = this.size.width / 5f
        val h = this.size.height
        listOf(a, b, c).forEachIndexed { i, f ->
            val bh = h * f
            drawRoundRect(
                color, Offset(i * 2f * w, h - bh), Size(w, bh), CornerRadius(w / 2f),
            )
        }
    }
}

/** Capa sobre la miniatura del video que está sonando. */
@Composable
fun NowPlayingOverlay(modifier: Modifier = Modifier, barsSize: Dp = 20.dp) {
    Box(modifier.background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        EqualizerBars(Color.White, size = barsSize)
    }
}
