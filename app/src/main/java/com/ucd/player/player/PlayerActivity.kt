package com.ucd.player.player

import android.content.ComponentName
import android.content.Context
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.ucd.player.R
import com.ucd.player.data.UcdPrefs
import com.ucd.player.ui.theme.UcdTheme
import kotlinx.coroutines.flow.MutableSharedFlow

/** Cola en memoria (evita límites de tamaño al pasar listas por Intent). */
object PlayQueue {
    var uris: List<String> = emptyList()
    var titles: List<String> = emptyList()
    var start: Int = 0
}

class PlayerActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controllerRef: Player? = null
    private lateinit var prefs: UcdPrefs

    /** Avisa a la pantalla cuando se pulsan los botones físicos de volumen. */
    private val volumeKeyEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /** true cuando la ventana está en modo imagen en imagen (se ocultan los controles). */
    private var inPip by mutableStateOf(false)

    /** Si nos abren un video desde otra app ("Abrir con"), lo convierte en la cola de reproducción. */
    private fun queueFromIntent(i: Intent?): Boolean {
        val data = i?.data ?: return false
        if (i.action != Intent.ACTION_VIEW) return false
        PlayQueue.uris = listOf(data.toString())
        PlayQueue.titles = listOf((displayNameOf(this, data) ?: "Video").substringBeforeLast('.'))
        PlayQueue.start = 0
        return true
    }

    private fun loadQueue(c: Player, uris: List<String>, titles: List<String>, start: Int) {
        if (uris.isEmpty()) return
        val s = start.coerceIn(0, uris.lastIndex)
        c.setPlaybackSpeed(1f)   // cada reproducción nueva empieza a velocidad normal
        c.repeatMode = if (prefs.repeatAll) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        c.shuffleModeEnabled = false
        // Cada reproducción nueva empieza sin pistas forzadas por el video anterior (p. ej. subtítulos desactivados)
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .clearOverrides()
            .build()
        c.setMediaItems(
            uris.mapIndexed { i, u ->
                // Si ya existe la copia convertida (con índice), se usa directamente
                val playUri = Remuxer.cached(this, u)?.let { Uri.fromFile(it).toString() } ?: u
                val b = MediaItem.Builder().setUri(playUri).setMediaId(u).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(titles.getOrNull(i)).build()
                )
                b.build()
            },
            s,
            // continuar donde lo dejaste (si hay posición guardada)
            if (prefs.resumePlayback) prefs.position(uris[s]) else 0L,
        )
        c.prepare()
        c.play()
    }

    private fun enterPip(p: Player) {
        val vs = p.videoSize
        val r = if (vs.width > 0 && vs.height > 0) vs.width * vs.pixelWidthHeightRatio / vs.height else 16f / 9f
        val rc = r.coerceIn(0.5f, 2.3f)
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational((rc * 1000).toInt(), 1000)).build()
            )
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (queueFromIntent(intent)) {
            controllerRef?.let { savePosition(); loadQueue(it, PlayQueue.uris, PlayQueue.titles, 0) }
        }
    }

    /** Animación tipo "pop up" al abrir y cerrar el reproductor. */
    @Suppress("DEPRECATION")
    private fun setupTransitions() {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, R.anim.pop_in, R.anim.pop_back_out)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.pop_back_in, R.anim.pop_out)
        } else {
            overridePendingTransition(R.anim.pop_in, R.anim.pop_back_out)
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT < 34) overridePendingTransition(R.anim.pop_back_in, R.anim.pop_out)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupTransitions()
        prefs = UcdPrefs(this)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (prefs.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        queueFromIntent(intent)
        val uris = PlayQueue.uris
        val titles = PlayQueue.titles
        val start = PlayQueue.start
        // Si vienes de la notificación y ya hay algo sonando, solo se vuelve a mostrar (no se recarga)
        val fromNotification = intent.getBooleanExtra(EXTRA_RESUME, false)
        val shouldLoad = savedInstanceState == null

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future

        setContent {
            UcdTheme(themeId = prefs.themeId) {
                var controller by remember { mutableStateOf<Player?>(null) }
                var error by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    future.addListener({
                        try {
                            val session = future.get()
                            // El servicio vive en este mismo proceso: usamos su reproductor directamente
                            // (sin pasar por la sesión), así duración y saltos funcionan en todos los videos.
                            val c: Player = PlaybackService.exo ?: session
                            if (shouldLoad && !(fromNotification && c.mediaItemCount > 0)) loadQueue(c, uris, titles, start)
                            controllerRef = c
                            controller = c
                        } catch (e: Exception) {
                            error = e.toString()
                        }
                    }, MoreExecutors.directExecutor())
                }
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    controller?.let { c ->
                        PlayerScreen(
                            player = c,
                            onBack = { finish() },
                            setImmersive = ::applyImmersive,
                            volumeKeyEvents = volumeKeyEvents,
                            pip = inPip,
                            onEnterPip = { enterPip(c) },
                        )
                    }
                    error?.let {
                        Text(
                            "No se pudo iniciar el reproductor:\n$it",
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )
                    }
                }
            }
        }
    }

    private fun applyImmersive(on: Boolean) {
        val ctl = WindowInsetsControllerCompat(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (on) ctl.hide(WindowInsetsCompat.Type.systemBars()) else ctl.show(WindowInsetsCompat.Type.systemBars())
    }

    /** Volumen con los botones físicos usando la interfaz de la app, no la del sistema. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val dir = if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            val up = keyCode == KeyEvent.KEYCODE_VOLUME_UP
            val atMax = am.getStreamVolume(AudioManager.STREAM_MUSIC) >= am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val boost = PlaybackService.boost
            if (up && atMax) {
                // Con el volumen del celular al tope, los botones siguen subiendo hasta 200 %
                PlaybackService.boost = (Math.round(boost * 10f) / 10f + 0.1f).coerceAtMost(2f)
            } else if (!up && boost > 1.001f) {
                // Primero se baja el volumen extra y luego el del celular
                val nb = Math.round(boost * 10f) / 10f - 0.1f
                PlaybackService.boost = if (nb < 1.05f) 1f else nb
            } else {
                runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0) } // 0 = sin panel del sistema
            }
            volumeKeyEvents.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) return true
        return super.onKeyUp(keyCode, event)
    }

    /** Guarda dónde te quedaste en el video actual. */
    override fun onStop() {
        // Si se sale con "atrás" y el ajuste de segundo plano está apagado, el video se pausa
        if (isFinishing && !prefs.backgroundPlay) runCatching { controllerRef?.pause() }
        savePosition()
        super.onStop()
    }

    private fun savePosition() {
        val c = controllerRef ?: return
        runCatching {
            val uri = c.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
            val dur = c.duration
            val pos = c.currentPosition
            if (pos < 5_000L || (dur > 0 && pos > dur - 5_000L)) prefs.clearPosition(uri)
            else prefs.savePosition(uri, pos)
        }
    }

    override fun onDestroy() {
        controllerRef = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_RESUME = "ucd.resume"

        fun intent(ctx: Context, uris: List<Uri>, titles: List<String>, start: Int): Intent {
            PlayQueue.uris = uris.map { it.toString() }
            PlayQueue.titles = titles
            PlayQueue.start = start
            return Intent(ctx, PlayerActivity::class.java)
        }
    }
}
