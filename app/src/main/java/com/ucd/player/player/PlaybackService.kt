package com.ucd.player.player

import android.app.PendingIntent
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.graphics.Bitmap
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.log10
import android.media.audiofx.LoudnessEnhancer
import com.ucd.player.data.UcdPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.ForwardingPlayer
import androidx.media3.session.CommandButton
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * El ExoPlayer vive aquí (no en la Activity), así el audio sigue sonando al
 * minimizar o apagar la pantalla. Media3 crea y gestiona la notificación
 * (play/pausa/siguiente) automáticamente a partir de la MediaSession.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkForMediaId: String? = null

    // Fundido de volumen: volumen "normal" (el que pone el usuario) y último valor puesto por el fundido
    private var fadeBase = 1f
    private var fadeSet = -1f

    companion object {
        const val STOP_ACTION = "ucd.stop_playback"
        private const val CMD_STOP = "ucd.cmd.stop_video"
        /** Reproductor activo (mismo proceso que la app). */
        @Volatile
        var exo: ExoPlayer? = null

        /** Volumen por encima del 100 % (1.0 = normal, 2.0 = 200 %). Lo cambia el reproductor. */
        @Volatile
        var boost: Float = 1f
    }

    private var enhancer: LoudnessEnhancer? = null
    private var enhancerSession = -1
    private var enhancerOn = false
    private var gainMb = 0

    /**
     * Aplica el volumen extra (más de 100 %) con un amplificador de sonido del sistema.
     * La ganancia sube y baja poco a poco, y el efecto nunca se apaga una vez encendido
     * (apagarlo al volver a 100 % causaba un microcorte).
     */
    private fun applyBoost(player: ExoPlayer) {
        val b = boost.coerceIn(1f, 2f)
        val sid = player.audioSessionId
        if (sid == C.AUDIO_SESSION_ID_UNSET) return
        if (enhancer == null || enhancerSession != sid) {
            runCatching { enhancer?.release() }
            enhancer = runCatching { LoudnessEnhancer(sid) }.getOrNull()
            enhancerSession = sid
            enhancerOn = false
            gainMb = 0
        }
        val e = enhancer ?: return
        val target = (2000f * log10(b)).toInt() // milibelios: 2.0 = +6 dB
        if (gainMb == target) return
        runCatching {
            if (!enhancerOn) {
                e.setTargetGain(0)
                e.enabled = true
                enhancerOn = true
            }
            gainMb += (target - gainMb).coerceIn(-120, 120)
            e.setTargetGain(gainMb)
        }
    }

    /**
     * Cada 50 ms ajusta el volumen según la posición del video (fundido al terminar y al empezar)
     * y aplica el ajuste "reproducir el siguiente automáticamente".
     */
    private fun startFadeLoop(player: ExoPlayer, prefs: UcdPrefs) {
        player.addListener(object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                // Si otro (el control de volumen de la app) sube el volumen, ese pasa a ser el normal
                if (volume > 0.999f && abs(volume - fadeSet) > 0.001f) fadeBase = volume
            }
        })
        serviceScope.launch {
            while (true) {
                delay(50)
                val pauseAtEnd = !prefs.autoNext
                if (player.pauseAtEndOfMediaItems != pauseAtEnd) player.pauseAtEndOfMediaItems = pauseAtEnd
                val f = if (player.mediaItemCount == 0) 1f
                else Fade.factor(player.currentPosition, player.duration, prefs.fadeMs)
                val target = fadeBase * f
                if (abs(player.volume - target) > 0.003f) {
                    fadeSet = target
                    player.volume = target
                }
                applyBoost(player)
            }
        }
    }

    private fun extractArtwork(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            ByteArrayOutputStream().use { output ->
                frame.compress(Bitmap.CompressFormat.JPEG, 82, output)
                output.toByteArray()
            }.takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = UcdPrefs(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo = player

        // Al tocar la notificación se vuelve al reproductor sin recargar el video
        val openIntent = Intent(this, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_RESUME, true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopCommand = SessionCommand(CMD_STOP, Bundle.EMPTY)
        val stopButton = CommandButton.Builder(CommandButton.ICON_STOP)
            .setDisplayName("Detener")
            .setSessionCommand(stopCommand)
            .build()

        session = MediaSession.Builder(this, player)
            .setSessionActivity(openPending)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(stopCommand)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setMediaButtonPreferences(ImmutableList.of(stopButton))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == CMD_STOP) {
                        // Detiene por completo: sin video cargado, sin sonido y sin notificación
                        player.pause()
                        player.stop()
                        player.clearMediaItems()
                        stopSelf()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()
        session?.setMediaButtonPreferences(ImmutableList.of(stopButton))

        // Miniatura para la notificación (sin reiniciar la reproducción: solo cambia los metadatos)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val item = mediaItem ?: return
                val id = item.mediaId
                if (id.isEmpty() || id == artworkForMediaId) return
                if (item.mediaMetadata.artworkData != null) return
                artworkForMediaId = id
                serviceScope.launch {
                    val bytes = withContext(Dispatchers.IO) {
                        extractArtwork(Uri.parse(item.localConfiguration?.uri?.toString() ?: id))
                    } ?: return@launch
                    val idx = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == id }
                        ?: return@launch
                    val old = player.getMediaItemAt(idx)
                    val md = old.mediaMetadata.buildUpon()
                        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        .build()
                    player.replaceMediaItem(idx, old.buildUpon().setMediaMetadata(md).build())
                }
            }
        })

        startFadeLoop(player, prefs)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        // Si no está reproduciendo, detener el servicio al cerrar la app desde recientes
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        VideoCast.stop()
        runCatching { enhancer?.release() }
        enhancer = null
        session?.run {
            player.release()
            release()
        }
        session = null
        serviceScope.cancel()
        exo = null
        super.onDestroy()
    }
}
